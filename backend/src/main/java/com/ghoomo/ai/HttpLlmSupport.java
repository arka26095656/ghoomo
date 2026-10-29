package com.ghoomo.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Small helpers shared by the HTTP-based providers. */
final class HttpLlmSupport {
    private HttpLlmSupport() {}

    static HttpRequest post(String url, Map<String, String> headers, String body, Duration timeout) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(b::header);
        return b.build();
    }

    static String send(HttpClient client, HttpRequest req, String provider) {
        try {
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new LlmException(provider + " returned HTTP " + res.statusCode() + ": " + abbreviate(res.body()), res.statusCode());
            }
            return res.body();
        } catch (HttpTimeoutException e) {
            throw new LlmException(provider + " timed out after " + seconds(req) + "s", e);
        } catch (IOException e) {
            throw new LlmException(provider + " request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(provider + " request interrupted", e);
        }
    }

    /** Reads a server-sent-events body and hands each "data:" payload to the consumer. */
    static void sendSse(HttpClient client, HttpRequest req, String provider, Consumer<String> onData) {
        try {
            HttpResponse<Stream<String>> res = client.send(req, HttpResponse.BodyHandlers.ofLines());
            if (res.statusCode() / 100 != 2) {
                String body;
                try (Stream<String> lines = res.body()) {
                    body = String.join("\n", lines.limit(20).toList());
                }
                throw new LlmException(provider + " returned HTTP " + res.statusCode() + ": " + abbreviate(body), res.statusCode());
            }
            try (Stream<String> lines = res.body()) {
                lines.filter(l -> l.startsWith("data:"))
                        .map(l -> l.substring(5).trim())
                        .filter(d -> !d.isEmpty() && !"[DONE]".equals(d))
                        .forEach(onData);
            }
        } catch (HttpTimeoutException e) {
            throw new LlmException(provider + " timed out after " + seconds(req) + "s", e);
        } catch (IOException e) {
            throw new LlmException(provider + " stream failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(provider + " stream interrupted", e);
        }
    }

    private static long seconds(HttpRequest req) {
        return req.timeout().map(Duration::toSeconds).orElse(0L);
    }

    static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
