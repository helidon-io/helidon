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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
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
class HttpTransportMetricsIntegrationTest {
    private static final MeterRegistry REGISTRY = MeterRegistry.create();

    private final WebClient client;

    HttpTransportMetricsIntegrationTest(WebClient client) {
        this.client = client;
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder server) {
        server.addFeature(ObserveFeature.builder()
                                  .addObserver(MetricsObserver.builder()
                                                       .meterRegistry(REGISTRY)
                                                       .build())
                                  .build());
    }

    @SetUpRoute
    static void setUpRoute(HttpRouting.Builder routing) {
        routing.get("/test", (_, response) -> response.send("ok"));
    }

    @Test
    void observesAcceptedHttp11ConnectionInConfiguredRegistry() {
        try (HttpClientResponse response = client.get("/test").request()) {
            assertThat(response.status().code(), is(200));
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (establishedConnectionMeters() == 0 && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Interrupted while waiting for HTTP transport metrics");
            }
        }
        assertThat("configured registry meters: " + REGISTRY.meters().stream()
                           .map(meter -> meter.id().name() + meter.id().tagsMap()
                                   + (meter instanceof Counter counter ? "=" + counter.count() : ""))
                           .sorted()
                           .toList(),
                   establishedConnectionMeters() > 0,
                   is(true));
    }

    private static long establishedConnectionMeters() {
        return REGISTRY.meters().stream()
                .filter(meter -> meter instanceof Counter)
                .filter(meter -> meter.id().name().equals("http.connections.established"))
                .filter(meter -> "server".equals(meter.id().tagsMap().get("role")))
                .filter(meter -> "tcp".equals(meter.id().tagsMap().get("transport")))
                .filter(meter -> "http/1.1".equals(meter.id().tagsMap().get("protocol")))
                .count();
    }
}
