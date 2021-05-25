package io.helidon.webserver.examples.rsocket;

import io.helidon.webserver.rsocket.RequestResponseHandler;
import io.rsocket.Payload;
import reactor.core.publisher.Mono;

public class PrintRequestResponseHandler implements RequestResponseHandler<Payload> {
    @Override
    public Mono handle(Payload payload) {
        System.out.println("Payload: " + payload.getDataUtf8());
        return Mono.empty();
    }
}
