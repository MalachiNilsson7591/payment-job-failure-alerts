package example.fintech;

public record FailureDecision(Severity severity, Action action, String auditReason) {
    public enum Severity { WARNING, ERROR }
    public enum Action { RETRY_PAYMENT_JOB, MANUAL_REVIEW, HOLD_SETTLEMENT }
}
