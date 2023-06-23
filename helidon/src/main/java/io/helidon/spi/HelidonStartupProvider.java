package io.helidon.spi;

/**
 * {@link java.util.ServiceLoader} provider interface to discover the correct startup type.
 * Only the first provider (with the highest {@link io.helidon.common.Weight}) will be used.
 * The default startup will create Helidon Injection service registry, and start all services that should be started.
 */
public interface HelidonStartupProvider {
    /**
     * Start the runtime.
     *
     * @param arguments command line arguments
     */
    void start(String[] arguments);
}
