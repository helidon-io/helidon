/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
package io.helidon.webserver.websocket.test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import io.helidon.common.http.Http;

import jakarta.websocket.DeploymentException;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.glassfish.tyrus.client.auth.AuthenticationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

class HandshakeFailureTest extends TyrusSupportBaseTest {

    @BeforeAll
    static void startServer() throws Exception {
        webServer(true, EchoEndpoint.class);
    }

    /**
     * Should fail because user is not Helidon. See server handshake at
     * {@link EchoEndpoint.ServerConfigurator#modifyHandshake(ServerEndpointConfig, HandshakeRequest, HandshakeResponse)}.
     */
    @Test
    void testEchoSingleUpgradeFail() {
        URI uri = URI.create("ws://localhost:" + webServer().port() + "/tyrus/echo?user=Unknown");
        EchoClient echoClient = new EchoClient(uri);
        try {
            echoClient.echo("One");
        } catch (Exception e) {
            assertThat(e, instanceOf(DeploymentException.class));
            assertThat(e.getCause(), instanceOf(AuthenticationException.class));
            AuthenticationException ae = (AuthenticationException) e.getCause();
            assertThat(ae.getHttpStatusCode(), is(401));
            assertThat(ae.getMessage(), is("Authentication failed."));
            return;
        }
        fail("Exception not thrown");
    }

    /**
     * Should fail because user is not Helidon. See server handshake at
     * {@link EchoEndpoint.ServerConfigurator#modifyHandshake(ServerEndpointConfig, HandshakeRequest, HandshakeResponse)}.
     */
    @Test
    void testEchoSingleUpgradeFailRaw() throws Exception {
        String response = SocketHttpClient.sendAndReceive("/tyrus/echo?user=Unknown",
                Http.Method.GET,
                List.of("Connection:Upgrade",
                        "Upgrade:websocket",
                        "Sec-WebSocket-Key:0SBbaRkS/idPrmvImDNHBA==",
                        "Sec-WebSocket-Version:13"),
                webServer());

        assertThat(SocketHttpClient.statusFromResponse(response),
                is(Http.Status.UNAUTHORIZED_401));
        assertThat(SocketHttpClient.entityFromResponse(response, false),
                is("Failed to authenticate\n"));
        Map<String, String> headers = SocketHttpClient.headersFromResponse(response);
        assertThat(headers.get("Endpoint"), is("EchoEndpoint"));
        assertFalse(headers.containsKey("Connection") || headers.containsKey("connection"));
        assertFalse(headers.containsKey("Upgrade") || headers.containsKey("upgrade"));
    }
}
