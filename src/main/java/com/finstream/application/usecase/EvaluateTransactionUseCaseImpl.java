package com.finstream.application.usecase;

import com.finstream.domain.event.TransactionReceived;
import com.finstream.domain.model.RequestContext;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.ports.inbound.EvaluateTransactionUseCase;
import com.finstream.domain.ports.outbound.EventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class EvaluateTransactionUseCaseImpl implements EvaluateTransactionUseCase {

    private static final Logger log = LoggerFactory.getLogger(EvaluateTransactionUseCaseImpl.class);

    private final EventPublisherPort eventPublisher;
    private final Clock clock;

    public EvaluateTransactionUseCaseImpl(EventPublisherPort eventPublisher, Clock clock) {
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void submit(Transaction transaction) {
        String traceId = RequestContext.TRACE_ID.isBound() ? RequestContext.TRACE_ID.get() : "no-trace";
        log.info("[{}] Evaluating transaction: {}", traceId, transaction.id().value());

        eventPublisher.publish(new TransactionReceived(transaction, Instant.now(clock)));
    }
}
