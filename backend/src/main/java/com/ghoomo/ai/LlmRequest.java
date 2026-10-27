package com.ghoomo.ai;

import java.util.List;
import java.util.Map;

/**
 * Provider-neutral request.
 *
 * @param task     what the call is for; real providers ignore it, the offline mock uses it to pick a canned behaviour
 * @param system   system instruction
 * @param messages conversation so far, oldest first
 * @param json     ask the provider for a JSON-only reply (Gemini responseMimeType / OpenAI response_format)
 * @param hints    structured data the mock provider can use instead of parsing prompts; never sent to real APIs
 */
public record LlmRequest(Task task, String system, List<Message> messages, boolean json, double temperature,
                         Map<String, Object> hints) {

    public enum Task { INTENT, PLAN, SUMMARY, CHAT }

    public record Message(Role role, String content) {
        public static Message user(String c) { return new Message(Role.USER, c); }
        public static Message assistant(String c) { return new Message(Role.ASSISTANT, c); }
    }

    public enum Role { USER, ASSISTANT }

    public String lastUserMessage() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).role() == Role.USER) return messages.get(i).content();
        }
        return "";
    }
}
