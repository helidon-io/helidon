/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.websocket;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.Size;
import io.helidon.webclient.websocket.WsClient;
import io.helidon.webclient.websocket.WsClientProtocolConfig;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.websocket.WsConfig;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;
import org.junit.jupiter.api.Test;

import static io.helidon.webserver.tests.websocket.WebSocketTest.randomString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@ServerTest
public class WebSocketClientTest {

    private final WsClient wsClient;

    private static final String[] text = {
            randomString(100),
            randomString(1000),
            randomString(10000),
            randomString(100000)
    };

    WebSocketClientTest(WsClient wsClient) {
        this.wsClient = wsClient;
    }

    @SetUpServer
    public static void setup(WebServerConfig.Builder builder) {
        builder.addProtocol(
                WsConfig.builder()
                        .maxFrameLength(100000)     // overrides application.yaml
                        .build());
    }

    @SetUpRoute
    static void router(Router.RouterBuilder<?> router) {
        router.addRouting(WsRouting.builder().endpoint("/echo", new EchoService()));
    }

    /**
     * Tests sending long text messages using Helidon's WS client.
     */
    @Test
    void testLongTextMessages() throws InterruptedException {
        Set<String> messages = new HashSet<>();
        CountDownLatch messageLatch = new CountDownLatch(text.length);

        wsClient.connect("/echo", new WsListener() {
            @Override
            public void onMessage(WsSession session, String text, boolean last) {
                messages.add(text);
                messageLatch.countDown();
                if (messageLatch.getCount() == 0) {
                    session.close(WsCloseCodes.NORMAL_CLOSE, "Bye!");
                }
            }

            @Override
            public void onOpen(WsSession session) {
                if (session.socketContext().remotePeer() == null) {
                    throw new InternalError("Unable to access remote peer info");
                }
                for (String s : text) {
                    session.send(s, false);
                }
            }
        });

        boolean await = messageLatch.await(10, TimeUnit.SECONDS);
        assertThat(await, is(true));
        assertThat(messages, hasItems(text));
    }

    /**
     * Tests sending a close frame with no code or reason.
     */
    @Test
    void testEmptyClose() throws InterruptedException {
        CountDownLatch messageLatch = new CountDownLatch(1);

        wsClient.connect("/echo", new WsListener() {
            @Override
            public void onOpen(WsSession session) {
                session.close(-1, "");      // empty close
                messageLatch.countDown();
            }
        });

        boolean await = messageLatch.await(10, TimeUnit.SECONDS);
        assertThat(await, is(true));
    }

    @Test
    void connectionUsesConfiguredBufferedMessageSizeWithoutSubProtocol() throws Exception {
        verifyConnectionProtocolConfig(false);
    }

    @Test
    void connectionUsesConfiguredBufferedMessageSizeWithSubProtocol() throws Exception {
        verifyConnectionProtocolConfig(true);
    }

    private void verifyConnectionProtocolConfig(boolean withSubProtocol) throws Exception {
        WsClientProtocolConfig.Builder protocolConfig = WsClientProtocolConfig.builder()
                .maxBufferedMessageSize(Size.create(17, Size.Unit.BYTE));
        if (withSubProtocol) {
            protocolConfig.addSubProtocol("chat");
        }
        WsClient client = WsClient.builder()
                .from(wsClient.prototype())
                .protocolConfig(protocolConfig.build())
                .build();
        CompletableFuture<WsSession> opened = new CompletableFuture<>();
        client.connect("/echo", new WsListener() {
            @Override
            public void onOpen(WsSession session) {
                opened.complete(session);
            }

            @Override
            public void onError(WsSession session, Throwable throwable) {
                opened.completeExceptionally(throwable);
            }
        });

        WsSession session = opened.get(10, TimeUnit.SECONDS);
        try {
            assertAll(
                    () -> assertThat(session.protocolConfig().maxBufferedMessageSize().toBytes(), is(17L)),
                    () -> assertThat(session.subProtocol().isPresent(), is(withSubProtocol))
            );
            if (withSubProtocol) {
                assertThat(session.subProtocol().orElseThrow(), is("chat"));
            }
        } finally {
            session.close(WsCloseCodes.NORMAL_CLOSE, "Bye!");
        }
    }
}
