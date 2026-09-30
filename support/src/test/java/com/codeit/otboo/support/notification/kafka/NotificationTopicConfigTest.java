package com.codeit.otboo.support.notification.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationTopicConfigTest {
    private final NotificationKafkaConfig config = new NotificationKafkaConfig();

    @Test
    void createTopicHasOnePartitionPerWorkerThread() {
        var topic = config.notificationCreateTopic();

        assertThat(topic.name()).isEqualTo(NotificationTopics.CREATE);
        assertThat(topic.numPartitions()).isEqualTo(4);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    void broadcastTopicKeepsASinglePartition() {
        var topic = config.notificationBroadcastTopic();

        assertThat(topic.name()).isEqualTo(NotificationTopics.BROADCAST);
        assertThat(topic.numPartitions()).isEqualTo(1);
    }
}
