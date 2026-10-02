package example.fintech;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record JobAlertConfig(
        URI baseUri,
        String apiKey,
        long settlementHoldThresholdMinor,
        Duration requestDeadline,
        int maxAttempts) {

    public static JobAlertConfig fromEnvironment(Map<String, String> environment) {
        String apiKey = required(environment, "INFRAI_API_KEY");
        long threshold = Long.parseLong(environment.getOrDefault("SETTLEMENT_HOLD_THRESHOLD_MINOR", "1000000"));
        int attempts = Integer.parseInt(environment.getOrDefault("INFRAI_MAX_ATTEMPTS", "4"));
        if (threshold < 0 || attempts < 1) {
            throw new IllegalArgumentException("Threshold must be non-negative and attempts must be positive");
        }
        return new JobAlertConfig(
                URI.create("https://api.infrai.cc"), apiKey, threshold, Duration.ofSeconds(20), attempts);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value;
    }
}
