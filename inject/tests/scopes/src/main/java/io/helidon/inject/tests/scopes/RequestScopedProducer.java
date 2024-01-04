package io.helidon.inject.tests.scopes;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.inject.service.Injection;

@Injection.Requeston
class RequestScopedProducer implements RequestScopedContract {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final int id = COUNTER.incrementAndGet();

    @Override
    public int id() {
        return id;
    }
}
