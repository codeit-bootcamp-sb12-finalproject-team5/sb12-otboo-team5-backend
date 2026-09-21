package com.codeit.otboo.api.notification.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Repository
public class SseEmitterRepository {
    private final Map<UUID, Set<SseEmitter>> store = new ConcurrentHashMap<>();

    public void save(UUID receiverId, SseEmitter emitter) {
        store.compute(receiverId, (id, emitters) -> {
            if (emitters == null) {
                emitters = new CopyOnWriteArraySet<>();
            }
            emitters.add(emitter);
            return emitters;
        });
    }

    public Set<SseEmitter> findEmitters(UUID receiverId) {
        Set<SseEmitter> emitters = store.get(receiverId);
        return emitters == null ? Set.of() : Set.copyOf(emitters);
    }

    public Map<UUID, Set<SseEmitter>> findAll() {
        Map<UUID, Set<SseEmitter>> result = new HashMap<>();
        store.forEach((receiverId, emitters) -> result.put(receiverId, Set.copyOf(emitters)));
        return Map.copyOf(result);
    }

    public int count() {
        return store.values().stream().mapToInt(Set::size).sum();
    }

    public int countByReceiver(UUID receiverId) {
        Set<SseEmitter> emitters = store.get(receiverId);
        return emitters == null ? 0 : emitters.size();
    }

    public void delete(UUID receiverId, SseEmitter emitter) {
        store.computeIfPresent(receiverId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
