package com.finstream.domain.ports.outbound;

import com.finstream.domain.model.LlmFraudAssessment;
import com.finstream.domain.model.ScoredTransaction;
import com.finstream.domain.model.Transaction;

import java.util.List;

public interface LlmFraudAnalysisPort {
    LlmFraudAssessment analyze(Transaction transaction, List<ScoredTransaction> similarTransactions);
}
