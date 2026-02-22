package com.finstream.infrastructure.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ModelCostCalculatorTest {

    @Test
    void nullModelReturnsZero() {
        assertThat(ModelCostCalculator.calculateCost(null, 1000, 1000)).isEqualTo(0.0);
    }

    @Test
    void unknownModelReturnsZero() {
        assertThat(ModelCostCalculator.calculateCost("unknown-model", 1000, 1000)).isEqualTo(0.0);
    }

    @Test
    void localOnnxModelReturnsZero() {
        assertThat(ModelCostCalculator.calculateCost("all-MiniLM-L6-v2", 500, 0)).isEqualTo(0.0);
    }

    @ParameterizedTest
    @CsvSource({
            "gpt-4o,        1000, 1000, 0.0125",
            "gpt-4o-mini,   1000, 1000, 0.00075",
            "gpt-3.5-turbo, 1000, 0,    0.0005"
    })
    void knownModelCalculatesCorrectCost(String model, int input, int output, double expected) {
        assertThat(ModelCostCalculator.calculateCost(model, input, output))
                .isCloseTo(expected, within(0.000001));
    }

    @Test
    void versionedModelMatchesByPrefix() {
        double cost = ModelCostCalculator.calculateCost("gpt-4o-2024-08-06", 1000, 1000);
        assertThat(cost).isGreaterThan(0.0);
    }

    @Test
    void zeroTokensReturnZeroCost() {
        assertThat(ModelCostCalculator.calculateCost("gpt-4o", 0, 0)).isEqualTo(0.0);
    }
}
