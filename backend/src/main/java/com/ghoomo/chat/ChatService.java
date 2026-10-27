package com.ghoomo.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghoomo.ai.LlmRequest;
import com.ghoomo.ai.LlmRequest.Message;
import com.ghoomo.ai.LlmRequest.Task;
import com.ghoomo.ai.LlmRouter;
import com.ghoomo.ai.MockLlmProvider;
import com.ghoomo.chat.ChatDtos.MessageView;
import com.ghoomo.chat.ChatDtos.SessionView;
import com.ghoomo.common.ApiException;
import com.ghoomo.itinerary.ItineraryDtos.Detail;
import com.ghoomo.itinerary.ItineraryService;
import com.ghoomo.itinerary.PlanJson;
import com.ghoomo.planner.PromptTemplates;
import com.ghoomo.planner.TripIntent;
import com.ghoomo.planner.TripPlanner;
import com.ghoomo.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int HISTORY_TURNS = 12;

    private final SessionRepository sessions;
    private final MessageRepository messages;
    private final TripPlanner planner;
    private final ItineraryService itineraries;
    private final LlmRouter llm;
    private final ObjectMapper mapper;

    public ChatService(SessionRepository sessions, MessageRepository messages, TripPlanner planner,
                       ItineraryService itineraries, LlmRouter llm, ObjectMapper mapper) {
        this.sessions = sessions;
        this.messages = messages;
        this.planner = planner;
        this.itineraries = itineraries;
        this.llm = llm;
        this.mapper = mapper;
    }

    // ---------- sessions ----------

    @Transactional
    public SessionView createSession(Long userId) {
        return SessionView.of(sessions.save(new ChatSession(userId)));
    }

    @Transactional(readOnly = true)
    public List<SessionView> listSessions(Long userId) {
        return sessions.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(SessionView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<MessageView> history(Long userId, Long sessionId) {
        requireSession(userId, sessionId);
        return messages.findBySessionIdOrderByIdAsc(sessionId).stream().map(MessageView::of).toList();
    }

    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        sessions.delete(requireSession(userId, sessionId));
    }

    public ChatSession requireSession(Long userId, Long sessionId) {
        return sessions.findByIdAndUserId(sessionId, userId).orElseThrow(() -> ApiException.notFound("Chat"));
    }

    // ---------- the conversation turn ----------

    /**
     * Runs one turn and reports progress through the sink. Called on a background thread.
     * Order of events: status* → itinerary? → token* → done | error
     */
    public void reply(User user, ChatSession session, String content, ChatEventSink sink) {
        messages.save(new ChatMessage(session.getId(), ChatMessage.Role.USER, content, null));
        if (ChatSession.DEFAULT_TITLE.equals(session.getTitle())) {
            session.setTitle(content.length() > 60 ? content.substring(0, 57).trim() + "…" : content);
        }
        session.touch();
        sessions.save(session);

        List<Message> history = loadHistory(session.getId());

        sink.status("Understanding your request");
        TripIntent intent = planner.detectIntent(history);

        StringBuilder reply = new StringBuilder();
        Long itineraryId = null;
        String provider;

        if (intent.wantsPlan()) {
            TripPlanner.PlanResult result = planner.plan(intent, user, sink::status);
            Detail saved = itineraries.create(user.getId(), result);
            itineraryId = saved.id();
            sink.itinerary(saved);

            String budgetLine = result.intent().budgetInr() == null ? "No budget given."
                    : "Budget: ₹" + result.intent().budgetInr() + " for " + result.intent().travelers() + " traveller(s).";
            var summaryReq = new LlmRequest(Task.SUMMARY, PromptTemplates.summary(),
                    List.of(Message.user(budgetLine + "\nItinerary:\n" + PlanJson.write(mapper, saved.plan()))),
                    false, 0.7, Map.of(MockLlmProvider.HINT_PLAN, saved.plan()));
            provider = llm.stream(summaryReq, token -> {
                reply.append(token);
                sink.token(token);
            });
        } else {
            var chatReq = new LlmRequest(Task.CHAT, PromptTemplates.chat(user, LocalDate.now()), history, false, 0.7, Map.of());
            provider = llm.stream(chatReq, token -> {
                reply.append(token);
                sink.token(token);
            });
        }

        String text = reply.isEmpty() ? "Done." : reply.toString();
        ChatMessage saved = messages.save(new ChatMessage(session.getId(), ChatMessage.Role.ASSISTANT, text, itineraryId));
        log.info("chat turn session={} provider={} itinerary={}", session.getId(), provider, itineraryId);
        sink.done(saved.getId(), provider);
    }

    private List<Message> loadHistory(Long sessionId) {
        List<ChatMessage> recent = new ArrayList<>(messages.findBySessionIdOrderByIdDesc(sessionId, PageRequest.of(0, HISTORY_TURNS)));
        Collections.reverse(recent);
        List<Message> out = new ArrayList<>();
        for (ChatMessage m : recent) {
            if (m.getRole() == ChatMessage.Role.USER) {
                out.add(Message.user(m.getContent()));
            } else {
                String c = m.getItineraryId() == null ? m.getContent()
                        : m.getContent() + "\n[An itinerary was created and shown to the user.]";
                out.add(Message.assistant(c));
            }
        }
        // Gemini requires the conversation to start with a user turn.
        while (!out.isEmpty() && out.get(0).role() == LlmRequest.Role.ASSISTANT) out.remove(0);
        return out;
    }
}
