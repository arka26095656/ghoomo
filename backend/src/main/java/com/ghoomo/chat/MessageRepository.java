package com.ghoomo.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdOrderByIdAsc(Long sessionId);

    /** Newest first; reverse before sending to the LLM. */
    List<ChatMessage> findBySessionIdOrderByIdDesc(Long sessionId, Pageable page);
}
