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

package io.helidon.tests.integration.quic.interop;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class MainTest {
    @Test
    void shouldUseInteropDefaults() {
        Main.InteropConfig config = Main.interopConfig(Map.of());

        assertThat(config.host(), is("0.0.0.0"));
        assertThat(config.port(), is(443));
        assertThat(config.certChain(), is(Path.of("/certs/cert.pem")));
        assertThat(config.privateKey(), is(Path.of("/certs/priv.key")));
        assertThat(config.webRoot(), is(Path.of("/www")));
        assertThat(config.welcomeFile(), is("index.html"));
    }

    @Test
    void shouldStripLeadingEcParametersBlockFromPrivateKeyPem() {
        String pem = """
                -----BEGIN EC PARAMETERS-----
                Zm9v
                -----END EC PARAMETERS-----
                -----BEGIN EC PRIVATE KEY-----
                YmFy
                -----END EC PRIVATE KEY-----
                """;

        String normalized = Main.normalizePrivateKeyPem(pem);

        assertThat(normalized.contains("EC PARAMETERS"), is(false));
        assertThat(normalized.startsWith("-----BEGIN EC PRIVATE KEY-----"), is(true));
    }

    @Test
    void shouldServeStaticContentOverHttp3(@TempDir(cleanup = CleanupMode.ALWAYS) Path workDir) throws Exception {
        Path webRoot = Files.createDirectory(workDir.resolve("www"));
        Path certChain = workDir.resolve("cert.pem");
        Path privateKey = workDir.resolve("priv.key");
        Files.writeString(webRoot.resolve("index.html"), "hello over http3", StandardCharsets.UTF_8);
        TestTlsSupport.exportServerPem(certChain, privateKey);

        Main.InteropConfig config = new Main.InteropConfig("localhost",
                                                           0,
                                                           certChain,
                                                           privateKey,
                                                           Optional.empty(),
                                                           webRoot,
                                                           "index.html");

        WebServer server = Main.startServer(config);
        try (HttpClient client = HttpClient.newBuilder()
                .version(HTTP_3)
                .connectTimeout(TestTlsSupport.TIMEOUT)
                .proxy(ProxySelector.of(null))
                .sslContext(TestTlsSupport.clientSslContext())
                .build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://localhost:" + server.port() + "/"))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .timeout(TestTlsSupport.TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat("Unexpected response body:\n" + response.body(), response.statusCode(), is(200));
            assertThat(response.version(), is(HTTP_3));
            assertThat(response.body(), is("hello over http3"));

            HttpRequest postRequest = HttpRequest.newBuilder(URI.create("https://localhost:" + server.port() + "/echo"))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .timeout(TestTlsSupport.TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString("payload"))
                    .build();

            HttpResponse<String> postResponse = client.send(postRequest, HttpResponse.BodyHandlers.ofString());

            assertThat("Unexpected response body:\n" + postResponse.body(), postResponse.statusCode(), is(200));
            assertThat(postResponse.version(), is(HTTP_3));
            assertThat(postResponse.body(), is("payload"));
        } finally {
            server.stop();
        }
    }
}
