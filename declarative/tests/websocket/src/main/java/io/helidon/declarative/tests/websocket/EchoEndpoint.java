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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
@Http.Path("/websocket/echo/{user}/{shard}")
@Service.Singleton
class EchoEndpoint {
    private final Set<WsSession> activeSessions = ConcurrentHashMap.newKeySet();
    private final AtomicInteger closeCount = new AtomicInteger();
    private final AtomicReference<String> lastUser = new AtomicReference<>();
    private final AtomicReference<Close> lastClose = new AtomicReference<>();
    private final AtomicReference<Throwable> lastError = new AtomicReference<>();
    private final AtomicReference<HttpPrologue> lastHttpPrologue = new AtomicReference<>();
    private volatile CompletableFuture<WsSession> openedSession = new CompletableFuture<>();

    void reset() {
        closeCount.set(0);
        openedSession = new CompletableFuture<>();
        lastUser.set(null);
        lastClose.set(null);
        lastError.set(null);
        lastHttpPrologue.set(null);
    }

    @WebSocket.OnOpen
    void onOpen(WsSession session, @Http.PathParam("user") String user) {
        activeSessions.add(session);
        lastUser.set(user);
        openedSession.complete(session);
    }

    @WebSocket.OnMessage
    void onMessage(WsSession session, String message) {
        session.send(message, true);
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
            session.send(result, true);
        }
    }

    @WebSocket.OnError
    void onError(Throwable t) {
        lastError.set(t);
    }

    @WebSocket.OnClose
    void onClose(WsSession session, String reason, int closeCode) {
        activeSessions.remove(session);
        closeCount.incrementAndGet();
        lastClose.set(new Close(reason, closeCode));
    }

    int activeSessions() {
        return activeSessions.size();
    }

    CompletableFuture<WsSession> openedSession() {
        return openedSession;
    }

    int closeCount() {
        return closeCount.get();
    }

    @WebSocket.OnHttpUpgrade
    void onUpgrade(HttpPrologue prologue) {
        lastHttpPrologue.set(prologue);
    }

    String lastUser() {
        return lastUser.get();
    }

    Close lastClose() {
        return lastClose.get();
    }

    Throwable lastError() {
        return lastError.get();
    }

    HttpPrologue lastHttpPrologue() {
        return lastHttpPrologue.get();
    }

    record Close(String reason, int code) {
    }
}
