package com.finstream.domain.ports.outbound;

import com.finstream.domain.model.Transaction;

public interface EventPublisherPort {
    void publishTransactionReceived(Transaction transaction);
}
