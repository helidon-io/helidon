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

import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.service.registry.Services;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.Socket;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class HttpTransportMetricsSocketConfigTest {
    private static final String FIRST_OBSERVED_SOCKET = "observed-one";
    private static final String SECOND_OBSERVED_SOCKET = "observed-two";
    private static final String EXCLUDED_SOCKET = "excluded";
    private static final ConcurrentLinkedQueue<Meter> ADDED_METERS = new ConcurrentLinkedQueue<>();

    private static MeterRegistry registry;

    private final WebServer server;
    private final WebClient firstObservedClient;
    private final WebClient secondObservedClient;
    private final WebClient excludedClient;

    HttpTransportMetricsSocketConfigTest(WebServer server,
                                         @Socket(FIRST_OBSERVED_SOCKET) WebClient firstObservedClient,
                                         @Socket(SECOND_OBSERVED_SOCKET) WebClient secondObservedClient,
                                         @Socket(EXCLUDED_SOCKET) WebClient excludedClient) {
        this.server = server;
        this.firstObservedClient = firstObservedClient;
        this.secondObservedClient = secondObservedClient;
        this.excludedClient = excludedClient;
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder server) {
        ADDED_METERS.clear();
        registry = Services.get(MetricsFactory.class)
                .createMeterRegistry(MetricsConfig.builder()
                                             .warnOnMultipleRegistries(false)
                                             .build(),
                                     ADDED_METERS::add,
                                     _ -> { });
        server.addFeature(ObserveFeature.builder()
                                  .addObserver(MetricsObserver.builder()
                                                       .meterRegistry(registry)
                                                       .autoHttpMetrics(AutoHttpMetricsConfig.builder()
                                                                                .sockets(Set.of(FIRST_OBSERVED_SOCKET,
                                                                                                SECOND_OBSERVED_SOCKET))
                                                                                .build())
                                                       .build())
                                  .build());
    }

    @SetUpRoute(FIRST_OBSERVED_SOCKET)
    static void setUpFirstObservedRoute(HttpRouting.Builder routing) {
        routing.get("/test", (_, response) -> response.send("observed-one"));
    }

    @SetUpRoute(SECOND_OBSERVED_SOCKET)
    static void setUpSecondObservedRoute(HttpRouting.Builder routing) {
        routing.get("/test", (_, response) -> response.send("observed-two"));
    }

    @SetUpRoute(EXCLUDED_SOCKET)
    static void setUpExcludedRoute(HttpRouting.Builder routing) {
        routing.get("/test", (_, response) -> response.send("excluded"));
    }

    @Test
    void observesEverySelectedSocketAndNoOthers() {
        try {
            request(firstObservedClient);
            request(secondObservedClient);
            request(excludedClient);

            server.stop();

            assertThat("Only the two selected listener connections are observed",
                       openedConnections(),
                       is(2L));
            assertThat("Final selected listener stop removes transport meters", transportMetersRemaining(), is(0L));
        } finally {
            server.stop();
            registry.close();
        }
    }

    private static void request(WebClient client) {
        try (HttpClientResponse response = client.get("/test").request()) {
            assertThat(response.status().code(), is(200));
        }
    }

    private static long openedConnections() {
        return ADDED_METERS.stream()
                .filter(meter -> meter instanceof Counter)
                .filter(meter -> meter.id().name().equals("helidon.http.connections.opened"))
                .map(Counter.class::cast)
                .mapToLong(Counter::count)
                .sum();
    }

    private static long transportMetersRemaining() {
        return registry.meters()
                .stream()
                .filter(meter -> meter.id().name().startsWith("helidon.http.connections.")
                        || meter.id().name().startsWith("helidon.http.handshakes")
                        || meter.id().name().startsWith("helidon.http.streams."))
                .count();
    }
}
