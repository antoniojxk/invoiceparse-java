package com.invoiceparse.validation;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GstinValidatorTest {
    private final GstinValidator validator = new GstinValidator();
    @Test void validatesFormat() {
        assertThat(validator.isValid("27ABCDE1234F1Z5")).isTrue();
        assertThat(validator.isValid("ABCDE1234F1Z5")).isFalse();
        assertThat(validator.isValid(null)).isFalse();
    }
}
