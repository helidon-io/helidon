package io.helidon.webserver.examples.rsocket;

import io.helidon.common.reactive.Single;
import io.helidon.webserver.rsocket.RequestResponseHandler;
import io.rsocket.Payload;

public class PrintRequestResponseHandler implements RequestResponseHandler<Payload> {
    @Override
    public Single<Payload> handle(Payload payload) {
        System.out.println("Payload: " + payload.getDataUtf8());
        return Single.empty();
    }
}
