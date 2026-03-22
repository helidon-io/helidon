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

package io.helidon.webserver.tests.sse;

import io.helidon.http.sse.SseEvent;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.sse.SseSink;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

/**
 * Test that verifies the fix for issue #11298.
 * <p>
 * When {@code Http1ServerResponse.sink()} creates a sink, it should immediately mark
 * the response as sent ({@code isSent = true}). Previously, {@code isSent} was only
 * set in the sink's closeRunnable, causing the routing layer to incorrectly throw:
 * </p>
 * <pre>
 * IllegalStateException: A route MUST call either send, reroute, or next on ServerResponse
 * </pre>
 * <p>
 * even though the response was already sent via the sink.
 * </p>
 */
@ServerTest
class Http1ServerResponseSinkIsSentTest extends SseBaseTest {

    Http1ServerResponseSinkIsSentTest(io.helidon.webserver.WebServer webServer) {
        super(webServer);
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder rules) {
        rules.get("/sse-no-send", Http1ServerResponseSinkIsSentTest::sseWithoutSend);
    }

    /**
     * SSE handler that emits events via sink without calling {@code res.send()}.
     * If {@code isSent} is not set immediately by {@code sink()}, the routing layer
     * will throw {@code IllegalStateException} after this handler returns.
     */
    static void sseWithoutSend(ServerRequest req, ServerResponse res) {
        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            sseSink.emit(SseEvent.create("message1"))
                    .emit(SseEvent.create("message2"));
        }
    }

    /**
     * Test that SSE works without explicitly calling {@code res.send()}.
     * <p>
     * Verifies the fix for issue #11298. Before the fix, this test would fail with:
     * </p>
     * <pre>
     * IllegalStateException: A route MUST call either send, reroute, or next on ServerResponse
     * </pre>
     */
    @Test
    void testSseWithoutSend() throws Exception {
        testSse("/sse-no-send", "data:message1", "data:message2");
    }
}
