package io.helidon.inject.service;

import java.util.function.Supplier;

import io.helidon.common.types.TypeName;

/**
 * A service can implement this type to act as a provider of instances of type {@code T}.
 * <p>
 * Responsibility to create and manage instance is fully on this provider instance.
 *
 * @param <T> type of the provided service
 */
public interface ServiceProvider<T> extends Supplier<T> {
    /**
     * Type name of this interface.
     */
    TypeName TYPE = TypeName.create(ServiceProvider.class);
}
