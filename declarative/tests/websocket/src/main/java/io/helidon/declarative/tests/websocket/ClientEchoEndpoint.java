/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.websocket;

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Http;
import io.helidon.service.registry.Service;
import io.helidon.webclient.websocket.WebSocketClient;
import io.helidon.websocket.WebSocket;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsSession;

@SuppressWarnings("deprecation")
@WebSocketClient.Endpoint("${echo-endpoint.client.host:http://localhost:8080}")
@Service.Singleton
@Http.Path("/websocket/echo/{user}/{shard}")
class ClientEchoEndpoint {
    private final AtomicReference<String> lastUser = new AtomicReference<>();
    private final AtomicReference<Integer> lastShard = new AtomicReference<>();
    private final AtomicReference<EchoEndpoint.Close> lastClose = new AtomicReference<>();
    private final AtomicReference<Throwable> lastError = new AtomicReference<>();
    private final AtomicReference<BufferData> lastBytes = new AtomicReference<>();
    private final AtomicReference<String> lastText = new AtomicReference<>();

    private volatile CountDownLatch latch = new CountDownLatch(1);

    @WebSocket.OnOpen
    void onOpen(WsSession session, @Http.PathParam("user") String user, @Http.PathParam("shard") int shard) {
        lastUser.set(user);
        lastShard.set(shard);
        session.send("Hello", false);
        session.send(" World", true);
        session.send(BufferData.create("Hello".getBytes()), false);
        session.send(BufferData.create(" World".getBytes()), true);
    }

    @WebSocket.OnMessage
    void onMessage(WsSession session, String message) {
        lastText.set(message);
        if (lastBytes.get() != null) {
            session.close(WsCloseCodes.NORMAL_CLOSE, "Closed by client");
        }
    }

    @WebSocket.OnMessage
    void onBinaryMessage(WsSession session, InputStream message) throws Exception {
        try (message) {
            BufferData result = BufferData.growing(1024);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = message.read(buffer)) != -1) {
                result.write(buffer, 0, read);
            }
            lastBytes.set(result);
        }
        if (lastText.get() != null) {
            session.close(WsCloseCodes.NORMAL_CLOSE, "Closed by client");
        }
    }

    @WebSocket.OnError
    void onError(Throwable t) {
        lastError.set(t);
    }

    @WebSocket.OnClose
    void onClose(String reason, int closeCode) {
        lastClose.set(new EchoEndpoint.Close(reason, closeCode));
        latch.countDown();
    }

    void reset() {
        lastUser.set(null);
        lastClose.set(null);
        lastError.set(null);
        lastBytes.set(null);
        lastText.set(null);
        latch = new CountDownLatch(1);
    }

    String lastUser() {
        return lastUser.get();
    }

    EchoEndpoint.Close lastClose() {
        return lastClose.get();
    }

    Throwable lastError() {
        return lastError.get();
    }

    BufferData lastBytes() {
        return lastBytes.get();
    }

    String lastText() {
        return lastText.get();
    }

    CountDownLatch latch() {
        return latch;
    }

    Integer lastShard() {
        return lastShard.get();
    }
}
