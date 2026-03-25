/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2Headers;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.http2.Http2TestClient;
import io.helidon.webserver.testing.junit5.http2.Http2TestConnection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.POST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ServerTest
class ContentLengthTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(100);
    private static final HeaderName PROBE_ID = HeaderNames.create("test-probe-id");
    // The server is shared across the whole class, so route-side state must be keyed per request.
    private static final Map<String, TestProbe> TEST_PROBES = new ConcurrentHashMap<>();

    private String probeId;
    private TestProbe testProbe;

    static {
        LogConfig.configureRuntime();
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.route(Http2Route.route(POST, "/", (req, res) -> {
            TestProbe testProbe = req.headers()
                    .first(PROBE_ID)
                    .map(TEST_PROBES::get)
                    .orElse(null);
            try {
                req.content().consume();
            } catch (Exception e) {
                if (testProbe != null) {
                    testProbe.consumeExceptionFuture().complete(e);
                }
            }
            try {
                res.send("pong");
            } catch (Exception e) {
                if (testProbe != null) {
                    testProbe.sendExceptionFuture().complete(e);
                }
            }
        }));
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        server.addProtocol(Http2Config.builder()
                                   .sendErrorDetails(true)
                                   .maxConcurrentStreams(5)
                                   .build());
    }

    @BeforeEach
    void beforeEach() {
        probeId = UUID.randomUUID().toString();
        testProbe = new TestProbe();
        TEST_PROBES.put(probeId, testProbe);
    }

    @AfterEach
    void afterEach() {
        TEST_PROBES.remove(probeId);
    }

    @Test
    void shorterData(Http2TestClient client) {
        Http2TestConnection h2conn = client.createConnection();

        var headers = headers(5);
        h2conn.request(1, POST, "/", headers, BufferData.create("fra"));

        h2conn.assertSettings(TIMEOUT);
        h2conn.assertWindowsUpdate(0, TIMEOUT);
        h2conn.assertSettings(TIMEOUT);

        Http2Headers http2Headers = h2conn.assertHeaders(1, TIMEOUT);
        assertThat(http2Headers.status(), is(Status.OK_200));
        byte[] responseBytes = h2conn.assertNextFrame(Http2FrameType.DATA, TIMEOUT).data().readBytes();
        assertThat(new String(responseBytes), is("pong"));

        assertNoHandlerExceptions();
    }

    @Test
    void longerData(Http2TestClient client) {
        Http2TestConnection h2conn = client.createConnection();

        assertNoHandlerExceptions();

        var headers = headers(2);
        h2conn.request(1, POST, "/", headers, BufferData.create("frank"));

        h2conn.assertSettings(TIMEOUT);
        h2conn.assertWindowsUpdate(0, TIMEOUT);
        h2conn.assertSettings(TIMEOUT);

        h2conn.assertRstStream(1, TIMEOUT);
        h2conn.assertGoAway(Http2ErrorCode.ENHANCE_YOUR_CALM,
                            "Request data length doesn't correspond to the content-length header.",
                            TIMEOUT);

        /*
        Now this fails already in connection, we never reach routing.
         */
    }

    @Test
    void longerDataSecondStream(Http2TestClient client) {
        Http2TestConnection h2conn = client.createConnection();

        // First send payload with proper data length
        var headers = headers(5);
        h2conn.request(1, POST, "/", headers, BufferData.create("frank"));

        h2conn.assertSettings(TIMEOUT);
        h2conn.assertWindowsUpdate(0, TIMEOUT);
        h2conn.assertSettings(TIMEOUT);

        h2conn.assertNextFrame(Http2FrameType.HEADERS, TIMEOUT);
        h2conn.assertNextFrame(Http2FrameType.DATA, TIMEOUT);

        assertNoHandlerExceptions();

        // Now send payload larger than advertised data length
        headers = headers(2);
        h2conn.request(3, POST, "/", headers, BufferData.create("frank"));

        h2conn.assertRstStream(3, TIMEOUT);
        h2conn.assertGoAway(Http2ErrorCode.ENHANCE_YOUR_CALM,
                            "Request data length doesn't correspond to the content-length header.",
                            TIMEOUT);

        /*
        As in the previous test, this may not reach routing at all (depends on environment, buffer sizes etc.).
        The original block of code should have been removed when changes for unix domain sockets were done, as in
        longerData() above.
         */
    }

    private WritableHeaders<?> headers(long contentLength) {
        var headers = WritableHeaders.create();
        headers.add(HeaderNames.CONTENT_LENGTH, contentLength);
        headers.add(PROBE_ID, probeId);
        return headers;
    }

    private void assertNoHandlerExceptions() {
        assertFalse(testProbe.consumeExceptionFuture().isDone());
        assertFalse(testProbe.sendExceptionFuture().isDone());
    }

    private record TestProbe(CompletableFuture<Exception> consumeExceptionFuture,
                             CompletableFuture<Exception> sendExceptionFuture) {
        private TestProbe() {
            this(new CompletableFuture<>(), new CompletableFuture<>());
        }
    }
}
