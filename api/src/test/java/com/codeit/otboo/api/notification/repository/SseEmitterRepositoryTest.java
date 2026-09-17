package com.codeit.otboo.api.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRepositoryTest {
    private final SseEmitterRepository repository = new SseEmitterRepository();

    @Test
    void keepsMultipleConnectionsPerUserAndIsolatesOtherUsers() {
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        var first = new SseEmitter();
        var second = new SseEmitter();
        var other = new SseEmitter();
        repository.save(firstUser, first);
        repository.save(firstUser, second);
        repository.save(firstUser, first);
        repository.save(secondUser, other);

        assertThat(repository.findEmitters(firstUser)).containsExactlyInAnyOrder(first, second);
        assertThat(repository.findEmitters(secondUser)).containsExactly(other);
        repository.delete(firstUser, first);
        assertThat(repository.findEmitters(firstUser)).containsExactly(second);
        assertThat(repository.findEmitters(secondUser)).containsExactly(other);
    }

    @Test
    void removesEmptyUserEntryAndOldCallbackDoesNotRemoveNewConnection() {
        UUID user = UUID.randomUUID();
        var oldConnection = new SseEmitter();
        var newConnection = new SseEmitter();
        repository.save(user, oldConnection);
        repository.delete(user, oldConnection);
        assertThat(repository.findAll()).doesNotContainKey(user);

        repository.save(user, newConnection);
        repository.delete(user, oldConnection);
        assertThat(repository.findEmitters(user)).containsExactly(newConnection);
    }

    @Test
    void callersCannotModifyRepositoryThroughSnapshots() {
        UUID user = UUID.randomUUID();
        var first = new SseEmitter();
        repository.save(user, first);
        var snapshot = repository.findAll();
        assertThatThrownBy(() -> snapshot.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.get(user).clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> repository.findEmitters(user).clear())
                .isInstanceOf(UnsupportedOperationException.class);
        repository.save(user, new SseEmitter());
        assertThat(snapshot.get(user)).containsExactly(first);
        assertThat(repository.findEmitters(user)).hasSize(2);
    }

    @Test
    void concurrentLastDeleteAndNewSaveKeepNewConnection() throws Exception {
        var threads = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 100; i++) {
                UUID user = UUID.randomUUID();
                var oldConnection = new SseEmitter();
                var newConnection = new SseEmitter();
                repository.save(user, oldConnection);
                var start = new CyclicBarrier(2);
                var deletion = threads.submit(() -> {
                    start.await(3, TimeUnit.SECONDS);
                    repository.delete(user, oldConnection);
                    return null;
                });
                var addition = threads.submit(() -> {
                    start.await(3, TimeUnit.SECONDS);
                    repository.save(user, newConnection);
                    return null;
                });
                deletion.get(5, TimeUnit.SECONDS);
                addition.get(5, TimeUnit.SECONDS);
                assertThat(repository.findEmitters(user)).containsExactly(newConnection);
                repository.delete(user, newConnection);
            }
        } finally {
            threads.shutdownNow();
        }
    }
}
