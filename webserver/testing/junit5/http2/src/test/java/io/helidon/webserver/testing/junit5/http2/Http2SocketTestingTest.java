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

package io.helidon.webserver.testing.junit5.http2;

import java.time.Duration;

import io.helidon.http.Method;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.WindowSize;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.Socket;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class Http2SocketTestingTest extends Http2AbstractTestingTest {
    private static final String DEFAULT_WINDOW_SOCKET = "default-window";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    Http2SocketTestingTest(Http2Client httpClient) {
        super(httpClient);
    }

    @SetUpRoute(DEFAULT_WINDOW_SOCKET)
    static void defaultWindowSocket(ListenerConfig.Builder listener, HttpRules rules) {
        listener.addProtocol(Http2Config.builder()
                                     .initialWindowSize(WindowSize.DEFAULT_WIN_SIZE)
                                     .build());
        rules.route(Http2Route.route(Method.GET, "/greet", (req, res) -> res.send("default-window")));
    }

    @Test
    void nextFrameTimeoutReportsExpectedFrame(Http2TestClient client) {
        try (Http2TestConnection h2conn = client.createConnection()) {
            h2conn.completeHandshake(TIMEOUT);

            AssertionError error = assertThrows(AssertionError.class,
                                               () -> h2conn.assertNextFrame(Http2FrameType.GO_AWAY,
                                                                           Duration.ofMillis(25)));
            assertThat(error.getMessage(), containsString("Timed out waiting for HTTP/2 frame GO_AWAY"));
        }
    }

    @Test
    void handshakeAllowsDefaultWindowSize(@Socket(DEFAULT_WINDOW_SOCKET) Http2TestClient client) {
        try (Http2TestConnection h2conn = client.createConnection()) {
            h2conn.completeHandshake(TIMEOUT);
        }
    }
}
