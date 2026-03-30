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

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2Headers;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.http2.Http2TestClient;
import io.helidon.webserver.testing.junit5.http2.Http2TestConnection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.POST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;

@ServerTest
class ContentLengthTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(100);
    private static final String SHORTER_DATA_PATH = "/shorter-data";
    private static final String LONGER_DATA_PATH = "/longer-data";
    private static final String SECOND_STREAM_OK_PATH = "/second-stream-ok";
    private static final TestProbe SHORTER_DATA_PROBE = new TestProbe();
    private static final TestProbe SECOND_STREAM_PROBE = new TestProbe();

    static {
        LogConfig.configureRuntime();
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.route(Http2Route.route(POST, SHORTER_DATA_PATH, trackedHandler(SHORTER_DATA_PROBE)))
                .route(Http2Route.route(POST, LONGER_DATA_PATH, handler()))
                .route(Http2Route.route(POST, SECOND_STREAM_OK_PATH, trackedHandler(SECOND_STREAM_PROBE)));
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
        SHORTER_DATA_PROBE.reset();
        SECOND_STREAM_PROBE.reset();
    }

    @Test
    void shorterData(Http2TestClient client) {
        Http2TestConnection h2conn = client.createConnection();

        var headers = requestHeadersWithContentLength(5);
        h2conn.request(1, POST, SHORTER_DATA_PATH, headers, BufferData.create("fra"));

        h2conn.assertSettings(TIMEOUT);
        h2conn.assertWindowsUpdate(0, TIMEOUT);
        h2conn.assertSettings(TIMEOUT);

        Http2Headers http2Headers = h2conn.assertHeaders(1, TIMEOUT);
        assertThat(http2Headers.status(), is(Status.OK_200));
        byte[] responseBytes = h2conn.assertNextFrame(Http2FrameType.DATA, TIMEOUT).data().readBytes();
        assertThat(new String(responseBytes), is("pong"));

        assertNoHandlerExceptions(SHORTER_DATA_PROBE);
    }

    @Test
    void longerData(Http2TestClient client) {
        Http2TestConnection h2conn = client.createConnection();

        var headers = requestHeadersWithContentLength(2);
        h2conn.request(1, POST, LONGER_DATA_PATH, headers, BufferData.create("frank"));

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
        var headers = requestHeadersWithContentLength(5);
        h2conn.request(1, POST, SECOND_STREAM_OK_PATH, headers, BufferData.create("frank"));

        h2conn.assertSettings(TIMEOUT);
        h2conn.assertWindowsUpdate(0, TIMEOUT);
        h2conn.assertSettings(TIMEOUT);

        h2conn.assertNextFrame(Http2FrameType.HEADERS, TIMEOUT);
        h2conn.assertNextFrame(Http2FrameType.DATA, TIMEOUT);

        assertNoHandlerExceptions(SECOND_STREAM_PROBE);

        // Now send payload larger than advertised data length
        headers = requestHeadersWithContentLength(2);
        h2conn.request(3, POST, LONGER_DATA_PATH, headers, BufferData.create("frank"));

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

    private static Handler handler() {
        return (req, res) -> {
            try {
                req.content().consume();
            } catch (Exception ignored) {
                // Request processing may already have failed at the connection level.
            }
            try {
                res.send("pong");
            } catch (Exception ignored) {
                // Ignore response failures in the untracked route as well.
            }
        };
    }

    private static Handler trackedHandler(TestProbe testProbe) {
        return (req, res) -> {
            try {
                req.content().consume();
            } catch (Exception e) {
                testProbe.consumeException = e;
            }
            try {
                res.send("pong");
            } catch (Exception e) {
                testProbe.sendException = e;
            }
        };
    }

    private WritableHeaders<?> requestHeadersWithContentLength(long contentLength) {
        var headers = WritableHeaders.create();
        headers.add(HeaderNames.CONTENT_LENGTH, contentLength);
        return headers;
    }

    private static void assertNoHandlerExceptions(TestProbe testProbe) {
        assertNull(testProbe.consumeException);
        assertNull(testProbe.sendException);
    }

    private static final class TestProbe {
        private volatile Exception consumeException;
        private volatile Exception sendException;

        private void reset() {
            consumeException = null;
            sendException = null;
        }
    }
}
