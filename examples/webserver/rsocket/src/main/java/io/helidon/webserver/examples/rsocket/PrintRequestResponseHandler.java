package io.helidon.webserver.examples.rsocket;

import io.helidon.webserver.rsocket.RequestResponseHandler;
import reactor.core.publisher.Mono;

public class PrintRequestResponseHandler implements RequestResponseHandler {
    @Override
    public Mono handle(Object payload) {
        System.out.println("Payload: " + payload);
        return Mono.empty();
    }
}
