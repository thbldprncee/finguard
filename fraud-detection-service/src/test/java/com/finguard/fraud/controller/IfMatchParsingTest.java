package com.finguard.fraud.controller;

import com.finguard.fraud.exception.InvalidHeaderException;
import com.finguard.fraud.exception.PreconditionRequiredException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IfMatchParsingTest {

    @ParameterizedTest
    @ValueSource(strings = {"7", "\"7\"", "W/\"7\"", " \"7\" "})
    void acceptsStrongAndWeakEntityTags(String header) {
        assertThat(FraudController.parseIfMatch(header)).isEqualTo(7);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingHeader_is428(String header) {
        assertThatThrownBy(() -> FraudController.parseIfMatch(header)).isInstanceOf(PreconditionRequiredException.class);
    }

    @Test
    void garbage_is400() {
        assertThatThrownBy(() -> FraudController.parseIfMatch("*")).isInstanceOf(InvalidHeaderException.class);
        assertThatThrownBy(() -> FraudController.parseIfMatch("\"abc\"")).isInstanceOf(InvalidHeaderException.class);
    }
}
