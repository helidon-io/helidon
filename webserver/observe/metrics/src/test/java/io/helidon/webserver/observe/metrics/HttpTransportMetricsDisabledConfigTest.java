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

import java.util.concurrent.ConcurrentLinkedQueue;

import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class HttpTransportMetricsDisabledConfigTest {
    private static final ConcurrentLinkedQueue<Meter> ADDED_METERS = new ConcurrentLinkedQueue<>();
    private static final MeterRegistry REGISTRY = MeterRegistry.create().onMeterAdded(ADDED_METERS::add);

    private final WebServer server;
    private final WebClient client;

    HttpTransportMetricsDisabledConfigTest(WebServer server, WebClient client) {
        this.server = server;
        this.client = client;
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder server) {
        ADDED_METERS.clear();
        server.addFeature(ObserveFeature.builder()
                                  .addObserver(MetricsObserver.builder()
                                                       .meterRegistry(REGISTRY)
                                                       .autoHttpMetrics(AutoHttpMetricsConfig.builder()
                                                                                .enabled(false)
                                                                                .build())
                                                       .build())
                                  .build());
    }

    @SetUpRoute
    static void setUpRoute(HttpRouting.Builder routing) {
        routing.get("/test", (_, response) -> response.send("ok"));
    }

    @Test
    void disablesTransportMetricsWithAutomaticHttpMetrics() {
        try (HttpClientResponse response = client.get("/test").request()) {
            assertThat(response.status().code(), is(200));
        }
        server.stop();

        boolean transportMeterAdded = ADDED_METERS.stream()
                .anyMatch(meter -> meter.id().name().startsWith("http.connections.")
                        || meter.id().name().startsWith("http.handshakes")
                        || meter.id().name().startsWith("http.streams."));
        assertThat(transportMeterAdded, is(false));
    }
}
