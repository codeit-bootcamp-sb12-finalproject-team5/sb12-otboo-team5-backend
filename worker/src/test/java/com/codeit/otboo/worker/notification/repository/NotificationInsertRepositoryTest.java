package com.codeit.otboo.worker.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.codeit.otboo.domain.notification.dto.NotificationDto;
import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class NotificationInsertRepositoryTest {
    @Test
    void emptyPageDoesNotAccessDatabase() {
        var jdbc = mock(JdbcTemplate.class);
        var repository = new NotificationInsertRepository(jdbc);
        assertThat(repository.insertPage(NotificationType.FEED_CREATED, "key",
                List.of(), "title", "", NotificationLevel.INFO)).isEmpty();
        verifyNoInteractions(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fiveHundredRecipientsUseOneStatementWithBoundValues() {
        var jdbc = mock(JdbcTemplate.class);
        var repository = new NotificationInsertRepository(jdbc);
        var receivers = IntStream.range(0, 500).mapToObj(i -> UUID.randomUUID()).toList();
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    Object[] parameters = (Object[]) invocation.getRawArguments()[2];
                    assertThat(sql).doesNotContain("user's title");
                    assertThat(sql).contains("ON CONFLICT (receiver_id, deduplication_key) DO NOTHING");
                    assertThat(parameters).hasSize(1006);
                    assertThat(parameters[1]).isEqualTo("user's title");
                    assertThat(parameters[2]).isEqualTo("");
                    for (int i = 0; i < 500; i++) {
                        assertThat(((UUID) parameters[6 + i * 2]).version()).isEqualTo(7);
                        assertThat(parameters[7 + i * 2]).isEqualTo(receivers.get(i));
                    }
                    return List.<NotificationDto>of();
                });
        repository.insertPage(NotificationType.FEED_CREATED, "key", receivers,
                "user's title", "", NotificationLevel.INFO);
        verify(jdbc, times(1)).query(anyString(), any(RowMapper.class), any(Object[].class));
    }
}
