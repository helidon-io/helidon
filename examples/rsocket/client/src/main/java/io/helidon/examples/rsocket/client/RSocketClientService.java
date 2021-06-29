package io.helidon.examples.rsocket.client;

import io.helidon.common.reactive.Single;
import io.helidon.rsocket.client.RSocketClient;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.transport.netty.client.WebsocketClientTransport;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

public class RSocketClientService implements Service {
    @Override
    public void update(Routing.Rules rules) {
        rules.get("/call",this::rsocketClientCall);
    }

    private void rsocketClientCall(ServerRequest req, ServerResponse response){

        RSocket rSocket = io.rsocket.core.RSocketConnector.create()
                .metadataMimeType(WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA.getString())
                .connect(WebsocketClientTransport.create(URI.create("ws://localhost:8080/rsocket/board")))
                .block();

        io.rsocket.core.RSocketClient from = io.rsocket.core.RSocketClient.from(rSocket);

        io.helidon.rsocket.client.RSocketClient client = new io.helidon.rsocket.client.RSocketClient(from);
        client.route("print");
        Single<Payload> payload = client.requestResponse(Single.just(ByteBuffer.wrap("Hello World!".getBytes(StandardCharsets.UTF_8))));
        try {
            response.send(payload.get().getDataUtf8());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }
}
