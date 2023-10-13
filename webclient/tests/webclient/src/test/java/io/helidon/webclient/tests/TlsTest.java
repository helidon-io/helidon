/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.tests;

import io.helidon.common.buffers.DataReader.IncorrectNewLineException;
import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * WebClient TLS connection.
 */
@ServerTest
public class TlsTest {

    private final Http1Client client;
    private final Http1Client secureClient;

    public TlsTest(WebServer server, Http1Client client) {
        this.client = client;
        Tls clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
        this.secureClient = Http1Client.builder()
                .baseUri("https://localhost:" + server.port())
                .tls(clientTls)
                .build();
    }

    @SetUpServer
    public static void setUp(WebServerConfig.Builder builder) {
        builder.routing(routing -> routing.any((req, res) -> res.send("It works!")))
                .tls(tls -> tls
                        .privateKey(key -> key
                                .keystore(store -> store
                                        .passphrase("password")
                                        .keystore(Resource.create("server.p12"))))
                        .privateKeyCertChain(key -> key
                                .keystore(store -> store
                                        .trustStore(true)
                                        .passphrase("password")
                                        .keystore(Resource.create("server.p12")))));
    }

    @Test
    public void testConnectionOnHttps() {
        assertThat(secureClient.get().requestEntity(String.class), is("It works!"));
    }

    @Test
    public void testConnectionOnHttpsWithHttp() {
        RuntimeException ex = assertThrows(IncorrectNewLineException.class, () ->
                client.get().request(String.class));
        assertThat(ex.getMessage(), startsWith("Found LF (6) without preceding CR."));
    }

    @Test
    public void testConnectionOnHttpsWithHttpWithoutKeepAlive() {
        RuntimeException ex = assertThrows(IncorrectNewLineException.class,
                () -> client.get()
                        .keepAlive(false)
                        .request(String.class));
        assertThat(ex.getMessage(), startsWith("Found LF (6) without preceding CR."));
    }

}
