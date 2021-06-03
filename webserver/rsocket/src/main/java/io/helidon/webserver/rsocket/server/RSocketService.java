package io.helidon.webserver.rsocket.server;

@FunctionalInterface
public interface RSocketService {

    void update(RSocketRouting.Rules rules);
}
