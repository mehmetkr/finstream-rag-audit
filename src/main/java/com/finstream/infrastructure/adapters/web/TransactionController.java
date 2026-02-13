package com.finstream.infrastructure.adapters.web;

import com.finstream.application.dto.TransactionRequest;
import com.finstream.application.usecase.EvaluateTransactionUseCaseImpl;
import com.finstream.domain.model.Amount;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Currency;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final EvaluateTransactionUseCaseImpl evaluateTransactionUseCase;

    public TransactionController(EvaluateTransactionUseCaseImpl evaluateTransactionUseCase) {
        this.evaluateTransactionUseCase = evaluateTransactionUseCase;
    }

    @PostMapping
    public ResponseEntity<Void> submitTransaction(@Valid @RequestBody TransactionRequest request) {
        Transaction transaction = new Transaction(
                TransactionId.generate(),
                new Amount(request.amount(), Currency.getInstance(request.currency())),
                new AccountId(request.fromAccount()),
                new AccountId(request.toAccount()),
                request.description(),
                Instant.now()
        );

        evaluateTransactionUseCase.submit(transaction);

        return ResponseEntity.accepted().build();
    }
}
