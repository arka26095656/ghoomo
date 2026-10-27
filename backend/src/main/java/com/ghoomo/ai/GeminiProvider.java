package com.ghoomo.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghoomo.config.AppProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.function.Consumer;

/** Google Gemini via the Generative Language REST API (free tier key from Google AI Studio). */
@Component
@Order(1)
public class GeminiProvider implements LlmProvider {

    private final AppProperties.Provider cfg;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public GeminiProvider(AppProperties props, HttpClient http, ObjectMapper mapper) {
        this.cfg = props.ai().gemini();
        this.http = http;
        this.mapper = mapper;
    }

    @Override public String name() { return "gemini"; }

    @Override public boolean isConfigured() { return cfg != null && cfg.configured(); }

    @Override
    public String complete(LlmRequest request) {
        String url = cfg.baseUrl() + "/models/" + cfg.model() + ":generateContent";
        String body = HttpLlmSupport.send(http, HttpLlmSupport.post(url, headers(), buildBody(request)), name());
        try {
            return extractText(mapper.readTree(body));
        } catch (Exception e) {
            throw new LlmException("gemini: unreadable response", e);
        }
    }

    @Override
    public void stream(LlmRequest request, Consumer<String> onToken) {
        String url = cfg.baseUrl() + "/models/" + cfg.model() + ":streamGenerateContent?alt=sse";
        HttpLlmSupport.sendSse(http, HttpLlmSupport.post(url, headers(), buildBody(request)), name(), data -> {
            try {
                String text = extractText(mapper.readTree(data));
                if (!text.isEmpty()) onToken.accept(text);
            } catch (LlmException e) {
                throw e;
            } catch (Exception ignored) {
                // skip keep-alive or malformed chunks
            }
        });
    }

    private Map<String, String> headers() {
        return Map.of("x-goog-api-key", cfg.apiKey());
    }

    private String buildBody(LlmRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.putObject("systemInstruction").putArray("parts").addObject().put("text", request.system());

        ArrayNode contents = root.putArray("contents");
        for (LlmRequest.Message m : request.messages()) {
            ObjectNode c = contents.addObject();
            c.put("role", m.role() == LlmRequest.Role.USER ? "user" : "model");
            c.putArray("parts").addObject().put("text", m.content());
        }

        ObjectNode gen = root.putObject("generationConfig");
        gen.put("temperature", request.temperature());
        if (request.json()) gen.put("responseMimeType", "application/json");
        return root.toString();
    }

    private static String extractText(JsonNode root) {
        JsonNode candidate = root.path("candidates").path(0);
        String finish = candidate.path("finishReason").asText("");
        if ("SAFETY".equals(finish) || "PROHIBITED_CONTENT".equals(finish)) {
            throw new LlmException("gemini blocked the reply (" + finish + ")", 400);
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (part.path("thought").asBoolean(false)) continue; // skip thinking summaries if present
            sb.append(part.path("text").asText(""));
        }
        return sb.toString();
    }
}
