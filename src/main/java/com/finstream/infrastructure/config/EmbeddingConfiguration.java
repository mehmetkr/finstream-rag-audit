package com.finstream.infrastructure.config;

import com.finstream.infrastructure.observability.TracedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class EmbeddingConfiguration {

    @Bean
    EmbeddingModel embeddingModel(Tracer tracer) {
        return new TracedEmbeddingModel(new AllMiniLmL6V2EmbeddingModel(), tracer,
                "all-MiniLM-L6-v2", "onnx");
    }

    @Bean
    EmbeddingStore<TextSegment> embeddingStore(DataSource dataSource) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("transaction_embeddings")
                .dimension(384)
                .createTable(true)
                .useIndex(false)
                .build();
    }
}
