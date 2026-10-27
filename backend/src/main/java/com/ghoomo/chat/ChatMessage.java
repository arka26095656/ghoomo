package com.ghoomo.chat;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    public enum Role { USER, ASSISTANT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, length = 20_000)
    private String content;

    /** Set when this assistant reply produced an itinerary. */
    @Column(name = "itinerary_id")
    private Long itineraryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatMessage() {}

    public ChatMessage(Long sessionId, Role role, String content, Long itineraryId) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content.length() > 20_000 ? content.substring(0, 20_000) : content;
        this.itineraryId = itineraryId;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public Long getItineraryId() { return itineraryId; }
    public Instant getCreatedAt() { return createdAt; }
}
