package com.ghoomo.common;

import com.ghoomo.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limiter, per user, kept in memory.
 * Protects the free LLM quota from one user (or a stuck client) burning it all.
 */
@Component
public class RateLimiter {

    private static final long WINDOW_MS = 60_000;

    private final int limit;
    private final Map<Long, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimiter(AppProperties props) {
        this.limit = Math.max(1, props.rateLimit().chatPerMinute());
    }

    public void check(Long userId) {
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) q.pollFirst();
            if (q.size() >= limit) {
                long waitSec = Math.max(1, (WINDOW_MS - (now - q.peekFirst())) / 1000);
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "You're sending messages quickly. Try again in " + waitSec + " seconds.");
            }
            q.addLast(now);
        }
    }
}
