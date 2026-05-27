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

package io.helidon.declarative.tests.compatibility.v4;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Http;
import io.helidon.http.HttpPrologue;
import io.helidon.service.registry.Service;
import io.helidon.webserver.websocket.WebSocketServer;
import io.helidon.websocket.WebSocket;
import io.helidon.websocket.WsSession;

@SuppressWarnings("deprecation")
@WebSocketServer.Endpoint
@Http.Path("/legacy/ws/{user}/{shard}")
@Service.Singleton
public class LegacyWsEndpoint {
    private final AtomicReference<String> lastUser = new AtomicReference<>();
    private final AtomicReference<Integer> lastShard = new AtomicReference<>();
    private final AtomicReference<Close> lastClose = new AtomicReference<>();
    private final AtomicReference<Throwable> lastError = new AtomicReference<>();
    private final AtomicReference<HttpPrologue> lastHttpPrologue = new AtomicReference<>();

    @WebSocket.OnOpen
    public void onOpen(WsSession session, @Http.PathParam("user") String user, @Http.PathParam("shard") int shard) {
        lastUser.set(user);
        lastShard.set(shard);
    }

    @WebSocket.OnMessage
    public void onMessage(WsSession session, String message) {
        session.send(message, true);
    }

    @WebSocket.OnMessage
    public void onBinaryMessage(WsSession session, InputStream message) throws Exception {
        try (message) {
            BufferData result = BufferData.growing(64);
            byte[] buffer = new byte[64];
            int read;
            while ((read = message.read(buffer)) != -1) {
                result.write(buffer, 0, read);
            }
            session.send(result, true);
        }
    }

    @WebSocket.OnError
    public void onError(Throwable throwable) {
        lastError.set(throwable);
    }

    @WebSocket.OnClose
    public void onClose(String reason, int closeCode) {
        lastClose.set(new Close(reason, closeCode));
    }

    @WebSocket.OnHttpUpgrade
    public void onUpgrade(HttpPrologue prologue) {
        lastHttpPrologue.set(prologue);
    }

    public void reset() {
        lastUser.set(null);
        lastShard.set(null);
        lastClose.set(null);
        lastError.set(null);
        lastHttpPrologue.set(null);
    }

    public String lastUser() {
        return lastUser.get();
    }

    public Integer lastShard() {
        return lastShard.get();
    }

    public Close lastClose() {
        return lastClose.get();
    }

    public Throwable lastError() {
        return lastError.get();
    }

    public HttpPrologue lastHttpPrologue() {
        return lastHttpPrologue.get();
    }

    public record Close(String reason, int code) {
    }
}
