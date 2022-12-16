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

package io.helidon.webserver;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import javax.net.ssl.SSLHandshakeException;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientTls;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The test of SSL Netty layer.
 */
class CipherSuiteTest {

    private static final Logger LOGGER = Logger.getLogger(CipherSuiteTest.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Config CONFIG = Config.create(ConfigSources.classpath("cipherSuiteConfig.yaml").build());

    private static WebServer webServer;
    private static WebClient clientOne;
    private static WebClient clientTwo;

    @BeforeAll
    static void startServer() throws Exception {
        webServer = WebServer.builder()
                .config(CONFIG.get("server"))
                .routing(Routing.builder().get("/", (req, res) -> res.send("It works!")))
                .addNamedRouting("second", Routing.builder()
                                                  .get("/", (req, res) -> res.send("It works! Second!")))
                .build()
                .start()
                .await(TIMEOUT);

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
    static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .await(TIMEOUT);
        }
    }

    @Test
    void testSupportedAlgorithm() {
        String response = clientOne.get()
                .request(String.class)
                .await(TIMEOUT);
        assertThat(response, is("It works!"));

        response = clientTwo.get()
                .uri("https://localhost:" + webServer.port("second"))
                .request(String.class)
                .await(TIMEOUT);
        assertThat(response, is("It works! Second!"));
    }

    @Test
    void testUnsupportedAlgorithm() {
        Throwable cause = assertThrows(CompletionException.class,
                                       () -> clientOne.get()
                                               .uri("https://localhost:" + webServer.port("second"))
                                               .request()
                                               .await(TIMEOUT))
                .getCause();
        checkCause(cause);

        cause = assertThrows(CompletionException.class, () -> clientTwo.get().request().await(TIMEOUT)).getCause();
        checkCause(cause);
    }

    private void checkCause(Throwable cause) {
        // Fix, until we understand the cause of intermittent failure
        // sometimes the connection is closed before we receive the SSL Handshake failure
        if (cause instanceof IllegalStateException ise) {
            // this is the message we get when connection is closed
            assertThat(ise.getMessage(), containsString("Connection reset by the host"));
        } else {
            assertThat(cause, instanceOf(SSLHandshakeException.class));
            assertThat(cause.getMessage(), is("Received fatal alert: handshake_failure"));
        }
    }
}
