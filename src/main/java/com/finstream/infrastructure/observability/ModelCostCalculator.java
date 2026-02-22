package com.finstream.infrastructure.observability;

import java.util.Map;

/**
 * Calculates the USD cost of LLM and embedding API calls based on
 * per-token pricing for each model. Prices are per 1,000 tokens.
 *
 * Update prices when model pricing changes. Source: https://openai.com/pricing
 */
public class ModelCostCalculator {

    public record ModelPricing(double inputPer1kTokens, double outputPer1kTokens) {}

    // Prices as of early 2026 — update as needed
    private static final Map<String, ModelPricing> PRICING = Map.of(
            "gpt-4o", new ModelPricing(0.0025, 0.0100),
            "gpt-4o-mini", new ModelPricing(0.000150, 0.000600),
            "gpt-4-turbo", new ModelPricing(0.0100, 0.0300),
            "gpt-4", new ModelPricing(0.0300, 0.0600),
            "gpt-3.5-turbo", new ModelPricing(0.0005, 0.0015),
            "text-embedding-3-small", new ModelPricing(0.00002, 0.0),
            "text-embedding-3-large", new ModelPricing(0.00013, 0.0)
    );

    /**
     * Calculates total cost in USD for a single API call.
     *
     * @param modelName    the model identifier (e.g., "gpt-4o")
     * @param inputTokens  number of input/prompt tokens
     * @param outputTokens number of output/completion tokens (0 for embeddings)
     * @return cost in USD, or 0.0 if model pricing is unknown
     */
    public static double calculateCost(String modelName, int inputTokens, int outputTokens) {
        if (modelName == null) return 0.0;
        ModelPricing pricing = PRICING.get(modelName);
        if (pricing == null) {
            // Try matching with prefix (handles versioned model names like "gpt-4o-2024-08-06")
            pricing = PRICING.entrySet().stream()
                    .filter(e -> modelName != null && modelName.startsWith(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
        if (pricing == null) return 0.0;

        return (inputTokens / 1000.0) * pricing.inputPer1kTokens()
                + (outputTokens / 1000.0) * pricing.outputPer1kTokens();
    }
}
