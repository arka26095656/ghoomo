package com.ghoomo.ai;

import com.ghoomo.common.ApiException;
import com.ghoomo.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Tries each configured provider in the order set by app.ai.order (default: gemini, then groq).
 * A busy provider (5xx) gets one retry after a short pause; any other failure, including a timeout,
 * moves straight to the next provider. The offline mock is the last resort.
 * Every call is logged with the provider that answered and how long it took.
 */
@Service
public class LlmRouter {

    private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);
    private static final long RETRY_DELAY_MS = 1500;

    public record Answer(String text, String provider) {}

    private final List<LlmProvider> providers;
    private final MockLlmProvider mock;
    private final boolean mockFallback;

    public LlmRouter(List<LlmProvider> all, MockLlmProvider mock, AppProperties props) {
        List<String> order = props.ai().order() == null ? List.of()
                : props.ai().order().stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).toList();
        this.providers = all.stream()
                .filter(p -> !(p instanceof MockLlmProvider))
                .sorted(Comparator.comparingInt(p -> rank(order, p.name())))
                .toList();
        this.mock = mock;
        this.mockFallback = props.ai().mockFallback();
        log.info("LLM providers configured, in order: {}{}",
                providers.stream().filter(LlmProvider::isConfigured).map(LlmProvider::name).toList(),
                mockFallback ? " (+ offline mock fallback)" : "");
    }

    private static int rank(List<String> order, String name) {
        int i = order.indexOf(name);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    public Answer complete(LlmRequest request) {
        for (LlmProvider p : providers) {
            if (!p.isConfigured()) continue;
            for (int attempt = 1; attempt <= 2; attempt++) {
                long start = System.nanoTime();
                try {
                    String text = p.complete(request);
                    if (text != null && !text.isBlank()) {
                        log.info("{} answered {} in {} ms ({} chars{})", p.name(), request.task(), ms(start),
                                text.length(), attempt > 1 ? ", after retry" : "");
                        return new Answer(text, p.name());
                    }
                    log.warn("{} returned an empty reply for {} after {} ms", p.name(), request.task(), ms(start));
                    break;
                } catch (LlmException e) {
                    log.warn("{} failed for {} after {} ms (attempt {}): {}", p.name(), request.task(), ms(start),
                            attempt, e.getMessage());
                    if (attempt == 1 && isBusy(e) && pause()) continue;
                    break;
                }
            }
        }
        if (mockFallback) {
            log.warn("No AI provider answered {}; using the offline demo planner", request.task());
            return new Answer(mock.complete(request), mock.name());
        }
        throw unavailable();
    }

    /** Streams from the first provider that starts answering. Returns that provider's name. */
    public String stream(LlmRequest request, Consumer<String> onToken) {
        for (LlmProvider p : providers) {
            if (!p.isConfigured()) continue;
            for (int attempt = 1; attempt <= 2; attempt++) {
                long start = System.nanoTime();
                AtomicBoolean started = new AtomicBoolean(false);
                AtomicInteger chars = new AtomicInteger();
                try {
                    p.stream(request, token -> {
                        started.set(true);
                        chars.addAndGet(token.length());
                        onToken.accept(token);
                    });
                    if (started.get()) {
                        log.info("{} streamed {} in {} ms ({} chars{})", p.name(), request.task(), ms(start),
                                chars.get(), attempt > 1 ? ", after retry" : "");
                        return p.name();
                    }
                    log.warn("{} streamed nothing for {} after {} ms", p.name(), request.task(), ms(start));
                    break;
                } catch (LlmException e) {
                    if (started.get()) {
                        // Can't un-send tokens; end the reply gracefully instead of switching voices mid-sentence.
                        log.warn("{} broke mid-stream for {} after {} ms: {}", p.name(), request.task(), ms(start),
                                e.getMessage());
                        onToken.accept("\n\n_(The reply was cut short. Ask again to continue.)_");
                        return p.name();
                    }
                    log.warn("{} failed before streaming {} after {} ms (attempt {}): {}", p.name(), request.task(),
                            ms(start), attempt, e.getMessage());
                    if (attempt == 1 && isBusy(e) && pause()) continue;
                    break;
                }
            }
        }
        if (mockFallback) {
            log.warn("No AI provider answered {}; using the offline demo planner", request.task());
            mock.stream(request, onToken);
            return mock.name();
        }
        throw unavailable();
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * Overloaded or gateway error: worth one more try after a pause. Timeouts are not retried (that would
     * double an already long wait), and neither are quota (429) or bad-request (4xx) errors.
     */
    private static boolean isBusy(LlmException e) {
        int s = e.httpStatus();
        return s == 500 || s == 502 || s == 503 || s == 504;
    }

    private static boolean pause() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "The AI service is busy or not configured. Try again in a minute.");
    }
}
