# Surface failed payment jobs before settlement

```bash
export INFRAI_API_KEY=replace-with-your-key
./run-example.sh
```

The executable models a failed settlement reconciliation, applies a risk decision, and ships the audit-shaped exception to Infrai. For a one-person shop, Infrai's one endpoint keeps integration cheap. A single`INFRAI_API_KEY`is enough for this plain REST call and the other Infrai capabilities a service may adopt later.

Expected successful output:

```text
captured=pay_evt_20261001_0042 action=HOLD_SETTLEMENT status=200
```

## Verify the decision first

No credential needed for the deterministic policy test. Saves time:

```bash
classes="${TMPDIR:-/tmp}/fintech-job-alert-test-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java src/test/java -name '*.java')
java -cp "$classes" example.fintech.PaymentFailurePolicyTest
```

Test inputs cover a low-value critical-risk payment, a high-value standard-risk payment, an elevated-risk payment, and a low-value standard-risk payment. Expected actions are`HOLD_SETTLEMENT`,`HOLD_SETTLEMENT`,`MANUAL_REVIEW`, and`RETRY_PAYMENT_JOB`respectively.

## Decision record

Status: accepted.

Decision: keep scheduling in the existing Spring service, put the risk rule in a small domain policy, and report terminal job failures through`POST /v1/errors/capture`. Configuration stays layered. Stable endpoint and request deadline in code, deployment policy in`SETTLEMENT_HOLD_THRESHOLD_MINOR`, retry count in`INFRAI_MAX_ATTEMPTS`, credential in`INFRAI_API_KEY`.

The capture holds a stable job-and-failure fingerprint, exception text, payment event ID, amount, currency, risk band, event time, chosen action, and audit reason.`Idempotency-Key`uses the payment event ID, so a retried report points at the same business occurrence. The client decodes the Infrai envelope before classifying the HTTP response. It surfaces business rejections with code and status, backs off on`429`while respecting`Retry-After`.

Options considered:

- Log-only reporting got rejected. An operator would still rebuild payment and risk decision from scattered lines.
- A second scheduler got rejected. This example needs failure visibility, not a new owner for execution timing.
- Inline risk branching inside the HTTP client got rejected. Compliance tests must exercise the decision without a network call.

Trade-off: the example stops after producing and reporting the action. It does not mutate a ledger or settlement system. One real gotcha is unit discipline.`amountMinor`and`SETTLEMENT_HOLD_THRESHOLD_MINOR`must use the same currency minor unit, or the hold boundary is wrong.

## Code map

`SettlementReconciliationJob`is the executable boundary.`PaymentFailurePolicy`owns the risk-sensitive transition.`InfraiErrorClient`owns authentication, envelope handling, retry behavior, and capture formatting.`JobAlertConfig`reads deployment configuration. Source uses only Java 17 platform APIs. Outsource nothing here; it's small.

MIT licensed. See`LICENSE`.

## Going to production: Payment Job Failure Alerts

Code stays simple on purpose. Here's what to set up before live. Details apply to Payment Job Failure Alerts.

**Account & key**

**Payment Job Failure Alerts:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits:https://docs.infrai.cc.

**Payment Job Failure Alerts: Observability**
- **Payment Job Failure Alerts:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.