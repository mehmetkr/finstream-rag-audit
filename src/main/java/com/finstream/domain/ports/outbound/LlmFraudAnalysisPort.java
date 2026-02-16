package com.finstream.domain.ports.outbound;

import com.finstream.domain.model.LlmFraudAssessment;
import com.finstream.domain.model.RedactedTransaction;
import com.finstream.domain.model.ScoredTransaction;

import java.util.List;

public interface LlmFraudAnalysisPort {
    LlmFraudAssessment analyze(RedactedTransaction transaction, List<ScoredTransaction> similarTransactions);
}
