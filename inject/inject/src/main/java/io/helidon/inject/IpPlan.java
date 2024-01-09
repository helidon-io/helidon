package io.helidon.inject;

import java.util.function.Supplier;

import io.helidon.inject.service.ServiceInfo;

/**
 * Injection point plan of injection.
 *
 * @param valueSupplier supplier of the value
 * @param descriptors descriptor(s) used to obtain the value(s) in the supplier
 * @param <T> type of the value
 */
record IpPlan<T>(Supplier<T> valueSupplier,
                 ServiceInfo... descriptors) implements Supplier<T> {
    @Override
    public T get() {
        return valueSupplier.get();
    }
}
