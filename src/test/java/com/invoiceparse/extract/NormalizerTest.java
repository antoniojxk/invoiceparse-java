package com.invoiceparse.extract;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class NormalizerTest {
    @Test void normalizesIndianAmountsAndNegatives() {
        assertThat(NumberNormalizer.parse("Rs. 1,23,456.78")).contains(new BigDecimal("123456.78"));
        assertThat(NumberNormalizer.parse("(42.50)")).contains(new BigDecimal("-42.50"));
        assertThat(NumberNormalizer.parse("many")).isEmpty();
    }
    @Test void normalizesCommonInvoiceDates() {
        assertThat(DateNormalizer.parse("7/08/2026")).contains(LocalDate.of(2026, 8, 7));
        assertThat(DateNormalizer.parse("07-Aug-2026")).contains(LocalDate.of(2026, 8, 7));
        assertThat(DateNormalizer.parse("not a date")).isEmpty();
    }
}
