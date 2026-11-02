package com.finguard.fraud.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FraudRuleUpdateRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalidFields(FraudRuleUpdateRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void emptyPatch_isRejected() {
        assertThat(invalidFields(new FraudRuleUpdateRequest(null, null))).containsExactly("anyFieldPresent");
    }

    @Test
    void weightOutsideOneToHundred_isRejected() {
        assertThat(invalidFields(new FraudRuleUpdateRequest(null, 0))).containsExactly("weight");
        assertThat(invalidFields(new FraudRuleUpdateRequest(null, 101))).containsExactly("weight");
    }

    @Test
    void singleFieldPatches_areValid() {
        assertThat(invalidFields(new FraudRuleUpdateRequest(false, null))).isEmpty();
        assertThat(invalidFields(new FraudRuleUpdateRequest(null, 100))).isEmpty();
    }
}
