package io.helidon.inject.service;

import java.util.Set;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;

/**
 * An instance managed by the service registry, with a subset of relevant metadata.
 * This type is injectable in the same manner as a regular service instance.
 *
 * @param <T> type of the instance
 */
public interface RegistryInstance<T> extends Supplier<T> {
    /**
     * Type name of this interface. {@link io.helidon.common.types.TypeName} is used in various APIs of service registry.
     */
    TypeName TYPE_NAME = TypeName.create(RegistryInstance.class);

    /**
     * Get the instance that the registry manages (or an instance that is unmanaged, if the provider is not within a scope).
     * The instance is guaranteed to be constructed and injected.
     *
     * @return instance
     */
    @Override
    T get();

    /**
     * Qualifiers of the instance.
     *
     * @return qualifiers of the service instance
     */
    Set<Qualifier> qualifiers();

    /**
     * Contracts of the service instance.
     *
     * @return contracts the service instance implements
     */
    Set<TypeName> contracts();

    /**
     * Scope this instance was created in. Always the same as the scope of the associated service descriptor
     * ({@link io.helidon.inject.service.ServiceDescriptor#scope()}.
     * This method may return {@link io.helidon.inject.service.Injection.Service} in case no scope is defined ("Service" scope
     * is not a real scope, as the instances cannot be managed, so each time an instance is injected, it is constructed,
     * injected, post constructed, and then forgotten by the registry).
     *
     * @return scope of this service instance
     */
    TypeName scope();

    /**
     * Weight of this instance, inherited from {@link io.helidon.inject.service.ServiceDescriptor#weight()}
     *
     * @return weight
     */
    double weight();

    /**
     * Service type responsible for creating this value, inherited from
     * {@link io.helidon.inject.service.ServiceDescriptor#serviceType()}.
     *
     * @return service type
     */
    TypeName serviceType();
}
