package io.helidon.webserver.rsocket.server;

/**
 * Interface for RSocket Service.
 */
@FunctionalInterface
public interface RSocketService {

    /**
     * Override to update routing.
     *
     * @param rules
     */
    void update(RSocketRouting.Rules rules);
}
