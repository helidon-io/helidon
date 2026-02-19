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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Stream;

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
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Makes sure the emitted data (as JSON written to a logger) is as expected.
 * <p>
 * Note that values we might expect to be JSON integers are expressed as strings. See
 * <a href="https://github.com/open-telemetry/opentelemetry-collector/issues/10457">this explanation</a>.
 */
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
                          auto-http-metrics:
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

    static Matcher<JsonValue> hasString(String name, Matcher<String> matcher) {
        return new FeatureMatcher<>(matcher, "has property " + name, name) {
            @Override
            protected String featureValueOf(JsonValue target) {
                return target.asJsonObject().getString(name);
            }
        };
    }

    static Matcher<JsonValue> hasStringAttribute(String key, Matcher<String> matcher) {
        return new FeatureMatcher<>(matcher, "has string attribute " + key, key) {
            @Override
            protected String featureValueOf(JsonValue actual) {
                return actual.asJsonObject().getJsonArray("attributes").stream()
                        .map(JsonValue::asJsonObject)
                        .filter(attribute -> attribute.getString("key").equals(key))
                        .map(attribute -> attribute.getJsonObject("value").getString("stringValue"))
                        .findFirst()
                        .orElse(null);
            }
        };
    }

    static Matcher<JsonValue> hasInteger(String name, Matcher<Integer> matcher) {
        return new FeatureMatcher<>(matcher, "has integer " + name, name) {
            @Override
            protected Integer featureValueOf(JsonValue target) {
                return target.asJsonObject().getInt(name);
            }
        };
    }

    static Matcher<JsonValue> hasDouble(String name, Matcher<Double> matcher) {
        return new FeatureMatcher<>(matcher, "has double " + name, name) {
            @Override
            protected Double featureValueOf(JsonValue target) {
                return target.asJsonObject().getJsonNumber(name).doubleValue();
            }
        };
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

            List<String> socketNamesInTimers = new ArrayList<>();

            /*
            There should be two timers, one for each of the greet endpoints and none for the
            metrics endpoint. Find each and make sure the socket name tag has the correct value
            and the timer's count is 1.
             */

            List<String> jsonText = testLogHandler.messages(2);

            var jsonReader = Json.createReader(new StringReader(jsonText.getFirst()));
            var root = jsonReader.readObject();

            var top = root.getJsonArray("resourceMetrics");

            Stream<JsonValue> scopeMetricsEntries;

            /*
            The top node might be resourceMetrics which is an array of resource/scopeMetrics tuples, or it might be a single
            resource/scopeMetrics tuple.
             */
            if (top != null) {
                scopeMetricsEntries =
                        top.asJsonArray().stream()
                                .flatMap(resourceMetricEntry -> resourceMetricEntry.asJsonArray().stream())
                                .map(JsonValue::asJsonObject)
                                .flatMap(resourceMetric -> resourceMetric.getJsonArray("scopeMetrics").stream());
            } else {
                scopeMetricsEntries = root.getJsonArray("scopeMetrics").stream();
            }

            List<JsonObject> metricsEntries = scopeMetricsEntries
                    .flatMap(scopeMetricsEntry -> scopeMetricsEntry.asJsonObject().getJsonArray("metrics").stream())
                    .map(JsonValue::asJsonObject)
                    .toList();

            Set<String> routesSeen = new HashSet<>();

            for (JsonObject metricsEntry : metricsEntries) {
                var dataPoints = metricsEntry.getJsonObject("histogram")
                        .getJsonArray("dataPoints").stream()
                        .map(JsonValue::asJsonObject)
                        .toList();

                assertThat("Metrics entry name",
                           metricsEntry.getString("name"),
                           is(OpenTelemetryMetricsHttpSemanticConventions.TIMER_NAME));
                assertThat("Metrics entry histogram data points entry",
                           dataPoints,
                           hasItem(allOf(hasString("count", is("1")),
                                         hasDouble("sum", greaterThan(0.0D)),
                                         hasStringAttribute("http.route", notNullValue(String.class)))));

                AtomicInteger port = new AtomicInteger(-2);
                AtomicReference<String> socketName = new AtomicReference<>();

                dataPoints.stream()
                        .flatMap(dataPoint -> dataPoint.getJsonArray("attributes").stream())
                        .map(JsonValue::asJsonObject)
                        .forEach(attribute -> {
                            String key = attribute.getString("key");
                            if (key.equals(OpenTelemetryMetricsHttpSemanticConventions.HTTP_ROUTE)) {
                                routesSeen.add(attribute.getJsonObject("value").getString("stringValue"));
                            }
                            if (key.equals(OpenTelemetryMetricsHttpSemanticConventions.SOCKET_NAME)) {
                                var sName = attribute.getJsonObject("value").getString("stringValue");
                                socketName.set(sName);
                                socketNamesInTimers.add(sName);
                            }
                            if (key.equals(OpenTelemetryMetricsHttpSemanticConventions.SERVER_PORT)) {
                                port.set(attribute.getJsonObject("value").getInt("intValue"));
                            }
                        });

                var expectedPort = switch (socketName.get()) {
                    case "private" -> server.port("private");
                    case "@default" -> server.port();
                    default -> -1;
                };

                var portMatcher = is(expectedPort);
                if (!checkPort) {
                    portMatcher = not(portMatcher);
                }
                assertThat("Port", port.get(), portMatcher);

            }

            assertThat("Expected sockets", socketNamesInTimers, allOf(
                    hasItem("@default"),
                    hasItem("private"),
                    not(hasItem("admin"))
            ));


            assertThat("Routes seen", routesSeen, allOf(hasItem("/greet"),
                                                        hasItem("/greet/{name}"),
                                                        not(hasItem("/observe/metrics"))));

            Set<String> unexpectedlyUntimedSockets = new HashSet<>(Set.of("@default", "private"));
            socketNamesInTimers.forEach(unexpectedlyUntimedSockets::remove);
            assertThat("Unexpectedly untimed sockets", unexpectedlyUntimedSockets, hasSize(0));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
