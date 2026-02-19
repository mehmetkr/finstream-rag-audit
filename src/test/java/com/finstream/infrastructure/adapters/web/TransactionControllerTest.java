package com.finstream.infrastructure.adapters.web;

import com.finstream.domain.ports.inbound.EvaluateTransactionUseCase;
import com.finstream.infrastructure.config.SecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import(SecurityConfiguration.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluateTransactionUseCase evaluateTransactionUseCase;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUpClock() {
        org.mockito.Mockito.when(clock.instant())
                .thenReturn(Instant.parse("2024-01-01T00:00:00Z"));
        org.mockito.Mockito.when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

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
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
    }

    @Test
    void should_return_401_when_unauthenticated() throws Exception {
        String body = """
                {
                    "amount": 100.00,
                    "currency": "USD",
                    "fromAccount": "GB1234567890",
                    "toAccount": "US9876543210",
                    "description": "Unauthorized attempt"
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return_400_for_missing_required_fields() throws Exception {
        String body = """
                {
                    "description": "Incomplete transaction"
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .with(jwt())
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
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return_structured_error_for_invalid_account_id() throws Exception {
        String body = """
                {
                    "amount": 100.00,
                    "currency": "USD",
                    "fromAccount": "invalid",
                    "toAccount": "US9876543210",
                    "description": "Bad account format"
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists());
    }
}
