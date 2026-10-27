package com.ghoomo.ai;

import com.ghoomo.common.ApiException;
import com.ghoomo.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Tries each configured provider in order (Gemini, then Groq). On a rate limit, timeout or
 * server error it fails over to the next one; the offline mock is the last resort.
 */
@Service
public class LlmRouter {

    private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);

    public record Answer(String text, String provider) {}

    private final List<LlmProvider> providers;
    private final MockLlmProvider mock;
    private final boolean mockFallback;

    public LlmRouter(List<LlmProvider> all, MockLlmProvider mock, AppProperties props) {
        this.providers = all.stream().filter(p -> !(p instanceof MockLlmProvider)).toList();
        this.mock = mock;
        this.mockFallback = props.ai().mockFallback();
        log.info("LLM providers configured: {}{}",
                providers.stream().filter(LlmProvider::isConfigured).map(LlmProvider::name).toList(),
                mockFallback ? " (+ offline mock fallback)" : "");
    }

    public Answer complete(LlmRequest request) {
        for (LlmProvider p : providers) {
            if (!p.isConfigured()) continue;
            try {
                String text = p.complete(request);
                if (text != null && !text.isBlank()) return new Answer(text, p.name());
                log.warn("{} returned an empty reply for {}", p.name(), request.task());
            } catch (LlmException e) {
                log.warn("{} failed for {}: {}", p.name(), request.task(), e.getMessage());
            }
        }
        if (mockFallback) return new Answer(mock.complete(request), mock.name());
        throw unavailable();
    }

    /** Streams from the first provider that starts answering. Returns that provider's name. */
    public String stream(LlmRequest request, Consumer<String> onToken) {
        for (LlmProvider p : providers) {
            if (!p.isConfigured()) continue;
            AtomicBoolean started = new AtomicBoolean(false);
            try {
                p.stream(request, token -> {
                    started.set(true);
                    onToken.accept(token);
                });
                if (started.get()) return p.name();
                log.warn("{} streamed nothing for {}", p.name(), request.task());
            } catch (LlmException e) {
                if (started.get()) {
                    // Can't un-send tokens; end the reply gracefully instead of switching voices mid-sentence.
                    log.warn("{} broke mid-stream: {}", p.name(), e.getMessage());
                    onToken.accept("\n\n_(The reply was cut short. Ask again to continue.)_");
                    return p.name();
                }
                log.warn("{} failed before streaming for {}: {}", p.name(), request.task(), e.getMessage());
            }
        }
        if (mockFallback) {
            mock.stream(request, onToken);
            return mock.name();
        }
        throw unavailable();
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "The AI service is busy or not configured. Try again in a minute.");
    }
}
