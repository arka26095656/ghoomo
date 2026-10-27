package com.ghoomo.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghoomo.config.AppProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Groq through its OpenAI-compatible Chat Completions API.
 * Point base-url at any other OpenAI-compatible endpoint (OpenRouter, a local Ollama) and it keeps working.
 */
@Component
@Order(2)
public class GroqProvider implements LlmProvider {

    private final AppProperties.Provider cfg;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public GroqProvider(AppProperties props, HttpClient http, ObjectMapper mapper) {
        this.cfg = props.ai().groq();
        this.http = http;
        this.mapper = mapper;
    }

    @Override public String name() { return "groq"; }

    @Override public boolean isConfigured() { return cfg != null && cfg.configured(); }

    @Override
    public String complete(LlmRequest request) {
        String body = HttpLlmSupport.send(http,
                HttpLlmSupport.post(cfg.baseUrl() + "/chat/completions", headers(), buildBody(request, false)), name());
        try {
            return mapper.readTree(body).path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            throw new LlmException("groq: unreadable response", e);
        }
    }

    @Override
    public void stream(LlmRequest request, Consumer<String> onToken) {
        HttpLlmSupport.sendSse(http,
                HttpLlmSupport.post(cfg.baseUrl() + "/chat/completions", headers(), buildBody(request, true)), name(), data -> {
                    try {
                        String text = mapper.readTree(data).path("choices").path(0).path("delta").path("content").asText("");
                        if (!text.isEmpty()) onToken.accept(text);
                    } catch (Exception ignored) {
                        // skip malformed chunk
                    }
                });
    }

    private Map<String, String> headers() {
        return Map.of("Authorization", "Bearer " + cfg.apiKey());
    }

    private String buildBody(LlmRequest request, boolean stream) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", cfg.model());
        root.put("temperature", request.temperature());
        root.put("stream", stream);
        if (request.json()) root.putObject("response_format").put("type", "json_object");

        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.system());
        for (LlmRequest.Message m : request.messages()) {
            messages.addObject()
                    .put("role", m.role() == LlmRequest.Role.USER ? "user" : "assistant")
                    .put("content", m.content());
        }
        return root.toString();
    }
}
