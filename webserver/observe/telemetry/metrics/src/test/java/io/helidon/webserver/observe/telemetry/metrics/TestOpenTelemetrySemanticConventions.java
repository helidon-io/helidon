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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.junit5.MatcherWithRetry;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.Method;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;import io.helidon.metrics.api.Timer;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.Socket;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasEntry;
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
        var meterRegistry = Metrics.globalRegistry();

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
                        .request()) {

            assertThat("Greet endpoint", defaultResponse.status().code(), is(200));
            assertThat("Private endpoint", privateResponse.status().code(), is(200));
            assertThat("Admin endpoint", adminResponse.status().code(), is(200));
            assertThat("Metrics endpoint via default socket", metricsOnDefaultResponse.status().code(), is(404));
            assertThat("Private endpoint HEAD", greetOptionsResponse.status().code(), is(200));

            /*
            Helidon registers and updates the timer asynchronously, so tolerate some delay.
            Despite all the requests we sent, we expect only two timers to have been created (and updated).
             */
            AtomicReference<List<Timer>> timersRef = requestTimers(meterRegistry, 2);

            /*
            There should be two timers, one for each of the greet endpoints and none for the
            metrics endpoint. Find each and make sure the socket name tag has the correct value
            and the timer's count is 1.
             */

            List<String> socketNamesInTimers = new ArrayList<>();

            for (Timer timer : timersRef.get()) {
                Map<String, String> tags = timer.id().tagsMap();

                /*
                Perform the checks below only on timers for successful requests. We'll verify the unsuccessful one later.
                 */
                if (!tags.get(OpenTelemetryMetricsHttpSemanticConventions.STATUS_CODE).equals("200")) {
                    continue;
                }

                var socketName = tags.get(OpenTelemetryMetricsHttpSemanticConventions.SOCKET_NAME);

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
                Even after the timer appears in the registry, it's possible we would check its count before the filter had
                updated the timer. So retry when we check the count as well.

                This test is written so each timer should be updated once, hence the hard-coded 1 below.
                 */
                MatcherWithRetry.assertThatWithRetry("Socket " + socketName + " timer update count",
                                                     timer::count,
                                                     is(1L));

                assertThat("Socket " + socketName + " timer total time", timer.totalTime(TimeUnit.NANOSECONDS),
                           greaterThan(0D));
                assertThat("Socket " + socketName + " timer name", timer.id().name(),
                           is(OpenTelemetryMetricsHttpSemanticConventions.TIMER_NAME));

                var portMatcher = hasKey(OpenTelemetryMetricsHttpSemanticConventions.SERVER_PORT);
                if (!checkPort) {
                    portMatcher = not(portMatcher);
                }
                assertThat("Tags", tags, allOf(
                        hasEntry(OpenTelemetryMetricsHttpSemanticConventions.HTTP_METHOD, "GET"),
                        hasEntry(OpenTelemetryMetricsHttpSemanticConventions.URL_SCHEME, "HTTP"),
                        hasEntry(OpenTelemetryMetricsHttpSemanticConventions.STATUS_CODE, "200"),
                        hasEntry(OpenTelemetryMetricsHttpSemanticConventions.ERROR_TYPE, ""),
                        hasEntry(OpenTelemetryMetricsHttpSemanticConventions.HTTP_ROUTE, expectedHttpRoute),
                        hasEntry(OpenTelemetryMetricsHttpSemanticConventions.SERVER_ADDRESS, "localhost"),
                        portMatcher,
                        hasEntry(OpenTelemetryMetricsHttpSemanticConventions.SOCKET_NAME, socketName)));

            }
            assertThat("Expected sockets", socketNamesInTimers, allOf(
                    hasItem("@default"),
                    hasItem("private"),
                    not(hasItem("admin"))
            ));

            Set<String> unexpectedlyUntimedSockets = new HashSet<>(Set.of("@default", "private"));
            socketNamesInTimers.forEach(unexpectedlyUntimedSockets::remove);
            assertThat("Unexpectedly untimed sockets", unexpectedlyUntimedSockets, hasSize(0));

        }

    }

    private AtomicReference<List<Timer>> requestTimers(MeterRegistry meterRegistry, int expectedCount) {

        AtomicReference<List<Timer>> timersRef = new AtomicReference<>();
        MatcherWithRetry.assertThatWithRetry("Automatic timers",
                                             () -> {
                                                 timersRef.set(meterRegistry.meters(meter -> meter.id().name()
                                                                 .equals(OpenTelemetryMetricsHttpSemanticConventions.TIMER_NAME))
                                                                       .stream()
                                                                       .filter(m -> m instanceof Timer)
                                                                       .map(m -> (Timer) m)
                                                                       .toList());
                                                 return timersRef.get();
                                             },
                                             hasSize(greaterThanOrEqualTo(expectedCount)));
        return timersRef;
    }
}
