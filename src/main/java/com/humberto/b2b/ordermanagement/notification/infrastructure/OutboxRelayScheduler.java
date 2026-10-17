package com.humberto.b2b.ordermanagement.notification.infrastructure;

import com.humberto.b2b.ordermanagement.notification.outbox.OutboxRelay;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OutboxRelayScheduler {

    private final OutboxRelay outboxRelay;

    OutboxRelayScheduler(OutboxRelay outboxRelay) {
        this.outboxRelay = outboxRelay;
    }

    @Scheduled(fixedDelayString = "${notification.outbox.relay-interval:PT1S}")
    void relayPending() {
        outboxRelay.relayPending();
    }
}
