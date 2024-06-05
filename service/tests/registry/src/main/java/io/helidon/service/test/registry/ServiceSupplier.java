package io.helidon.service.test.registry;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.service.registry.Service;

@Service.Provider
class ServiceSupplier implements Supplier<SuppliedContract> {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Override
    public SuppliedContract get() {
        int i = COUNTER.incrementAndGet();
        return () -> "Supplied:" + i;
    }
}
