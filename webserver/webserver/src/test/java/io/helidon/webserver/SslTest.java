/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.webclient.WebClientTls;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * The test of SSL Netty layer.
 */
public class SslTest {

    private static final Logger LOGGER = Logger.getLogger(SslTest.class.getName());

    private static WebServer webServer;
    private static WebClient client;

    /**
     * Start the secured Web Server
     *
     * @param port the port on which to start the secured server; if less than 1,
     *             the port is dynamically selected
     * @throws Exception in case of an error
     */
    private static void startServer(int port) throws Exception {
        webServer = WebServer.builder(
                Routing.builder()
                        .any((req, res) -> res.send("It works!")))

                .port(port)
                .tls(WebServerTls.builder()
                             .privateKey(KeyConfig.pemBuilder()
                                                 .key(Resource.create("ssl/key.pkcs8.pem"))
                                                 .certChain(Resource.create("ssl/certificate.pem"))
                                                 .build()))
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        LOGGER.info("Started secured server at: https://localhost:" + webServer.port());
    }

    @Test
    public void testSecuredServerWithJerseyClient() throws Exception {

        client.get()
                .uri("https://localhost:" + webServer.port())
                .request(String.class)
                .thenAccept(it -> assertThat(it, is("It works!")))
                .toCompletableFuture()
                .get();
    }

    @Test
    public void multipleSslRequestsKeepAlive() throws Exception {
        WebClientRequestBuilder requestBuilder = client.get()
                .uri("https://localhost:" + webServer.port());

        // send an entity that won't be consumed, as such a new connection will be created by the server
        requestBuilder
                .request(String.class)
                .thenAccept(it -> assertThat(it, is("It works!")))
                .thenCompose(it -> requestBuilder.request(String.class))
                .thenAccept(it -> assertThat(it, is("It works!")))
                .toCompletableFuture()
                .get();
    }

    @Test
    public void multipleSslRequestsNonKeepAlive() throws Exception {
        WebClientRequestBuilder requestBuilder = client.post()
                .uri("https://localhost:" + webServer.port());

        // send an entity that won't be consumed, as such a new connection will be created by the server
        requestBuilder
                .submit("", String.class)
                .thenAccept(it -> assertThat(it, is("It works!")))
                .thenCompose(it -> requestBuilder.submit("", String.class))
                .thenAccept(it -> assertThat(it, is("It works!")))
                .toCompletableFuture()
                .get();
    }

    @BeforeAll
    public static void startServer() throws Exception {
        // start the server at a free port
        startServer(0);
    }

    @BeforeAll
    public static void createClientAcceptingAllCertificates() {
        client = WebClient.builder()
                .tls(WebClientTls.builder()
                             .trustAll(true)
                             .build())
                .build();
    }

    @AfterAll
    public static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }
}
