package com.ghoomo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/** Typed view of the {@code app.*} block in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Jwt jwt, RateLimit rateLimit, Ai ai, Travel travel) {

    public record Cors(List<String> allowedOrigins) {}

    public record Jwt(String secret, long ttlHours) {}

    public record RateLimit(int chatPerMinute) {}

    /** order: provider names to try first, e.g. [gemini, groq]. Unlisted providers go last. */
    public record Ai(boolean mockFallback, List<String> order, Provider gemini, Provider groq) {}

    public record Provider(String apiKey, String model, String baseUrl, Integer timeoutSeconds) {
        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }

        /** How long to wait for a reply before giving up and trying the next provider. */
        public Duration timeout() {
            return Duration.ofSeconds(timeoutSeconds == null || timeoutSeconds < 1 ? 60 : timeoutSeconds);
        }
    }

    public record Travel(String userAgent, String nominatimUrl, String overpassUrl,
                         String openMeteoUrl, String frankfurterUrl) {}
}
