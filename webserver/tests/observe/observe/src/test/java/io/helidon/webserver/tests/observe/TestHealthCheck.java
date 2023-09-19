package io.helidon.webserver.tests.observe;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;

class TestHealthCheck implements HealthCheck {
    private final AtomicInteger calls = new AtomicInteger();
    private final String message;

    TestHealthCheck(String message) {
        this.message = message;
    }

    @Override
    public String path() {
        return "test";
    }

    @Override
    public HealthCheckResponse call() {
        calls.incrementAndGet();
        return HealthCheckResponse.builder()
                .detail("message", message)
                .status(true)
                .build();
    }
}
