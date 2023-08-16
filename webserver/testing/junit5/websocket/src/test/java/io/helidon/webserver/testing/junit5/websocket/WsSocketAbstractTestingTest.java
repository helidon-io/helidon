/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5.websocket;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.Socket;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;
import io.helidon.webclient.websocket.WsClient;
import io.helidon.webserver.websocket.WsRouting;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

abstract class WsSocketAbstractTestingTest {
    private static final ServerSideListener WS_LISTENER = new ServerSideListener();

    private final Http1Client httpClient;
    private final WsClient wsClient;

    protected WsSocketAbstractTestingTest(Http1Client httpClient, WsClient wsClient) {
        this.httpClient = httpClient;
        this.wsClient = wsClient;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder http, WsRouting.Builder ws) {
        http.get("/test", (req, res) -> res.send("http"));
        ws.endpoint("/testWs", WS_LISTENER);
    }

    @SetUpRoute("custom")
    static void customRouting(WsRouting.Builder ws) {
        ws.endpoint("/customWs", WS_LISTENER);
    }

    @Test
    void testHttpEndpoint() {
        String message = httpClient.get("/test").requestEntity(String.class);
        assertThat(message, is("http"));
    }

    @Test
    void testWsEndpoint() {
        WS_LISTENER.reset();

        ClientSideListener clientListener = new ClientSideListener();
        wsClient.connect("/testWs", clientListener);

        assertThat("We should have received a response", clientListener.await());
        assertThat(clientListener.message, is("ws"));

        assertThat(WS_LISTENER.opened, is(true));
        assertThat(WS_LISTENER.closed, is(true));
        assertThat(WS_LISTENER.message, is("hello"));
    }

    @Test
    void testWsEndpointCustomSocket(@Socket("custom") WsClient wsClient) {
        WS_LISTENER.reset();

        ClientSideListener clientListener = new ClientSideListener();
        wsClient.connect("/customWs", clientListener);

        assertThat("We should have received a response", clientListener.await());
        assertThat(clientListener.message, is("ws"));

        assertThat(WS_LISTENER.opened, is(true));
        assertThat(WS_LISTENER.closed, is(true));
        assertThat(WS_LISTENER.message, is("hello"));
    }

    private static class ClientSideListener implements WsListener {
        private final CountDownLatch cdl = new CountDownLatch(1);
        private String message;
        private volatile Throwable throwable;

        @Override
        public void onOpen(WsSession session) {
            session.send("hello", true);
        }

        @Override
        public void onMessage(WsSession session, String text, boolean last) {
            this.message = text;
            session.close(WsCloseCodes.NORMAL_CLOSE, "End");
        }

        @Override
        public void onClose(WsSession session, int status, String reason) {
            cdl.countDown();
        }

        @Override
        public void onError(WsSession session, Throwable t) {
            this.throwable = t;
            cdl.countDown();
        }

        boolean await() {
            try {
                boolean await = cdl.await(10, TimeUnit.SECONDS);
                if (throwable != null) {
                    fail(throwable);
                }
                return await;
            } catch (InterruptedException e) {
                fail(e);
                return false;
            }
        }
    }

    private static class ServerSideListener implements WsListener {
        boolean opened;
        boolean closed;
        String message;

        @Override
        public void onMessage(WsSession session, String text, boolean last) {
            message = text;
            session.send("ws", true);
        }

        @Override
        public void onClose(WsSession session, int status, String reason) {
            closed = true;
        }

        @Override
        public void onOpen(WsSession session) {
            opened = true;
        }

        void reset() {
            opened = false;
            closed = false;
            message = null;
        }
    }
}

