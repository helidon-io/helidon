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

package io.helidon.webserver.tests.http3;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.http.HttpRouting;

import org.junit.jupiter.api.Test;

import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

class Http3InteroperabilityTest {
    private static final String CHUNK = "0123456789abcdef".repeat(256);
    private static final int REPETITIONS = 64;
    private static final String LARGE_RESPONSE = CHUNK.repeat(REPETITIONS);
    private static final byte[] LARGE_UPLOAD = LARGE_RESPONSE.getBytes(StandardCharsets.UTF_8);

    private static void routing(HttpRouting.Builder router) {
        router.post("/upload-length", (req, res) -> res.send(Long.toString(countBytes(req.content().inputStream()))))
                .get("/download-large", (req, res) -> {
                    try (OutputStream outputStream = res.outputStream()) {
                        byte[] chunkBytes = CHUNK.getBytes(StandardCharsets.UTF_8);
                        for (int i = 0; i < REPETITIONS; i++) {
                            outputStream.write(chunkBytes);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .get("/socket-id", (req, res) -> res.send(req.socketId()));
    }

    @Test
    void shouldAcceptLargeJdkHttp3Upload() throws Exception {
        try (Http3TestSupport.TestEnvironment environment = Http3TestSupport.sharedListener(Http3InteroperabilityTest::routing)) {
            HttpClient client = environment.http3Client();
            HttpRequest request = HttpRequest.newBuilder(environment.uri("/upload-length"))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .timeout(Http3TestSupport.TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(LARGE_UPLOAD))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode(), is(200));
            assertThat(response.version(), is(HTTP_3));
            assertThat(response.body(), is(Long.toString(LARGE_UPLOAD.length)));
        }
    }

    @Test
    void shouldServeLargeResponseToTypedHttp3Client() throws Exception {
        try (Http3TestSupport.TestEnvironment environment = Http3TestSupport.sharedListener(Http3InteroperabilityTest::routing)) {
            Http3Client client = Http3TestSupport.strictClientBuilder()
                    .baseUri(environment.baseUri())
                    .tls(environment.clientTls())
                    .build();

            try (Http3ClientResponse response = client.get("/download-large").request()) {
                assertThat(response.status().code(), is(200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(response.as(String.class), is(LARGE_RESPONSE));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldOpenNewTypedHttp3ConnectionAfterIdleTimeout() throws Exception {
        try (Http3TestSupport.TestEnvironment environment = Http3TestSupport.sharedListener(Http3InteroperabilityTest::routing)) {
            Http3ClientProtocolConfig.Builder protocolConfig = Http3ClientProtocolConfig.builder()
                    .quic(quic -> quic.idleTimeout(Duration.ofSeconds(1)));
            Http3Client client = Http3TestSupport.strictClientBuilder(protocolConfig)
                    .baseUri(environment.baseUri())
                    .tls(environment.clientTls())
                    .build();

            try {
                String firstSocketId;
                try (Http3ClientResponse first = client.get("/socket-id").request()) {
                    assertThat(first.status().code(), is(200));
                    assertThat(first.protocolId(), is(Http3Client.PROTOCOL_ID));
                    firstSocketId = first.as(String.class);
                }

                Thread.sleep(3_000);

                try (Http3ClientResponse second = client.get("/socket-id").request()) {
                    assertThat(second.status().code(), is(200));
                    assertThat(second.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(second.as(String.class), is(not(firstSocketId)));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    private static long countBytes(InputStream inputStream) {
        byte[] buffer = new byte[8_192];
        long total = 0;
        try (InputStream stream = inputStream) {
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
            }
            return total;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
