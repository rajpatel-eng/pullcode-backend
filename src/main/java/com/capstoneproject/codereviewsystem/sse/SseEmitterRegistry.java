package com.capstoneproject.codereviewsystem.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterRegistry {

    private final ObjectMapper objectMapper;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    private static final long TIMEOUT_MS = 30 * 60 * 1000L; // 30 min

    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        log.debug("SSE registered userId={} connections={}", userId,
                emitters.get(userId).size());
        return emitter;
    }

    public void emit(Long userId, ReviewProgressEvent event) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) return;

        String data;
        try { data = objectMapper.writeValueAsString(event); }
        catch (Exception e) { log.error("SSE serialize error: {}", e.getMessage()); return; }

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("review-progress").data(data));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        dead.forEach(e -> remove(userId, e));
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(userId);
        }
    }
}
