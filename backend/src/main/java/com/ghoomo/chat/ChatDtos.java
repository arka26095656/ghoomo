package com.ghoomo.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ChatDtos {
    private ChatDtos() {}

    public record SessionView(Long id, String title, Instant createdAt, Instant updatedAt) {
        static SessionView of(ChatSession s) {
            return new SessionView(s.getId(), s.getTitle(), s.getCreatedAt(), s.getUpdatedAt());
        }
    }

    public record MessageView(Long id, String role, String content, Long itineraryId, Instant createdAt) {
        static MessageView of(ChatMessage m) {
            return new MessageView(m.getId(), m.getRole().name().toLowerCase(), m.getContent(), m.getItineraryId(), m.getCreatedAt());
        }
    }

    public record SendMessageRequest(@NotBlank @Size(max = 2000) String content) {}
}
