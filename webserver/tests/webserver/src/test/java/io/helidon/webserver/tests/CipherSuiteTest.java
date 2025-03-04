/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests;

import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.Socket;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class CipherSuiteTest {

    private static final Config CONFIG = Config.just(() -> ConfigSources.classpath("cipherSuiteConfig.yaml").build());

    private final WebClient defaultClient;
    private final WebClient invalidClient;

    CipherSuiteTest(@Socket("@default") URI defaultUri, @Socket("invalid") URI invalidUri) {
        // the "valid" algorithm was updated for Java 24, as the previous is no longer valid
        // the current setup works both in Java 21 and in Java 24
        this.defaultClient = WebClient.builder()
                .baseUri(defaultUri)
                .tls(tls -> tls.enabledCipherSuites(List.of("TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"))
                        .trustAll(true)
                        .endpointIdentificationAlgorithm("NONE"))
                .build();
        this.invalidClient = WebClient.builder()
                .baseUri(invalidUri)
                .tls(tls -> tls.enabledCipherSuites(List.of("TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"))
                        .trustAll(true)
                        .endpointIdentificationAlgorithm("NONE"))
                .build();
    }

    private static final String DEFAULT_ENDPOINT = "Allowed cipher: \"TLS_DHE_RSA_WITH_AES_256_GCM_SHA384\"";

    @SetUpServer
    static void setupServer(WebServerConfig.Builder server) {
        server.config(CONFIG.get("server"));
    }

    @SetUpRoute
    static void routeSetupDefault(HttpRouting.Builder builder) {
        builder.get("/", (req, res) -> res.send(DEFAULT_ENDPOINT));
    }

    @SetUpRoute("invalid")
    static void routeSetupInvalid(HttpRouting.Builder builder) {
        builder.get("/", (req, res) -> res.send("This endpoint should not be reached!"));
    }

    @Test
    void testDefaultCipherSuite() {
        String entity = defaultClient.get().requestEntity(String.class);
        assertThat(entity, is(DEFAULT_ENDPOINT));
    }

    @Test
    void testInvalidCipherSuite() {
        UncheckedIOException exception = assertThrows(UncheckedIOException.class, () -> invalidClient.get().request());
        assertThat(exception.getCause(), instanceOf(SSLHandshakeException.class));
        assertThat(exception.getCause().getMessage(), containsString("Received fatal alert: handshake_failure"));
    }

}
