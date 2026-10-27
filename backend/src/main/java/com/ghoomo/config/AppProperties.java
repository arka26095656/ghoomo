package com.ghoomo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Typed view of the {@code app.*} block in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Jwt jwt, RateLimit rateLimit, Ai ai, Travel travel) {

    public record Cors(List<String> allowedOrigins) {}

    public record Jwt(String secret, long ttlHours) {}

    public record RateLimit(int chatPerMinute) {}

    public record Ai(boolean mockFallback, Provider gemini, Provider groq) {}

    public record Provider(String apiKey, String model, String baseUrl) {
        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Travel(String userAgent, String nominatimUrl, String overpassUrl,
                         String openMeteoUrl, String frankfurterUrl) {}
}
