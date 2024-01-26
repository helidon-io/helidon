package io.helidon.service.core;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Basic implementation of the service registry without injection support.
 */
class CoreServiceRegistry implements ServiceRegistry {
    @Override
    public <T> T get(Class<T> contract) {
        return null;
    }

    @Override
    public <T> Optional<T> first(Class<T> contract) {
        return Optional.empty();
    }

    @Override
    public <T> List<T> all(Class<T> contract) {
        return null;
    }

    @Override
    public <T> Supplier<T> supply(Class<T> contract) {
        return null;
    }

    @Override
    public <T> Supplier<Optional<T>> supplyFirst(Class<T> contract) {
        return null;
    }

    @Override
    public <T> Supplier<List<T>> supplyAll(Class<T> contract) {
        return null;
    }
}
