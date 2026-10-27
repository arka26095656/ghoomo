package com.ghoomo.itinerary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pulls a JSON object out of an LLM reply. Models sometimes wrap JSON in ```json fences
 * or add a sentence before it even when asked not to.
 */
public final class PlanJson {
    private PlanJson() {}

    public static String extractObject(String raw) {
        if (raw == null) throw new IllegalArgumentException("Empty model reply");
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) s = s.substring(firstNewline + 1, lastFence).trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("No JSON object in model reply");
        return s.substring(start, end + 1);
    }

    public static <T> T parse(ObjectMapper mapper, String raw, Class<T> type) {
        try {
            return mapper.readValue(extractObject(raw), type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Model reply was not valid JSON: " + e.getOriginalMessage(), e);
        }
    }

    public static String write(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
