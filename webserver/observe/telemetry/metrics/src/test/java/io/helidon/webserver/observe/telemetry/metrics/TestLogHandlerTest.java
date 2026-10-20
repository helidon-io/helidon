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

package io.helidon.webserver.observe.telemetry.metrics;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TestLogHandlerTest {

    @Test
    void selectsLatestHttpMetricsAmongSdkExports() {
        String sdkMetrics = """
                {"name":"otel.sdk.metric_reader.collection.duration"}
                """;
        String firstHttpMetrics = """
                {"name":"http.server.request.duration","count":"1"}
                """;
        String latestHttpMetrics = """
                {"name":"http.server.request.duration","count":"2"}
                """;

        try (TestLogHandler handler = TestLogHandler.create(Logger.getAnonymousLogger())) {
            handler.publish(new LogRecord(Level.INFO, sdkMetrics));
            handler.publish(new LogRecord(Level.INFO, firstHttpMetrics));
            handler.publish(new LogRecord(Level.INFO, latestHttpMetrics));
            handler.publish(new LogRecord(Level.INFO, sdkMetrics));

            assertThat("Latest HTTP metrics despite surrounding SDK-only exports",
                       handler.lastMessageContaining(OpenTelemetryMetricsHttpSemanticConventions.TIMER_NAME),
                       is(latestHttpMetrics));
        }
    }
}
