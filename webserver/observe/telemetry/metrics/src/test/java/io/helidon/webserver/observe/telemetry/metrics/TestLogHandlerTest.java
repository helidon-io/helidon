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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.json.JsonParser;
import io.helidon.json.JsonValue;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void requiresAllSocketRoutePairsInOneSnapshot() {
        String defaultOnly = httpMetrics(dataPoint("@default", "/greet/{name}", 1));
        String privateOnly = httpMetrics(dataPoint("private", "/greet", 1));
        String complete = httpMetrics(dataPoint("@default", "/greet/{name}", 1),
                                      dataPoint("private", "/greet", 1),
                                      dataPoint("@default", "/useLazyRouteOverride", 1));
        var ready = TestOpenTelemetrySemanticConventions.hasExpectedHttpMeasurements();

        assertThat("Default measurement alone is incomplete", defaultOnly, not(ready));
        assertThat("Private measurement alone is incomplete", privateOnly, not(ready));
        assertThat("Separate partial snapshots cannot be combined",
                   List.of(defaultOnly, privateOnly),
                   not(hasItem(ready)));
        assertThat("Greet measurements without the lazy route are incomplete",
                   httpMetrics(dataPoint("@default", "/greet/{name}", 1), dataPoint("private", "/greet", 1)),
                   not(ready));
        assertThat("All expected measurements are ready", complete, ready);

        try (TestLogHandler handler = TestLogHandler.create(Logger.getAnonymousLogger())) {
            handler.publish(new LogRecord(Level.INFO, defaultOnly));
            handler.publish(new LogRecord(Level.INFO, complete));
            handler.publish(new LogRecord(Level.INFO, "{\"name\":\"otel.sdk.metric_reader.collection.duration\"}"));
            handler.publish(new LogRecord(Level.INFO, privateOnly));

            assertThat("Latest complete snapshot despite surrounding partial and SDK exports",
                       handler.lastMessageMatching(ready),
                       is(complete));
        }
    }

    @Test
    void rejectsHttpDataPointWithoutAttributes() {
        String missingAttributes = httpMetrics("{\"count\":\"1\",\"sum\":0.01}");
        String emptyAttributes = httpMetrics("{\"count\":\"1\",\"sum\":0.01,\"attributes\":[]}");
        var ready = TestOpenTelemetrySemanticConventions.hasExpectedHttpMeasurements();

        assertThat("A data point without attributes is incomplete", missingAttributes, not(ready));
        assertThat("A data point without either expected attribute pair is incomplete", emptyAttributes, not(ready));
    }

    @Test
    void readinessLeavesCountsAndExcludedMeasurementsForAssertions() {
        var ready = TestOpenTelemetrySemanticConventions.hasExpectedHttpMeasurements();
        String invalidCounts = httpMetrics(dataPoint("@default", "/greet/{name}", 2),
                                           dataPoint("private", "/greet", 2),
                                           dataPoint("@default", "/useLazyRouteOverride", 2));
        String excludedMeasurement = httpMetrics(dataPoint("@default", "/greet/{name}", 1),
                                                 dataPoint("private", "/greet", 1),
                                                 dataPoint("@default", "/useLazyRouteOverride", 1),
                                                 dataPoint("admin", "/observe/metrics", 1));

        assertThat("Invalid counts must reach the count assertions", invalidCounts, ready);
        assertThat("Excluded measurements must reach the exclusion assertions", excludedMeasurement, ready);
    }

    @Test
    void acceptsExpectedMeasurementValues() {
        assertExpectedHttpMeasurementValues(dataPoint("@default", "/greet/{name}", 1),
                                            dataPoint("private", "/greet", 1),
                                            dataPoint("@default", "/useLazyRouteOverride", 1));
    }

    @Test
    void rejectsInvalidCountForEitherSocket() {
        assertThrows(AssertionError.class,
                     () -> assertExpectedHttpMeasurementValues(dataPoint("@default", "/greet/{name}", 1),
                                                               dataPoint("private", "/greet", 2),
                                                               dataPoint("@default", "/useLazyRouteOverride", 1)));
        assertThrows(AssertionError.class,
                     () -> assertExpectedHttpMeasurementValues(dataPoint("@default", "/greet/{name}", 2),
                                                               dataPoint("private", "/greet", 1),
                                                               dataPoint("@default", "/useLazyRouteOverride", 1)));
        assertThrows(AssertionError.class,
                     () -> assertExpectedHttpMeasurementValues(dataPoint("@default", "/greet/{name}", 1),
                                                               dataPoint("private", "/greet", 1),
                                                               dataPoint("@default", "/useLazyRouteOverride", 2)));
    }

    @Test
    void rejectsNonPositiveSumForEitherSocket() {
        for (double sum : List.of(0.0D, -0.01D)) {
            assertThrows(AssertionError.class,
                         () -> assertExpectedHttpMeasurementValues(dataPoint("@default", "/greet/{name}", 1),
                                                                   dataPoint("private", "/greet", 1, sum),
                                                                   dataPoint("@default", "/useLazyRouteOverride", 1)));
            assertThrows(AssertionError.class,
                         () -> assertExpectedHttpMeasurementValues(dataPoint("@default", "/greet/{name}", 1, sum),
                                                                   dataPoint("private", "/greet", 1),
                                                                   dataPoint("@default", "/useLazyRouteOverride", 1)));
            assertThrows(AssertionError.class,
                         () -> assertExpectedHttpMeasurementValues(dataPoint("@default", "/greet/{name}", 1),
                                                                   dataPoint("private", "/greet", 1),
                                                                   dataPoint("@default", "/useLazyRouteOverride", 1, sum)));
        }
    }

    private static void assertExpectedHttpMeasurementValues(String... dataPoints) {
        var metric = TestOpenTelemetrySemanticConventions.metricsEntries(
                JsonParser.create(httpMetrics(dataPoints)).readJsonObject()).getFirst();
        var actual = metric.objectValue("histogram").orElseThrow()
                .arrayValue("dataPoints").orElseThrow().values().stream()
                .map(JsonValue::asObject)
                .toList();

        TestOpenTelemetrySemanticConventions.assertExpectedHttpMeasurementValues(actual);
    }

    private static String httpMetrics(String... dataPoints) {
        return """
                {"scopeMetrics":[{"metrics":[{"name":"http.server.request.duration",
                  "histogram":{"dataPoints":[%s]}}]}]}
                """.formatted(String.join(",", dataPoints));
    }

    private static String dataPoint(String socket, String route, int count) {
        return dataPoint(socket, route, count, 0.01D);
    }

    private static String dataPoint(String socket, String route, int count, double sum) {
        return """
                {"count":"%d","sum":%s,"attributes":[
                  {"key":"socket.name","value":{"stringValue":"%s"}},
                  {"key":"http.route","value":{"stringValue":"%s"}}]}
                """.formatted(count, sum, socket, route);
    }
}
