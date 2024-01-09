package io.helidon.inject;

import java.util.Optional;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;

/**
 * Extension point for the service registry.
 * To support additional scope, a service implementing this interface must be available in the registry.
 * It should be accompanied by a way to start and stop the scope (such as {@link io.helidon.inject.RequestonControl} for
 * request scope).
 */
@Injection.Contract
public interface ScopeHandler {
    /**
     * Type name of this interface.
     * Service registry uses {@link io.helidon.common.types.TypeName} in its APIs.
     */
    TypeName TYPE_NAME = TypeName.create(ScopeHandler.class);

    /**
     * Scope this handle is capable of handling.
     *
     * @return scope type
     */
    TypeName supportedScope();

    /**
     * Get the current scope if available.
     *
     * @return current scope instance, or empty if the scope is not active
     */
    Optional<Scope> currentScope();
}
