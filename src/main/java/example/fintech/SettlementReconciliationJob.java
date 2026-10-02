package example.fintech;

import java.time.Instant;

public final class SettlementReconciliationJob {
    private SettlementReconciliationJob() {}

    public static void main(String[] args) throws Exception {
        JobAlertConfig config = JobAlertConfig.fromEnvironment(System.getenv());
        PaymentJobFailure failure = new PaymentJobFailure(
                "pay_evt_20261001_0042",
                "settlement-reconciliation",
                2_500_000,
                "USD",
                PaymentJobFailure.RiskBand.CRITICAL,
                Instant.parse("2026-10-01T03:00:00Z"),
                "LedgerMismatch",
                "clearing total differs from the internal ledger");

        FailureDecision decision = new PaymentFailurePolicy(config.settlementHoldThresholdMinor()).decide(failure);
        InfraiErrorClient.CaptureReceipt receipt = new InfraiErrorClient(config).capture(failure, decision);
        System.out.printf("captured=%s action=%s status=%d%n",
                receipt.paymentEventId(), receipt.action(), receipt.statusCode());
    }
}
