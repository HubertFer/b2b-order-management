package com.humberto.b2b.ordermanagement.notification.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxStore outboxStore;
    private final NotificationPublisher notificationPublisher;
    private final Clock clock;
    private final int batchSize;

    public OutboxRelay(OutboxStore outboxStore, NotificationPublisher notificationPublisher, Clock clock, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be greater than zero");
        }
        this.outboxStore = outboxStore;
        this.notificationPublisher = notificationPublisher;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Transactional
    public int relayPending() {
        int published = 0;
        for (PendingOutboxMessage pending : outboxStore.lockPending(batchSize)) {
            try {
                notificationPublisher.publish(pending.message());
            } catch (RuntimeException exception) {
                log.warn("Failed to publish outbox event {}, will retry", pending.message().eventId(), exception);
                break;
            }
            outboxStore.markPublished(pending.id(), clock.instant());
            published++;
        }
        return published;
    }
}
