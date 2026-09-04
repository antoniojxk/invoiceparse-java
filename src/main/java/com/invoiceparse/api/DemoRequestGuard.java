package com.invoiceparse.api;

import com.invoiceparse.config.InvoiceParseProperties;
import com.invoiceparse.exception.DocumentProcessingException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Component
public class DemoRequestGuard {
    private final InvoiceParseProperties.DemoAccess settings;
    private final Clock clock;
    private final Semaphore concurrentRequests;
    private final Map<String, Integer> requestsByClient = new HashMap<>();
    private long activeWindow = Long.MIN_VALUE;
    private int globalRequests;

    @Autowired
    public DemoRequestGuard(InvoiceParseProperties properties) {
        this(properties.demoAccess(), Clock.systemUTC());
    }

    DemoRequestGuard(InvoiceParseProperties.DemoAccess settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
        this.concurrentRequests = new Semaphore(settings.maxConcurrentRequests(), true);
    }

    public Permit acquire(String clientAddress) {
        if (!settings.enabled()) return Permit.NO_OP;
        if (!concurrentRequests.tryAcquire()) {
            throw new DocumentProcessingException("DEMO_BUSY",
                    "The public demo is processing another invoice. Please try again shortly.");
        }
        try {
            if (!recordRequest(normalizeClient(clientAddress))) {
                throw new DocumentProcessingException("RATE_LIMITED",
                        "The public demo request limit has been reached. Please try again later.");
            }
            return concurrentRequests::release;
        } catch (RuntimeException error) {
            concurrentRequests.release();
            throw error;
        }
    }

    private synchronized boolean recordRequest(String client) {
        long window = clock.instant().getEpochSecond() / settings.windowSeconds();
        if (window != activeWindow) {
            activeWindow = window;
            globalRequests = 0;
            requestsByClient.clear();
        }
        int clientRequests = requestsByClient.getOrDefault(client, 0);
        if (globalRequests >= settings.maxRequestsGlobal()
                || clientRequests >= settings.maxRequestsPerClient()) return false;
        globalRequests++;
        requestsByClient.put(client, clientRequests + 1);
        return true;
    }

    private String normalizeClient(String clientAddress) {
        if (clientAddress == null || clientAddress.isBlank()) return "unknown";
        return clientAddress.substring(0, Math.min(clientAddress.length(), 64));
    }

    @FunctionalInterface
    public interface Permit extends AutoCloseable {
        Permit NO_OP = () -> { };
        @Override void close();
    }
}
