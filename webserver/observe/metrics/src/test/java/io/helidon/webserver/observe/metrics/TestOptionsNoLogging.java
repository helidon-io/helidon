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

package io.helidon.webserver.observe.metrics;

import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.junit5.InMemoryLoggingHandler;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

@RoutingTest
class TestOptionsNoLogging {

    private final WebClient client;

    TestOptionsNoLogging(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void setUpRoute(HttpRouting.Builder routing) {
    }

    @Test
    void testOptionsDoesNotLog() {
        try (InMemoryLoggingHandler loggingHandler = InMemoryLoggingHandler.create(Logger.getLogger(""));
             HttpClientResponse response = client.method(Method.OPTIONS)
                     .path("/observe/metrics")
                     .accept(MediaTypes.create("x/x"))
                     .request()) {
            assertThat("Response", response.status().code(), is(Status.METHOD_NOT_ALLOWED_405.code()));
            // Need to wait for the server to try to add more to the already-sent response in order to trigger the warning message.
            pause(250);

            // Without the bug fix, the server logs "cannot send error response, as response already sent"
            // Map the log records to a list of the messages so the assert failure message is clearer.
            assertThat("Saved log records", loggingHandler.logRecords().stream().map(LogRecord::getMessage).toList(), empty());
        }
    }

    static void pause(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}