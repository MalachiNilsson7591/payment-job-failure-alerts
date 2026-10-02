package example.fintech;

import java.time.Instant;

public final class PaymentFailurePolicyTest {
    public static void main(String[] args) {
        PaymentFailurePolicy policy = new PaymentFailurePolicy(1_000_000);

        PaymentJobFailure criticalLowValue = failure(25_00, PaymentJobFailure.RiskBand.CRITICAL);
        assertEquals(FailureDecision.Action.HOLD_SETTLEMENT, policy.decide(criticalLowValue).action());

        PaymentJobFailure standardHighValue = failure(1_000_000, PaymentJobFailure.RiskBand.STANDARD);
        assertEquals(FailureDecision.Action.HOLD_SETTLEMENT, policy.decide(standardHighValue).action());

        PaymentJobFailure elevated = failure(5_000, PaymentJobFailure.RiskBand.ELEVATED);
        assertEquals(FailureDecision.Action.MANUAL_REVIEW, policy.decide(elevated).action());

        PaymentJobFailure standard = failure(5_000, PaymentJobFailure.RiskBand.STANDARD);
        assertEquals(FailureDecision.Action.RETRY_PAYMENT_JOB, policy.decide(standard).action());

        System.out.println("PaymentFailurePolicyTest passed");
    }

    private static PaymentJobFailure failure(long amountMinor, PaymentJobFailure.RiskBand riskBand) {
        return new PaymentJobFailure("pay_evt_test", "settlement-reconciliation", amountMinor, "USD",
                riskBand, Instant.parse("2026-10-01T03:00:00Z"), "LedgerMismatch", "totals differ");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but got " + actual);
        }
    }
}
