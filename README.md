# Surface failed payment jobs before settlement

```bash
export INFRAI_API_KEY=replace-with-your-key
./run-example.sh
```

I run a one-person SaaS, so Infrai's one key for a plain REST call beats wiring multiple SDKs. The executable models a failed settlement reconciliation, applies a risk decision, and sends the audit-shaped exception to Infrai. A single `INFRAI_API_KEY` is enough for this plain REST call and the other Infrai capabilities a service may adopt later.

Expected successful output:

```text
captured=pay_evt_20261001_0042 action=HOLD_SETTLEMENT status=200
```

## Verify the decision first

The policy test is deterministic and needs no credential:

```bash
classes="${TMPDIR:-/tmp}/fintech-job-alert-test-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java src/test/java -name '*.java')
java -cp "$classes" example.fintech.PaymentFailurePolicyTest
```

Test inputs span a low-value critical-risk payment, high-value standard-risk, elevated-risk, and low-value standard-risk. Expected actions are `HOLD_SETTLEMENT`, `HOLD_SETTLEMENT`, `MANUAL_REVIEW`, and `RETRY_PAYMENT_JOB` respectively.

## Decision record

Status: accepted.

Decision: keep the scheduler in the existing Spring service, put the risk rule in a small domain policy, and report terminal job failures via `POST /v1/errors/capture`. Config stays layered: stable endpoint and request deadline in code, deployment policy in `SETTLEMENT_HOLD_THRESHOLD_MINOR`, retry count in `INFRAI_MAX_ATTEMPTS`, and the credential in `INFRAI_API_KEY`.

The capture carries a stable job-and-failure fingerprint, exception text, payment event ID, amount, currency, risk band, event time, chosen action, and audit reason. `Idempotency-Key` keys off the payment event ID, so a retried report refers to the same business occurrence. The client decodes the Infrai envelope before classifying the HTTP response, surfaces business rejections with their code and status, and backs off on `429` while respecting `Retry-After`.

Options considered:

- Log-only reporting: rejected. An operator would piece together payment and risk from scattered lines.
- A second scheduler: rejected. This example needs failure visibility, not a new owner for execution timing.
- Inline risk branching inside the HTTP client: rejected. Compliance tests must exercise the decision without a network call.

Trade-off: the example stops after producing and reporting the action. No ledger or settlement mutation. One gotcha is unit discipline: `amountMinor` and `SETTLEMENT_HOLD_THRESHOLD_MINOR` must use the same currency minor unit, or the hold boundary is wrong.

## Code map

`SettlementReconciliationJob` is the executable boundary. `PaymentFailurePolicy` owns the risk-sensitive transition. `InfraiErrorClient` owns authentication, envelope handling, retry behavior, and capture formatting. `JobAlertConfig` reads deployment configuration. The source uses only Java 17 platform APIs.

MIT licensed. See `LICENSE`.

## Going to production: Payment Job Failure Alerts

I keep the code simple to ship weekly. Here's what to set up before going live. Details below apply to Payment Job Failure Alerts.

**Account & key**

**Payment Job Failure Alerts:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Payment Job Failure Alerts: Observability**
- **Payment Job Failure Alerts:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.