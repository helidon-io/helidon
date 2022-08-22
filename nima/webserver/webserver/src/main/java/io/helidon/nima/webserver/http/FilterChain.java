package io.helidon.nima.webserver.http;

/**
 * Filter chain contains all subsequent filters that are configured, as well as the final route.
 */
public interface FilterChain {
    /**
     * Proceed with the next filters, or route.
     */
    void proceed();
}
