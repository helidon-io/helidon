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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.junit5.MatcherWithRetry;
import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.Method;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.Socket;

import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingMetricExporter;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@ServerTest
class TestOpenTelemetrySemanticConventions {

    private static boolean checkPort;

    private final Http1Client defaultClient;
    private final Http1Client adminClient;
    private final Http1Client privateClient;

    private final WebServer server;

    TestOpenTelemetrySemanticConventions(WebServer server,
                                         Http1Client defaultClient,
                                         @Socket("admin") Http1Client adminClient,
                                         @Socket("private") Http1Client privateClient) {
        this.defaultClient = defaultClient;
        this.adminClient = adminClient;
        this.privateClient = privateClient;
        this.server = server;
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder serverBuilder) {
        var configText = """
                server:
                  sockets:
                    - name: admin
                      host: localhost
                    - name: private
                      host: localhost
                  features:
                    observe:
                      sockets: admin
                      observers:
                        metrics:
                          auto:
                            paths:
                              - path: /greet
                                methods: ["OPTIONS"]
                                enabled: false
                            sockets: ["@default","private"]
                            opt-in: ["http.server.request.duration:server.address"]
                """;
        checkPort = false; // because we did not opt in for the server.port.
        Config config = Config.just(ConfigSources.create(configText, MediaTypes.APPLICATION_YAML));
        serverBuilder
                .config(config.get("server"))
                .routing(r -> r.get("/greet/{name}",
                                    (req, resp) ->
                                            resp.send("Hello, " + req.path().segments().get(1).value() + "!")))
                .routing("private", r -> r.any("/greet",
                                               (req, resp) -> {
                                                   switch (req.prologue().method().text()) {
                                                   case Method.GET_NAME -> resp.send("Hello, World!");
                                                   case Method.OPTIONS_NAME -> resp.send("Options, World!");
                                                   default -> resp.next();
                                                   }
                                               }));

    }

    @Test
    void checkAutoMetrics() {

        // Use default socket.
        try (Http1ClientResponse defaultResponse = defaultClient.get("/greet/Joe")
                .accept(MediaTypes.TEXT_PLAIN)
                .request();
                Http1ClientResponse privateResponse = privateClient.get("/greet")
                        .accept(MediaTypes.TEXT_PLAIN)
                        .request();
                Http1ClientResponse adminResponse = adminClient.get("/observe/metrics")
                        .accept(MediaTypes.APPLICATION_JSON)
                        .request();
                Http1ClientResponse metricsOnDefaultResponse = defaultClient.get("/observe/metrics")
                        .accept(MediaTypes.APPLICATION_JSON)
                        .request();
                Http1ClientResponse greetOptionsResponse = privateClient.options("/greet")
                        .accept(MediaTypes.TEXT_PLAIN)
                        .request();
                TestLogHandler testLogHandler = TestLogHandler.create(
                        Logger.getLogger(OtlpJsonLoggingMetricExporter.class.getName()))) {

            assertThat("Greet endpoint", defaultResponse.status().code(), is(200));
            assertThat("Private endpoint", privateResponse.status().code(), is(200));
            assertThat("Admin endpoint", adminResponse.status().code(), is(200));
            assertThat("Metrics endpoint via default socket", metricsOnDefaultResponse.status().code(), is(404));
            assertThat("Private endpoint HEAD", greetOptionsResponse.status().code(), is(200));


            /*
            There should be two timers, one for each of the greet endpoints and none for the
            metrics endpoint. Find each and make sure the socket name tag has the correct value
            and the timer's count is 1.
             */

            var dataPoints = MatcherWithRetry.assertThatWithRetry("Metrics entries",
                                                                  () -> dataPoints(testLogHandler.messages()),
                                                                  hasSize(2));

            List<String> socketNamesInTimers = new ArrayList<>();

            for (MetricsEnvelope.HistogramDataPoint dataPoint : dataPoints) {
                assertThat("Histogram data count", dataPoint.count(), is(1));
                assertThat("Histogram data sum", dataPoint.sum(), greaterThan(0D));
                var httpStatus = dataPoint.attributes().get(OpenTelemetryMetricsHttpSemanticConventions.STATUS_CODE);
                assertThat("Histogram data attributes",
                           httpStatus.asLong(),
                           OptionalMatcher.optionalValue(is(200L)));

                //
                var socketNameValue = dataPoint.attributes().get(OpenTelemetryMetricsHttpSemanticConventions.SOCKET_NAME);
                assertThat("Histogram data socket name", socketNameValue.asString(), OptionalMatcher.optionalPresent());
                String socketName = socketNameValue.asString().get();

                var expectedHttpRoute = switch (socketName) {
                    case "private" -> "/greet";
                    case "@default" -> "/greet/{name}";
                    default -> "unexpected";
                };

                var expectedPort = switch (socketName) {
                    case "private" -> server.port("private");
                    case "@default" -> server.port();
                    default -> -1;
                };

                socketNamesInTimers.add(socketName);

                /*
                Even after the timer appears in the registry, it's possible we would check its count before the
                filter had
                updated the timer. So retry when we check the count as well.

                This test is written so each timer should be updated once, hence the hard-coded 1 below.
                 */
                MatcherWithRetry.assertThatWithRetry("Socket " + socketName + " timer update count",
                                                     dataPoint::count,
                                                     is(1));

                assertThat("Socket " + socketName + " timer total time", dataPoint.sum(),
                           greaterThan(0D));

                var portMatcher = hasKey(OpenTelemetryMetricsHttpSemanticConventions.SERVER_PORT);
                if (!checkPort) {
                    portMatcher = not(portMatcher);
                }

                assertThat("Port", dataPoint.attributes(), portMatcher);

                checkAttribute(dataPoint.attributes(), OpenTelemetryMetricsHttpSemanticConventions.HTTP_METHOD, "GET");
                checkAttribute(dataPoint.attributes(), OpenTelemetryMetricsHttpSemanticConventions.URL_SCHEME, "HTTP");
                checkAttribute(dataPoint.attributes(), OpenTelemetryMetricsHttpSemanticConventions.STATUS_CODE, 200);

                assertThat("Attribute error type ",
                           dataPoint.attributes().get(OpenTelemetryMetricsHttpSemanticConventions.ERROR_TYPE).asString(),
                           OptionalMatcher.optionalEmpty());

                checkAttribute(dataPoint.attributes(), OpenTelemetryMetricsHttpSemanticConventions.HTTP_ROUTE, expectedHttpRoute);
                checkAttribute(dataPoint.attributes(), OpenTelemetryMetricsHttpSemanticConventions.SERVER_ADDRESS, "localhost");
                checkAttribute(dataPoint.attributes(), OpenTelemetryMetricsHttpSemanticConventions.SOCKET_NAME, socketName);
            }

            assertThat("Expected sockets", socketNamesInTimers, allOf(
                    hasItem("@default"),
                    hasItem("private"),
                    not(hasItem("admin"))
            ));

            Set<String> unexpectedlyUntimedSockets = new HashSet<>(Set.of("@default", "private"));
            socketNamesInTimers.forEach(unexpectedlyUntimedSockets::remove);
            assertThat("Unexpectedly untimed sockets", unexpectedlyUntimedSockets, hasSize(0));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private void checkAttribute(Map<String, MetricsEnvelope.AnyValue> attributes, String attribute, String expectedValue) {
        assertThat("Attribute " + attribute,
                   attributes.get(attribute).asString(),
                   OptionalMatcher.optionalValue(is(expectedValue)));
    }

    private void checkAttribute(Map<String, MetricsEnvelope.AnyValue> attributes, String attribute, long expectedValue) {
        assertThat("Attribute " + attribute,
                   attributes.get(attribute).asLong(),
                   OptionalMatcher.optionalValue(is(expectedValue)));
    }

    private List<MetricsEnvelope.HistogramDataPoint> dataPoints(List<String> logMessages) {
        var parser = OtlpJsonpMetricsParser.create();

        var results = logMessages.stream()
                .map(parser::parse)
                .flatMap(metricsDocument -> metricsDocument.resourceMetrics().stream())
                .flatMap(rm -> rm.scopeMetrics().stream())
                .flatMap(sm -> sm.metrics().stream())
                .map(MetricsEnvelope.Metric::histogram)
                .flatMap(h -> h.dataPoints().stream())
                .toList();
        return results;
    }
}
