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

package io.helidon.webserver.testing.junit5.http3;

import io.helidon.webclient.http3.Http3Client;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.TcpTransportConfig;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http3.Http3Config;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.Socket;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

@ServerTest
class Http3NamedSocketTestingTest {
    private static final String CUSTOM_SOCKET = "custom";

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder serverBuilder) {
        serverBuilder.protocolsDiscoverServices(false)
                .bindingsDiscoverServices(false)
                .addBinding(TcpTransportConfig.create());
    }

    @SetUpRoute(CUSTOM_SOCKET)
    static void customRouting(HttpRules rules, ListenerConfig.Builder listenerBuilder) {
        listenerBuilder.protocolsDiscoverServices(false)
                .port(0)
                .tls(Http3AbstractTestingTest.serverTls())
                .addProtocol(Http1Config.create())
                .addProtocol(Http3Config.builder()
                                     .name(CUSTOM_SOCKET)
                                     .buildPrototype());
        Http3AbstractTestingTest.addGreetingRoutes(rules, "custom");
    }

    @Test
    void testCustomSocket(@Socket(CUSTOM_SOCKET)
                          @Http3ClientTls(resource = "client-truststore.p12")
                          Http3Client customClient) {
        Http3AbstractTestingTest.assertGreeting(customClient, "custom");
    }

    @Test
    void testCustomSocketDynamicQpack(@Socket(CUSTOM_SOCKET)
                                      @Http3ClientTls(resource = "client-truststore.p12")
                                      Http3LowLevelClient customClient) {
        Http3AbstractTestingTest.assertDynamicQpackReuse(customClient, "custom");
    }

    @Test
    void highLevelClientsUseParameterTlsIdentityInDeclarationOrder(
            @Socket(CUSTOM_SOCKET)
            @Http3ClientTls(resource = "client-truststore.p12")
            Http3Client firstClient,
            @Socket(CUSTOM_SOCKET)
            @Http3ClientTls(resource = "server-keystore.p12")
            Http3Client secondClient) {
        assertThat(firstClient, not(sameInstance(secondClient)));
        Http3AbstractTestingTest.assertGreeting(firstClient, "custom");
        Http3AbstractTestingTest.assertGreeting(secondClient, "custom");
    }

    @Test
    void highLevelClientsUseParameterTlsIdentityInReverseDeclarationOrder(
            @Socket(CUSTOM_SOCKET)
            @Http3ClientTls(resource = "server-keystore.p12")
            Http3Client firstClient,
            @Socket(CUSTOM_SOCKET)
            @Http3ClientTls(resource = "client-truststore.p12")
            Http3Client secondClient) {
        assertThat(firstClient, not(sameInstance(secondClient)));
        Http3AbstractTestingTest.assertGreeting(firstClient, "custom");
        Http3AbstractTestingTest.assertGreeting(secondClient, "custom");
    }

    @Test
    void lowLevelClientsUseParameterTlsIdentityInDeclarationOrder(
            @Socket(CUSTOM_SOCKET)
            @Http3ClientTls(resource = "client-truststore.p12")
            Http3LowLevelClient firstClient,
            @Socket(CUSTOM_SOCKET)
            @Http3ClientTls(resource = "server-keystore.p12")
            Http3LowLevelClient secondClient) {
        assertThat(firstClient, not(sameInstance(secondClient)));
        assertThat(firstClient.get("/greet").status(), is(200));
        assertThat(secondClient.get("/greet").status(), is(200));
    }

    @Test
    void lowLevelClientsUseParameterTlsIdentityInReverseDeclarationOrder(
            @Socket(CUSTOM_SOCKET)
            @Http3ClientTls(resource = "server-keystore.p12")
            Http3LowLevelClient firstClient,
            @Socket(CUSTOM_SOCKET)
            @Http3ClientTls(resource = "client-truststore.p12")
            Http3LowLevelClient secondClient) {
        assertThat(firstClient, not(sameInstance(secondClient)));
        assertThat(firstClient.get("/greet").status(), is(200));
        assertThat(secondClient.get("/greet").status(), is(200));
    }
}
