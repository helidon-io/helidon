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

package io.helidon.webserver.tests.http2;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2HuffmanEncoder;
import io.helidon.http.http2.Http2Setting;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Principal;
import io.helidon.security.Security;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.security.SecurityFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.http2.Http2TestClient;
import io.helidon.webserver.testing.junit5.http2.Http2TestConnection;

import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class ConnectionHeaderTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String MISSING = "missing";
    private static final AtomicInteger SECURITY_INVOCATIONS = new AtomicInteger();

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(Http2Route.route(GET, "/client-cn", (req, res) -> {
            String commonName = req.headers().value(HeaderNames.X_HELIDON_CN).orElse(MISSING);
            res.send(commonName);
        }))
                .get("/security-once",
                     SecurityFeature.authenticate(),
                     (req, res) -> res.send("secure"));
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        Security security = Security.builder()
                .addAuthenticationProvider(providerRequest -> {
                    SECURITY_INVOCATIONS.incrementAndGet();
                    return AuthenticationResponse.success(Principal.create("jack"));
                })
                .build();

        server.addFeature(ContextFeature.create())
                .addFeature(SecurityFeature.builder()
                                    .security(security)
                                    .build())
                .addProtocol(Http2Config.builder().build());
    }

    @Test
    void clientCannotSpoofTlsCommonNameHeader(Http2TestClient client) {
        Http2TestConnection connection = client.createConnection();
        WritableHeaders<?> headers = WritableHeaders.create()
                .add(HeaderValues.create(HeaderNames.X_HELIDON_CN, "spoofed-client"));

        connection.request(1, GET, "/client-cn", headers, BufferData.create(new byte[0]));

        assertMissingHeaderResponse(connection, 1);
    }

    @Test
    void clientCannotSpoofTlsCommonNameHeaderWithContinuation(Http2TestClient client) {
        Http2TestConnection connection = client.createConnection();
        WritableHeaders<?> headers = WritableHeaders.create()
                .add(HeaderValues.create(HeaderNames.X_HELIDON_CN, "spoofed-client"));
        Http2Headers h2Headers = Http2Headers.create(headers)
                .method(GET)
                .path("/client-cn")
                .scheme(connection.clientUri().scheme())
                .authority(connection.clientUri().authority());
        BufferData headerBlock = BufferData.growing(512);
        h2Headers.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                        Http2HuffmanEncoder.create(),
                        headerBlock);
        byte[] headerBytes = headerBlock.readBytes();
        int split = headerBytes.length / 2;

        connection.writer().write(new Http2FrameData(
                Http2FrameHeader.create(split,
                                        Http2FrameTypes.HEADERS,
                                        Http2Flag.HeaderFlags.create(Http2Flag.END_OF_STREAM),
                                        1),
                BufferData.create(Arrays.copyOf(headerBytes, split))));
        connection.writer().write(new Http2FrameData(
                Http2FrameHeader.create(headerBytes.length - split,
                                        Http2FrameTypes.CONTINUATION,
                                        Http2Flag.ContinuationFlags.create(Http2Flag.END_OF_HEADERS),
                                        1),
                BufferData.create(Arrays.copyOfRange(headerBytes, split, headerBytes.length))));

        assertMissingHeaderResponse(connection, 1);
    }

    @Test
    void clientCannotSpoofTlsCommonNameHeaderWithUpgrade(WebServer server) throws IOException, InterruptedException {
        try (var client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(TIMEOUT)
                .build()) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                                                                  .timeout(TIMEOUT)
                                                                  .uri(URI.create("http://localhost:" + server.port()
                                                                                          + "/client-cn"))
                                                                  .header(HeaderNames.X_HELIDON_CN_NAME, "spoofed-client")
                                                                  .GET()
                                                                  .build(),
                                                          HttpResponse.BodyHandlers.ofString());

            assertThat(response.version(), is(HttpClient.Version.HTTP_2));
            assertThat(response.statusCode(), is(Status.OK_200.code()));
            assertThat(response.body(), is(MISSING));
        }
    }

    @Test
    void securityRunsOnceForHttp2Upgrade(WebServer server) throws IOException, InterruptedException {
        SECURITY_INVOCATIONS.set(0);

        try (var client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(TIMEOUT)
                .build()) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                                                                  .timeout(TIMEOUT)
                                                                  .uri(URI.create("http://localhost:" + server.port()
                                                                                          + "/security-once"))
                                                                  .GET()
                                                                  .build(),
                                                          HttpResponse.BodyHandlers.ofString());

            assertThat(response.version(), is(HttpClient.Version.HTTP_2));
            assertThat(response.statusCode(), is(Status.OK_200.code()));
            assertThat(response.body(), is("secure"));
            assertThat(SECURITY_INVOCATIONS.get(), is(1));
        }
    }

    private static void assertMissingHeaderResponse(Http2TestConnection connection, int streamId) {
        connection.assertSettings(TIMEOUT);
        connection.assertWindowsUpdate(0, TIMEOUT);
        connection.assertSettings(TIMEOUT);

        Http2Headers responseHeaders = connection.assertHeaders(streamId, TIMEOUT);
        assertThat(responseHeaders.status(), is(Status.OK_200));
        byte[] responseBytes = connection.assertNextFrame(Http2FrameType.DATA, TIMEOUT).data().readBytes();
        assertThat(new String(responseBytes), is(MISSING));
    }
}
