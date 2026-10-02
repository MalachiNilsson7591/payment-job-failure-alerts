package example.fintech;

import static example.fintech.FailureDecision.Action.HOLD_SETTLEMENT;
import static example.fintech.FailureDecision.Action.MANUAL_REVIEW;
import static example.fintech.FailureDecision.Action.RETRY_PAYMENT_JOB;
import static example.fintech.FailureDecision.Severity.ERROR;
import static example.fintech.FailureDecision.Severity.WARNING;

public final class PaymentFailurePolicy {
    private final long settlementHoldThresholdMinor;

    public PaymentFailurePolicy(long settlementHoldThresholdMinor) {
        this.settlementHoldThresholdMinor = settlementHoldThresholdMinor;
    }

    public FailureDecision decide(PaymentJobFailure failure) {
        if (failure.riskBand() == PaymentJobFailure.RiskBand.CRITICAL
                || failure.amountMinor() >= settlementHoldThresholdMinor) {
            return new FailureDecision(ERROR, HOLD_SETTLEMENT,
                    "Critical risk or amount threshold requires settlement hold");
        }
        if (failure.riskBand() == PaymentJobFailure.RiskBand.ELEVATED) {
            return new FailureDecision(ERROR, MANUAL_REVIEW,
                    "Elevated risk requires an operator decision");
        }
        return new FailureDecision(WARNING, RETRY_PAYMENT_JOB,
                "Standard-risk failure remains eligible for job retry");
    }
}
