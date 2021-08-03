/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.examples.rsocket.client;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

import io.helidon.common.reactive.Single;
import io.helidon.rsocket.client.RSocketClient;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import io.rsocket.Payload;

/**
 * RSocket Client service Sample.
 */
public class RSocketClientService implements Service {
    @Override
    public void update(Routing.Rules rules) {
        rules.get("/call", this::rsocketClientCall);
    }

    private void rsocketClientCall(ServerRequest req, ServerResponse response) {

        RSocketClient client = RSocketClient.builder()
                .websocket("ws://localhost:8080/rsocket/board")
                .route("print")
                .build();

        Single<Payload> payload = client.requestResponse(
                Single.just(ByteBuffer.wrap("Hello World!".getBytes(StandardCharsets.UTF_8))));
        try {
            String result = payload.get().getDataUtf8();
            response.send(result);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
