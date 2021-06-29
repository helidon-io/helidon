package io.helidon.examples.rsocket.client;

import io.helidon.common.reactive.Single;
import io.helidon.rsocket.client.RSocketClient;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.rsocket.Payload;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

public class RSocketClientService implements Service {
    @Override
    public void update(Routing.Rules rules) {
        rules.get("/call",this::rsocketClientCall);
    }

    private void rsocketClientCall(ServerRequest req, ServerResponse response){

        RSocketClient client = RSocketClient.builder()
                .websocket("ws://localhost:8080/rsocket/board")
                .route("print")
                .build();

        Single<Payload> payload = client.requestResponse(Single.just(ByteBuffer.wrap("Hello World!".getBytes(StandardCharsets.UTF_8))));
        try {
            String result = payload.get().getDataUtf8();
            response.send(result);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
