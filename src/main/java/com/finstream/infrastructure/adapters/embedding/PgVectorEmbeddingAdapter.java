package com.finstream.infrastructure.adapters.embedding;

import com.finstream.domain.model.ScoredTransaction;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.TransactionId;
import com.finstream.domain.ports.outbound.EmbeddingStorePort;
import com.finstream.domain.ports.outbound.TransactionRepository;
import com.finstream.infrastructure.observability.AuditTraceContext;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PgVectorEmbeddingAdapter implements EmbeddingStorePort {

    private static final Logger log = LoggerFactory.getLogger(PgVectorEmbeddingAdapter.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final TransactionRepository transactionRepository;
    private final Tracer tracer;

    public PgVectorEmbeddingAdapter(EmbeddingModel embeddingModel,
                                     EmbeddingStore<TextSegment> embeddingStore,
                                     TransactionRepository transactionRepository,
                                     OpenTelemetry openTelemetry) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.transactionRepository = transactionRepository;
        this.tracer = openTelemetry.getTracer("finstream-rag-audit");
    }

    @Override
    public void store(TransactionId id, Transaction transaction) {
        String text = toTextRepresentation(transaction);
        Embedding embedding = embeddingModel.embed(text).content();
        embeddingStore.addAll(
                List.of(id.value().toString()),
                List.of(embedding),
                List.of(TextSegment.from(text)));
        log.debug("Stored embedding for transaction: {}", id.value());
    }

    @Override
    public List<ScoredTransaction> findSimilar(Transaction transaction, int maxResults) {
        Span span = tracer.spanBuilder("rag.retrieval")
                .setAttribute("rag.store.type", "pgvector")
                .startSpan();

        // Apply business context if available
        AuditTraceContext ctx = AuditTraceContext.getCurrent();
        if (ctx != null) {
            ctx.applyToSpan(span);
        }

        try (Scope scope = span.makeCurrent()) {
            String text = toTextRepresentation(transaction);
            Embedding queryEmbedding = embeddingModel.embed(text).content();

            var request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(0.0)
                    .build();

            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();

            List<TransactionId> ids = matches.stream()
                    .map(match -> new TransactionId(UUID.fromString(match.embeddingId())))
                    .toList();

            Map<UUID, Transaction> transactionMap = transactionRepository.findAllByIds(ids).stream()
                    .collect(Collectors.toMap(tx -> tx.id().value(), tx -> tx));

            List<ScoredTransaction> results = matches.stream()
                    .filter(match -> transactionMap.containsKey(UUID.fromString(match.embeddingId())))
                    .map(match -> new ScoredTransaction(
                            transactionMap.get(UUID.fromString(match.embeddingId())),
                            match.score()))
                    .toList();

            span.setAttribute("rag.results.count", results.size());
            span.setStatus(StatusCode.OK);
            return results;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    static String toTextRepresentation(Transaction transaction) {
        return "%s %s from %s to %s: %s".formatted(
                transaction.amount().value().toPlainString(),
                transaction.amount().currency().getCurrencyCode(),
                transaction.fromAccount().value(),
                transaction.toAccount().value(),
                transaction.description());
    }
}
