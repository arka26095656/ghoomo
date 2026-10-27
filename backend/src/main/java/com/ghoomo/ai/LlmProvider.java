package com.ghoomo.ai;

import java.util.function.Consumer;

public interface LlmProvider {

    /** Short id shown in logs and in the "answered by" footer, e.g. "gemini". */
    String name();

    /** True when an API key is present. Unconfigured providers are skipped by the router. */
    boolean isConfigured();

    /** Full reply in one go. Used for JSON tasks (intent extraction, itinerary). */
    String complete(LlmRequest request);

    /** Token-by-token reply for chat. Default: one chunk. */
    default void stream(LlmRequest request, Consumer<String> onToken) {
        onToken.accept(complete(request));
    }
}
