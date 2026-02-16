package com.finstream.domain.ports.inbound;

import com.finstream.domain.model.FraudDecision;
import com.finstream.domain.model.Transaction;

public interface FraudEvaluationUseCase {
    FraudDecision evaluate(Transaction transaction);
}
