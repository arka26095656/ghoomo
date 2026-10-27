package com.ghoomo.chat;

import com.ghoomo.chat.ChatDtos.*;
import com.ghoomo.common.ApiException;
import com.ghoomo.common.RateLimiter;
import com.ghoomo.itinerary.ItineraryDtos.Detail;
import com.ghoomo.security.CurrentUser;
import com.ghoomo.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/api/chat/sessions")
@Tag(name = "Chat", description = "Conversations with the AI planner. Replies stream as Server-Sent Events.")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final long SSE_TIMEOUT_MS = 180_000;

    private final ChatService chat;
    private final CurrentUser currentUser;
    private final RateLimiter rateLimiter;
    private final ExecutorService executor;

    public ChatController(ChatService chat, CurrentUser currentUser, RateLimiter rateLimiter, ExecutorService chatExecutor) {
        this.chat = chat;
        this.currentUser = currentUser;
        this.rateLimiter = rateLimiter;
        this.executor = chatExecutor;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a new conversation")
    public SessionView create() {
        return chat.createSession(currentUser.id());
    }

    @GetMapping
    @Operation(summary = "List my conversations, most recent first")
    public List<SessionView> list() {
        return chat.listSessions(currentUser.id());
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Full message history of a conversation")
    public List<MessageView> messages(@PathVariable Long id) {
        return chat.history(currentUser.id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a conversation (saved itineraries are kept)")
    public void delete(@PathVariable Long id) {
        chat.deleteSession(currentUser.id(), id);
    }

    @PostMapping(value = "/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Send a message; the reply streams back as SSE",
            description = "Events: `status` {text}, `itinerary` {itinerary detail}, `token` {text}, "
                    + "`done` {messageId, provider}, `error` {message}.")
    public SseEmitter send(@PathVariable Long id, @Valid @RequestBody SendMessageRequest req) {
        // Everything that needs the security context happens here, on the request thread.
        User user = currentUser.require();
        ChatSession session = chat.requireSession(user.getId(), id);
        rateLimiter.check(user.getId());

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        SseSink sink = new SseSink(emitter);
        executor.submit(() -> {
            try {
                chat.reply(user, session, req.content().trim(), sink);
            } catch (ClientGoneException e) {
                log.debug("Client disconnected from session {}", id);
            } catch (ApiException e) {
                sink.error(e.getMessage());
            } catch (Exception e) {
                log.error("Chat turn failed for session {}", id, e);
                sink.error("Something went wrong while planning. Try sending your message again.");
            } finally {
                sink.complete();
            }
        });
        return emitter;
    }

    /** Thrown when the browser has gone away; stops the pipeline early. */
    static final class ClientGoneException extends RuntimeException {
        ClientGoneException(Throwable cause) {
            super(cause);
        }
    }

    static final class SseSink implements ChatEventSink {
        private final SseEmitter emitter;
        private volatile boolean closed;

        SseSink(SseEmitter emitter) {
            this.emitter = emitter;
            emitter.onCompletion(() -> closed = true);
            emitter.onTimeout(() -> closed = true);
            emitter.onError(e -> closed = true);
        }

        private void send(String name, Object data) {
            if (closed) throw new ClientGoneException(null);
            try {
                emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                closed = true;
                throw new ClientGoneException(e);
            }
        }

        @Override public void status(String text) { send("status", Map.of("text", text)); }

        @Override public void itinerary(Detail itinerary) { send("itinerary", itinerary); }

        @Override public void token(String text) { send("token", Map.of("text", text)); }

        @Override public void done(Long messageId, String provider) {
            send("done", Map.of("messageId", messageId, "provider", provider));
        }

        @Override public void error(String message) {
            try {
                send("error", Map.of("message", message));
            } catch (ClientGoneException ignored) {
                // nobody to tell
            }
        }

        void complete() {
            if (!closed) {
                closed = true;
                emitter.complete();
            }
        }
    }
}
