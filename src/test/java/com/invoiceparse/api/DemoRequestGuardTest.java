package com.invoiceparse.api;

import com.invoiceparse.config.InvoiceParseProperties;
import com.invoiceparse.exception.DocumentProcessingException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoRequestGuardTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC);

    @Test void enforcesPerClientLimit() {
        var guard = guard(2, 10, 1);
        try (var ignored = guard.acquire("198.51.100.10")) { }
        try (var ignored = guard.acquire("198.51.100.10")) { }

        assertCode(() -> guard.acquire("198.51.100.10"), "RATE_LIMITED");
    }

    @Test void enforcesGlobalLimitAcrossClients() {
        var guard = guard(10, 2, 1);
        try (var ignored = guard.acquire("198.51.100.10")) { }
        try (var ignored = guard.acquire("198.51.100.11")) { }

        assertCode(() -> guard.acquire("198.51.100.12"), "RATE_LIMITED");
    }

    @Test void rejectsConcurrentWorkInsteadOfQueueingIt() {
        var guard = guard(10, 10, 1);
        try (var ignored = guard.acquire("198.51.100.10")) {
            assertCode(() -> guard.acquire("198.51.100.11"), "DEMO_BUSY");
        }
    }

    private DemoRequestGuard guard(int clientLimit, int globalLimit, int concurrentLimit) {
        var settings = new InvoiceParseProperties.DemoAccess(true, 600, clientLimit, globalLimit, concurrentLimit);
        return new DemoRequestGuard(settings, clock);
    }

    private void assertCode(Runnable request, String expectedCode) {
        assertThatThrownBy(request::run)
                .isInstanceOf(DocumentProcessingException.class)
                .satisfies(error -> assertThat(((DocumentProcessingException) error).getCode()).isEqualTo(expectedCode));
    }
}
