package com.codeit.otboo.api.notification.config;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class NotificationSseExecutorConfig {
    private static final int SENDER_COUNT = 4;
    private static final int QUEUE_CAPACITY = 128;

    @Bean(destroyMethod = "close")
    public NotificationSseExecutors notificationSseExecutors() {
        // 연결별로 하나의 실행기에 배정해 heartbeat와 알림의 쓰기 순서를 유지한다.
        List<ExecutorService> senders = IntStream.range(0, SENDER_COUNT)
                .<ExecutorService>mapToObj(index -> new ThreadPoolExecutor(
                        1, 1, 0L, TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
                            Thread thread = new Thread(runnable, "notification-sse-send-" + index);
                            thread.setDaemon(true);
                            return thread;
                        }, new ThreadPoolExecutor.AbortPolicy()))
                .toList();

        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "notification-sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });

        return new NotificationSseExecutors(senders, heartbeat);
    }

    public record NotificationSseExecutors(
            List<ExecutorService> senders,
            ScheduledExecutorService heartbeat
    ) implements AutoCloseable {
        @Override
        public void close() {
            heartbeat.shutdownNow();
            senders.forEach(ExecutorService::shutdownNow);
        }
    }
}
