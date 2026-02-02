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

import java.util.concurrent.TimeUnit;

import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.websocket.WsClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.websocket.WsCloseCodes;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

@ServerTest
public class DeclarativeWebSocketClientTest {
    private final WsClient wsClient;
    private final ClientEchoEndpointFactory clientFactory;
    private final ClientEchoEndpoint clientEndpoint;
    private final EchoEndpoint serverEndpoint;
    private final ClientEndpoint2Handler clientEndpoint2;

    public DeclarativeWebSocketClientTest(WebClient webClient,
                                          ClientEchoEndpointFactory clientFactory,
                                          ClientEchoEndpoint clientEndpoint,
                                          EchoEndpoint serverEndpoint,
                                          // verify the class is generated with the correct name from annotation
                                          ClientEndpoint2Handler clientEndpoint2) {
        this.wsClient = webClient.client(WsClient.PROTOCOL);
        this.clientFactory = clientFactory;
        this.clientEndpoint = clientEndpoint;
        this.serverEndpoint = serverEndpoint;
        this.clientEndpoint2 = clientEndpoint2;
    }

    @Test
    public void testWebSocketClient() throws Exception {
        clientEndpoint.reset();
        serverEndpoint.reset();

        clientFactory.connect(wsClient, "test-user", 4);
        clientEndpoint.latch().await(10, TimeUnit.SECONDS);

        assertThat(clientEndpoint.lastError(), nullValue());
        assertThat(clientEndpoint.lastUser(), is("test-user"));
        assertThat(clientEndpoint.lastClose(), is(new EchoEndpoint.Close("normal", WsCloseCodes.NORMAL_CLOSE)));
        assertThat(clientEndpoint.lastText(), is("Hello World"));
        assertThat(clientEndpoint.lastShard(), is(4));
        var bufferData = clientEndpoint.lastBytes();
        assertThat(bufferData, notNullValue());
        assertThat(bufferData.readString(bufferData.available()), is("Hello World"));

        assertThat(serverEndpoint.lastError(), nullValue());
        assertThat(serverEndpoint.lastUser(), is("test-user"));
        assertThat(serverEndpoint.lastClose(), is(new EchoEndpoint.Close("Closed by client", WsCloseCodes.NORMAL_CLOSE)));
        HttpPrologue prologue = serverEndpoint.lastHttpPrologue();
        assertThat(prologue.method(), is(Method.GET));
    }

    @Test
    public void testWebSocketFactory() {
        assertThat(clientEndpoint2.pathParameterNames(), hasItems("client", "shard"));
    }
}
