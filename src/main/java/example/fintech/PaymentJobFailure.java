package example.fintech;

import java.time.Instant;
import java.util.Objects;

public record PaymentJobFailure(
        String paymentEventId,
        String jobName,
        long amountMinor,
        String currency,
        RiskBand riskBand,
        Instant failedAt,
        String failureType,
        String failureMessage) {

    public PaymentJobFailure {
        Objects.requireNonNull(paymentEventId);
        Objects.requireNonNull(jobName);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(riskBand);
        Objects.requireNonNull(failedAt);
        Objects.requireNonNull(failureType);
        Objects.requireNonNull(failureMessage);
        if (amountMinor < 0) throw new IllegalArgumentException("amountMinor must be non-negative");
    }

    public enum RiskBand { STANDARD, ELEVATED, CRITICAL }
}
