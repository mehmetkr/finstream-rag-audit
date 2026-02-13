package com.finstream.infrastructure.adapters.web;

import com.finstream.application.usecase.EvaluateTransactionUseCaseImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluateTransactionUseCaseImpl evaluateTransactionUseCase;

    @Test
    void should_return_202_for_valid_transaction() throws Exception {
        String body = """
                {
                    "amount": 100.00,
                    "currency": "USD",
                    "fromAccount": "GB1234567890",
                    "toAccount": "US9876543210",
                    "description": "Payment for services"
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
    }

    @Test
    void should_return_400_for_missing_required_fields() throws Exception {
        String body = """
                {
                    "description": "Incomplete transaction"
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return_400_for_negative_amount() throws Exception {
        String body = """
                {
                    "amount": -50.00,
                    "currency": "USD",
                    "fromAccount": "GB1234567890",
                    "toAccount": "US9876543210",
                    "description": "Negative amount"
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
