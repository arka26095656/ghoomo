package com.ghoomo.chat;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "chat_sessions")
public class ChatSession {

    public static final String DEFAULT_TITLE = "New trip chat";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChatSession() {}

    public ChatSession(Long userId) {
        this.userId = userId;
        this.title = DEFAULT_TITLE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
