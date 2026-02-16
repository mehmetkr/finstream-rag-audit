package com.finstream.domain.ports.outbound;

import com.finstream.domain.event.TransactionEvent;

public interface EventPublisherPort {
    void publish(TransactionEvent event);
}
