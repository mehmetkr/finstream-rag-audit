package com.finstream.infrastructure.adapters.web;

import com.finstream.application.dto.TransactionRequest;
import com.finstream.domain.model.Amount;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import com.finstream.domain.ports.inbound.EvaluateTransactionUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.Currency;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final EvaluateTransactionUseCase evaluateTransactionUseCase;
    private final Clock clock;

    public TransactionController(EvaluateTransactionUseCase evaluateTransactionUseCase, Clock clock) {
        this.evaluateTransactionUseCase = evaluateTransactionUseCase;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<Void> submitTransaction(@Valid @RequestBody TransactionRequest request) {
        Transaction transaction = new Transaction(
                TransactionId.generate(),
                new Amount(request.amount(), Currency.getInstance(request.currency())),
                new AccountId(request.fromAccount()),
                new AccountId(request.toAccount()),
                request.description(),
                Instant.now(clock)
        );

        evaluateTransactionUseCase.submit(transaction);

        return ResponseEntity.accepted().build();
    }
}
