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

package io.helidon.webserver;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.net.ssl.SSLHandshakeException;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientTls;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The test of SSL Netty layer.
 */
public class CipherSuiteTest {

    private static final Logger LOGGER = Logger.getLogger(CipherSuiteTest.class.getName());
    private static final Config CONFIG = Config.just(() -> ConfigSources.classpath("cipherSuiteConfig.yaml").build());

    private static WebServer webServer;
    private static WebClient clientOne;
    private static WebClient clientTwo;

    @BeforeAll
    public static void startServer() throws Exception {
        webServer = WebServer.builder(Routing.builder().get("/", (req, res) -> res.send("It works!")))
                .config(CONFIG.get("server"))
                .addNamedRouting("second", Routing.builder().get("/", (req, res) -> res.send("It works! Second!")))
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        clientOne = WebClient.builder()
                .baseUri("https://localhost:" + webServer.port())
                .config(CONFIG.get("client"))
                .build();

        clientTwo = WebClient.builder()
                .baseUri("https://localhost:" + webServer.port())
                .tls(WebClientTls.builder()
                             .trustAll(true)
                             .allowedCipherSuite(List.of("TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"))
                             .build())
                .build();

        LOGGER.info("Started secured server at: https://localhost:" + webServer.port());
    }

    @AfterAll
    public static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testSupportedAlgorithm() {
        String response = clientOne.get()
                .request(String.class)
                .await();
        assertThat(response, is("It works!"));

        response = clientTwo.get()
                .uri("https://localhost:" + webServer.port("second"))
                .request(String.class)
                .await();
        assertThat(response, is("It works! Second!"));
    }

    @Test
    public void testUnsupportedAlgorithm() {
        CompletionException completionException = assertThrows(CompletionException.class,
                                                               () -> clientOne.get()
                                                                       .uri("https://localhost:" + webServer.port("second"))
                                                                       .request()
                                                                       .await());
        assertThat(completionException.getCause(), instanceOf(SSLHandshakeException.class));
        assertThat(completionException.getCause().getMessage(), is("Received fatal alert: handshake_failure"));

        completionException = assertThrows(CompletionException.class, () -> clientTwo.get().request().await());
        assertThat(completionException.getCause(), instanceOf(SSLHandshakeException.class));
        assertThat(completionException.getCause().getMessage(), is("Received fatal alert: handshake_failure"));
    }

}
