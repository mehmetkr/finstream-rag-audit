package com.finstream.application.usecase;

import com.finstream.domain.model.Transaction;
import com.finstream.domain.ports.inbound.EvaluateTransactionUseCase;
import com.finstream.domain.ports.outbound.EventPublisherPort;
import org.springframework.stereotype.Service;

@Service
public class EvaluateTransactionUseCaseImpl implements EvaluateTransactionUseCase {

    private final EventPublisherPort eventPublisher;

    public EvaluateTransactionUseCaseImpl(EventPublisherPort eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void submit(Transaction transaction) {
        eventPublisher.publishTransactionReceived(transaction);
    }
}
