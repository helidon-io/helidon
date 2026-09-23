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

package io.helidon.integrations.neo4j;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import javax.net.ssl.SSLHandshakeException;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerTls;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.Driver;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.exceptions.SecurityException;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(20)
class Neo4jTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static Path certificate;
    private static WebServer server;

    @BeforeAll
    static void startServer() throws Exception {
        certificate = Path.of(Neo4jTest.class.getResource("/neo4j/certificate.pem").toURI());
        server = WebServer.builder(Routing.builder())
                .host("127.0.0.1")
                .port(0)
                // Reject the Bolt handshake immediately after TLS, so accepted certificates yield an HTTP response.
                .maxInitialLineLength(1)
                .tls(WebServerTls.builder()
                             .privateKey(KeyConfig.pemBuilder()
                                                 .key(Resource.create("neo4j/key.pkcs8.pem"))
                                                 .certChain(Resource.create("neo4j/certificate.pem"))
                                                 .build())
                             .build())
                .build();
        server.start().await(TIMEOUT);
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.shutdown().await(TIMEOUT);
        }
    }

    @Test
    void hostnameVerificationEnabledByDefault() {
        assertHostnameMismatch(tlsBuilder()
                                       .trustStrategy(Neo4j.Builder.TrustStrategy.TRUST_CUSTOM_CA_SIGNED_CERTIFICATES)
                                       .certificate(certificate));
        assertHostnameMismatch(tlsBuilder()
                                       .trustStrategy(Neo4j.Builder.TrustStrategy.TRUST_CUSTOM_CA_SIGNED_CERTIFICATES)
                                       .certificate(certificate)
                                       .config(Config.empty()));
    }

    @Test
    void hostnameVerificationCanBeDisabled() {
        assertHttpEndpointReached(tlsBuilder()
                                          .trustStrategy(Neo4j.Builder.TrustStrategy.TRUST_CUSTOM_CA_SIGNED_CERTIFICATES)
                                          .certificate(certificate)
                                          .hostnameVerificationEnabled(false));

        Config config = Config.just(ConfigSources.create(Map.of("trustsettings.hostnameVerificationEnabled", "false")));
        assertHttpEndpointReached(tlsBuilder()
                                          .trustStrategy(Neo4j.Builder.TrustStrategy.TRUST_CUSTOM_CA_SIGNED_CERTIFICATES)
                                          .certificate(certificate)
                                          .config(config));
    }

    @Test
    void publishedLegacyTrustSettingsRemainSupported() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "trust-strategy", "TRUST_CUSTOM_CA_SIGNED_CERTIFICATES",
                "certificate", certificate.toString(),
                "hostname-verification-enabled", "false")));

        assertHttpEndpointReached(tlsBuilder().config(config));
    }

    @Test
    void canonicalTrustSettingsTakePrecedence(@TempDir Path tempDir) {
        Config config = Config.just(ConfigSources.create(Map.of(
                "trust-strategy", "TRUST_SYSTEM_CA_SIGNED_CERTIFICATES",
                "certificate", tempDir.resolve("missing-legacy-ca.pem").toString(),
                "hostname-verification-enabled", "false",
                "trustsettings.trustStrategy", "TRUST_CUSTOM_CA_SIGNED_CERTIFICATES",
                "trustsettings.certificate", certificate.toString(),
                "trustsettings.hostnameVerificationEnabled", "true")));

        assertHostnameMismatch(tlsBuilder().config(config));
    }

    @Test
    void invalidCanonicalTrustSettingDoesNotFallBackToLegacyValue() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "trust-strategy", "TRUST_SYSTEM_CA_SIGNED_CERTIFICATES",
                "trustsettings.trustStrategy", "invalid")));

        assertThrows(IllegalArgumentException.class, () -> Neo4j.builder().config(config));
    }

    private static Neo4j.Builder tlsBuilder() {
        return Neo4j.builder()
                .uri("bolt://127.0.0.1:" + server.port())
                .authenticationEnabled(false)
                .encrypted(true)
                .maxConnectionPoolSize(1)
                .connectionAcquisitionTimeout(Duration.ofSeconds(5));
    }

    private static void assertHostnameMismatch(Neo4j.Builder builder) {
        try (Driver driver = builder.build().driver()) {
            SecurityException exception = assertThrows(SecurityException.class, driver::verifyConnectivity);
            assertThat(exception.getCause(), instanceOf(SSLHandshakeException.class));
            assertThat(exception.getCause().getMessage(), containsString("No subject alternative names present"));
        }
    }

    private static void assertHttpEndpointReached(Neo4j.Builder builder) {
        try (Driver driver = builder.build().driver()) {
            ClientException exception = assertThrows(ClientException.class, driver::verifyConnectivity);
            assertThat(exception.getMessage(), containsString("Server responded HTTP."));
        }
    }
}
