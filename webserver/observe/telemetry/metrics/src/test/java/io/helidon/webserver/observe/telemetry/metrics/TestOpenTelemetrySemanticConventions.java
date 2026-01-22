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
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Services;
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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@ServerTest
class TestOpenTelemetrySemanticConventions {

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
                            sockets: ["@default","private"]
                """;
        Config config = Config.just(ConfigSources.create(configText, MediaTypes.APPLICATION_YAML));
        serverBuilder
                .config(config.get("server"))
                .routing(r -> r.get("/greet/{name}",
                                    (req, resp) ->
                                            resp.send("Hello, " + req.path().segments().get(1).value() + "!")))
                .routing("private", r -> r.get("/greet",
                                               (req, resp) ->
                                                       resp.send("Hello, World!")));

    }

    @Test
    void checkAutoMetrics() {
        var meterRegistry = Services.get(MeterRegistry.class);

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
                        .request()) {

            assertThat("Greet endpoint", defaultResponse.status().code(), is(200));
            assertThat("Private endpoint", privateResponse.status().code(), is(200));
            assertThat("Admin endpoint", adminResponse.status().code(), is(200));
            assertThat("Metrics endpoint via default socket", metricsOnDefaultResponse.status().code(), is(404));

            /*
            Helidon registers and updates the timer asynchronously, so tolerate some delay.
             */
            AtomicReference<List<Timer>> timersRef = requestTimers(meterRegistry, 3);

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
                if (!tags.get(OpenTelemetryAutoHttpMetricsFilter.STATUS_CODE).equals("200")) {
                    continue;
                }

                var socketName = tags.get(OpenTelemetryAutoHttpMetricsFilter.SOCKET_NAME);

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
                           is(OpenTelemetryAutoHttpMetricsFilter.TIMER_NAME));

                assertThat("Tags", tags, allOf(
                        hasEntry(OpenTelemetryAutoHttpMetricsFilter.HTTP_METHOD, "GET"),
                        hasEntry(OpenTelemetryAutoHttpMetricsFilter.HTTP_SCHEME, "HTTP"),
                        hasEntry(OpenTelemetryAutoHttpMetricsFilter.STATUS_CODE, "200"),
                        hasEntry(OpenTelemetryAutoHttpMetricsFilter.ERROR_TYPE, ""),
                        hasEntry(OpenTelemetryAutoHttpMetricsFilter.HTTP_ROUTE, expectedHttpRoute),
                        hasEntry(OpenTelemetryAutoHttpMetricsFilter.NETWORK_PROTOCOL_NAME, "HTTP"),
                        hasEntry(OpenTelemetryAutoHttpMetricsFilter.NETWORK_PROTOCOL_VERSION, "1.1"),
                        hasEntry(OpenTelemetryAutoHttpMetricsFilter.SERVER_ADDRESS, "localhost"),
                        hasEntry(OpenTelemetryAutoHttpMetricsFilter.SERVER_PORT, Integer.toString(expectedPort)),
                        hasEntry(OpenTelemetryAutoHttpMetricsFilter.SOCKET_NAME, socketName)));

            }
            assertThat("Expected sockets", socketNamesInTimers, allOf(
                    hasItem("@default"),
                    hasItem("private"),
                    not(hasItem("admin"))
            ));

            Timer timerForFailedMetricsAccess = timersRef.get().stream()
                            .filter(t -> t.id().tagsMap().get(OpenTelemetryAutoHttpMetricsFilter.STATUS_CODE).equals("404"))
                                    .findFirst().orElseThrow();

            assertThat("Timer for failed access", timerForFailedMetricsAccess.count(), is(1L));
            assertThat("Tags for timer for failed access", timerForFailedMetricsAccess.id().tagsMap(), allOf(
                    hasEntry(OpenTelemetryAutoHttpMetricsFilter.HTTP_METHOD, "GET"),
                    hasEntry(OpenTelemetryAutoHttpMetricsFilter.HTTP_SCHEME, "HTTP"),
                    hasEntry(OpenTelemetryAutoHttpMetricsFilter.STATUS_CODE, "404"),
                    hasEntry(OpenTelemetryAutoHttpMetricsFilter.ERROR_TYPE, "404"),
                    hasEntry(OpenTelemetryAutoHttpMetricsFilter.HTTP_ROUTE, ""), // on failure, the route in the filter is empty
                    hasEntry(OpenTelemetryAutoHttpMetricsFilter.NETWORK_PROTOCOL_NAME, "HTTP"),
                    hasEntry(OpenTelemetryAutoHttpMetricsFilter.NETWORK_PROTOCOL_VERSION, "1.1"),
                    hasEntry(OpenTelemetryAutoHttpMetricsFilter.SERVER_ADDRESS, "localhost"),
                    hasEntry(OpenTelemetryAutoHttpMetricsFilter.SERVER_PORT, Integer.toString(server.port())),
                    hasEntry(OpenTelemetryAutoHttpMetricsFilter.SOCKET_NAME, "@default")));

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
                                                                 .equals(OpenTelemetryAutoHttpMetricsFilter.TIMER_NAME))
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
