package io.helidon.inject;

/**
 * A scope, such as request scope.
 */
public interface Scope extends AutoCloseable {
    /**
     * Stop the scope, and destroy all service instances created within it.
     */
    @Override
    void close();

    /**
     * Services instance associated with this scope.
     *
     * @return services
     */
    ScopeServices services();
}
