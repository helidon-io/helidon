package io.helidon.inject;

/**
 * A scope.
 */
public interface Scope {
    /**
     * Stop the scope, and destroy all service instances created within it.
     */
    void close();

    /**
     * Services instance associated with this scope.
     *
     * @return services
     */
    Services services();
}
