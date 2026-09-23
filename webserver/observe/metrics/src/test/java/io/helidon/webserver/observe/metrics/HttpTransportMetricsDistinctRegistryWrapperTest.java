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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class HttpTransportMetricsDistinctRegistryWrapperTest {
    private static final ConcurrentLinkedQueue<Meter> ADDED_METERS = new ConcurrentLinkedQueue<>();

    private static MeterRegistry nativeRegistry;

    private final WebServer server;
    private final WebClient client;

    HttpTransportMetricsDistinctRegistryWrapperTest(WebServer server, WebClient client) {
        this.server = server;
        this.client = client;
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder server) {
        ADDED_METERS.clear();
        nativeRegistry = Services.get(MetricsFactory.class)
                .createMeterRegistry(MetricsConfig.builder()
                                             .warnOnMultipleRegistries(false)
                                             .build(),
                                     ADDED_METERS::add,
                                     _ -> { });
        MeterRegistry firstWrapper = wrapper(nativeRegistry);
        MeterRegistry secondWrapper = wrapper(nativeRegistry);
        server.addFeature(ObserveFeature.builder()
                                  .addObserver(MetricsObserver.builder()
                                                       .name("metrics-one")
                                                       .endpoint("metrics-one")
                                                       .meterRegistry(firstWrapper)
                                                       .build())
                                  .addObserver(MetricsObserver.builder()
                                                       .name("metrics-two")
                                                       .endpoint("metrics-two")
                                                       .meterRegistry(secondWrapper)
                                                       .build())
                                  .build());
    }

    @SetUpRoute
    static void setUpRoute(HttpRouting.Builder routing) {
        routing.get("/test", (_, response) -> response.send("ok"));
    }

    @Test
    void retainsDistinctConfiguredWrappersOverTheSameNativeRegistry() {
        try {
            try (HttpClientResponse response = client.get("/test").request()) {
                assertThat(response.status().code(), is(200));
            }

            server.stop();

            assertThat("Each distinct configured wrapper receives the physical connection",
                       openedConnections(),
                       is(2L));
            assertThat("Final listener shutdown removes shared-native transport meters",
                       transportMetersRemaining(),
                       is(0L));
        } finally {
            server.stop();
            nativeRegistry.close();
        }
    }

    private static MeterRegistry wrapper(MeterRegistry delegate) {
        return (MeterRegistry) Proxy.newProxyInstance(MeterRegistry.class.getClassLoader(),
                                                      new Class<?>[] {MeterRegistry.class},
                                                      (_, method, arguments) -> {
                                                          try {
                                                              return method.invoke(delegate, arguments);
                                                          } catch (InvocationTargetException e) {
                                                              throw e.getCause();
                                                          }
                                                      });
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
        return nativeRegistry.meters()
                .stream()
                .filter(meter -> meter.id().name().startsWith("helidon.http.connections.")
                        || meter.id().name().startsWith("helidon.http.handshakes")
                        || meter.id().name().startsWith("helidon.http.streams."))
                .count();
    }
}
