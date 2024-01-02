package io.helidon.inject;

import java.util.function.Supplier;

import io.helidon.inject.service.ServiceProvider;

final class ServiceProviderFactory {
    private ServiceProviderFactory() {
    }

    static <T> ServiceProvider<T> create(Supplier<T> supplier) {
        return new SupplierProvider<>(supplier);
    }

    private static class SupplierProvider<T> implements ServiceProvider<T> {
        private final Supplier<T> supplier;

        private SupplierProvider(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public T get() {
            return supplier.get();
        }
    }
}
