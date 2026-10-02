package example.fintech;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiErrorClient {
    public static final String CAPABILITY = "infrai.errors.capture";
    private static final String CAPTURE_PATH = "/v1/errors/capture";
    private final JobAlertConfig config;
    private final HttpClient http;

    public InfraiErrorClient(JobAlertConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestDeadline()).build());
    }

    InfraiErrorClient(JobAlertConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public CaptureReceipt capture(PaymentJobFailure failure, FailureDecision decision)
            throws IOException, InterruptedException {
        String body = payload(failure, decision);
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUri().resolve(CAPTURE_PATH))
                    .timeout(config.requestDeadline())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", failure.paymentEventId())
                    .method("POST", HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Envelope envelope = Envelope.parse(response.body());

            if (!envelope.ok()) {
                if (response.statusCode() == 429 && attempt < config.maxAttempts()) {
                    sleep(backoff(response, attempt));
                    continue;
                }
                throw new InfraiException(envelope.errorCode(), envelope.errorMessage(), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                if (attempt < config.maxAttempts()) {
                    sleep(Duration.ofMillis(250L << (attempt - 1)));
                    continue;
                }
                throw new IOException("Infrai request ended with HTTP " + response.statusCode());
            }
            return new CaptureReceipt(failure.paymentEventId(), decision.action(), response.statusCode());
        }
        throw new IllegalStateException("Attempt loop ended unexpectedly");
    }

    private static Duration backoff(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(InfraiErrorClient::retryAfter)
                .orElse(Duration.ofMillis(250L << (attempt - 1)));
    }

    private static Duration retryAfter(String value) {
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim())));
        } catch (NumberFormatException ignored) {
            return Duration.ofSeconds(1);
        }
    }

    private static void sleep(Duration delay) throws InterruptedException {
        Thread.sleep(delay.toMillis());
    }

    private static String payload(PaymentJobFailure f, FailureDecision d) {
        String title = f.jobName() + " failed for payment " + f.paymentEventId();
        String exception = f.failureType() + ": " + f.failureMessage();
        return "{" +
                "\"title\":\"" + json(title) + "\"," +
                "\"message\":\"" + json(d.auditReason()) + "\"," +
                "\"level\":\"" + d.severity().name().toLowerCase() + "\"," +
                "\"fingerprint\":[\"" + json(f.jobName()) + "\",\"" + json(f.failureType()) + "\"]," +
                "\"exception\":\"" + json(exception) + "\"," +
                "\"context\":{" +
                "\"payment_event_id\":\"" + json(f.paymentEventId()) + "\"," +
                "\"job_name\":\"" + json(f.jobName()) + "\"," +
                "\"amount_minor\":" + f.amountMinor() + "," +
                "\"currency\":\"" + json(f.currency()) + "\"," +
                "\"risk_band\":\"" + f.riskBand() + "\"," +
                "\"failed_at\":\"" + f.failedAt() + "\"," +
                "\"action\":\"" + d.action() + "\"}}";
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    public record CaptureReceipt(String paymentEventId, FailureDecision.Action action, int statusCode) {}

    public static final class InfraiException extends IOException {
        private final String code;
        private final int statusCode;

        InfraiException(String code, String message, int statusCode) {
            super(message);
            this.code = code;
            this.statusCode = statusCode;
        }

        public String code() { return code; }
        public int statusCode() { return statusCode; }
    }

    private record Envelope(boolean ok, String errorCode, String errorMessage) {
        private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");

        static Envelope parse(String json) throws IOException {
            Matcher ok = OK.matcher(json);
            if (!ok.find()) throw new IOException("Response is not an Infrai envelope");
            boolean accepted = Boolean.parseBoolean(ok.group(1));
            if (accepted) return new Envelope(true, "", "");
            String errorObject = objectFor(json, "error");
            return new Envelope(false, stringFor(errorObject, "code"), stringFor(errorObject, "message"));
        }

        private static String objectFor(String json, String key) throws IOException {
            int keyAt = json.indexOf("\"" + key + "\"");
            int start = keyAt < 0 ? -1 : json.indexOf('{', keyAt);
            if (start < 0) throw new IOException("Envelope has no error object");
            boolean quoted = false;
            boolean escaped = false;
            int depth = 0;
            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (escaped) escaped = false;
                else if (c == '\\' && quoted) escaped = true;
                else if (c == '\"') quoted = !quoted;
                else if (!quoted && c == '{') depth++;
                else if (!quoted && c == '}' && --depth == 0) return json.substring(start, i + 1);
            }
            throw new IOException("Envelope error object is incomplete");
        }

        private static String stringFor(String json, String key) {
            Pattern field = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
            Matcher match = field.matcher(json);
            return match.find() ? match.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : "Unspecified error";
        }
    }
}
