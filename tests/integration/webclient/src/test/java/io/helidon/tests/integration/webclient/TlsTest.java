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

package io.helidon.tests.integration.webclient;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientException;
import io.helidon.webclient.WebClientTls;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerTls;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * WebClient TLS connection.
 */
public class TlsTest {

    private static WebServer webServer;
    private static WebClient webClient;

    @BeforeAll
    public static void setUp() {
        webServer = WebServer.builder(
                        Routing.builder()
                                .any((req, res) -> res.send("It works!")))
                .tls(WebServerTls.builder()
                             .privateKey(KeyConfig.keystoreBuilder()
                                                 .keystorePassphrase("password")
                                                 .keystore(Resource.create("server.p12"))
                                                 .build()))
                .build()
                .start()
                .await(10, TimeUnit.SECONDS);


        webClient = WebClient.builder()
                .baseUri("https://localhost:" + webServer.port())
                .tls(WebClientTls.builder()
                             .trustAll(true)
                             .build())
                .build();
    }

    @Test
    public void testConnectionOnHttpsWithHttp() {
        CompletionException exception = assertThrows(CompletionException.class,
                                                     () -> webClient.get()
                                                             .uri("http://localhost:" + webServer.port())
                                                             .request(String.class)
                                                             .await(5, TimeUnit.SECONDS));
        assertThat(exception.getCause(), instanceOf(WebClientException.class));
        assertThat(exception.getCause().getMessage(), is("Connection reset by the host"));
    }

    @Test
    public void testConnectionOnHttpsWithHttpWithoutKeepAlive() {
        CompletionException exception = assertThrows(CompletionException.class,
                                                     () -> webClient.get()
                                                             .keepAlive(false)
                                                             .uri("http://localhost:" + webServer.port())
                                                             .request(String.class)
                                                             .await(5, TimeUnit.SECONDS));
        assertThat(exception.getCause(), instanceOf(WebClientException.class));
        assertThat(exception.getCause().getMessage(), is("Connection reset by the host"));
    }


}
