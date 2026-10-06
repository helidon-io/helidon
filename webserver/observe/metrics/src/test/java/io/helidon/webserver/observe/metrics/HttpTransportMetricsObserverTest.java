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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Handshake;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.Role;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.json.JsonObject;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.MatcherWithRetry.assertThatWithRetry;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_1_1;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_TCP;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class HttpTransportMetricsObserverTest {
    private static final String CONNECTIONS_ACTIVE = "helidon.http.connections.active";
    private static final String STREAMS_ACTIVE = "helidon.http.streams.active";

    @Test
    void formatsActiveTransportGaugesAsJson() throws Exception {
        MetricsConfig metricsConfig = MetricsConfig.builder().warnOnMultipleRegistries(false).build();
        MeterRegistry registry = Services.get(MetricsFactory.class).createMeterRegistry(metricsConfig);
        HttpTransportMetricsObserver observer = new HttpTransportMetricsObserver(registry);
        observer.start();
        var connection = observer.connectionOpened(Role.SERVER, TRANSPORT_TCP, Handshake.NONE);
        connection.protocolSelected(PROTOCOL_HTTP_1_1);
        var stream = connection.streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE);
        try {
            for (String name : List.of(CONNECTIONS_ACTIVE, STREAMS_ACTIVE)) {
                assertThatWithRetry("Active gauge is registered and bound: " + name,
                                    () -> registry.meters().stream()
                                            .filter(meter -> meter.id().name().equals(name))
                                            .filter(meter -> PROTOCOL_HTTP_1_1.equals(meter.id().tagsMap().get("protocol")))
                                            .map(meter -> ((Gauge<?>) meter).value().doubleValue())
                                            .toList(),
                                    is(List.of(1D)));
            }

            JsonFormatter formatter = JsonFormatter.builder(metricsConfig, registry)
                    .meterNameSelection(Set.of(CONNECTIONS_ACTIVE, STREAMS_ACTIVE))
                    .tagSelection(Map.of("protocol", Set.of(PROTOCOL_HTTP_1_1)))
                    .build();
            assertActiveGauges(formatter, 1D);

            stream.close(StreamOutcome.COMPLETED);
            connection.close(ConnectionOutcome.NORMAL);
            assertActiveGauges(formatter, 0D);
        } finally {
            stream.close(StreamOutcome.COMPLETED);
            connection.close(ConnectionOutcome.NORMAL);
            try {
                observer.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
            } finally {
                registry.close();
            }
        }
    }

    @Test
    void sharesOneConfiguredRegistryLeaseAcrossListeners() throws Exception {
        CompletableFuture<Counter> openedMeter = new CompletableFuture<>();
        MeterRegistry configuredRegistry = newRegistry(openedMeter);
        HttpTransportMetricsObserver observer = new HttpTransportMetricsObserver(configuredRegistry);

        try {
            observer.start();
            observer.start();
            observer.connectionOpened(Role.SERVER, TRANSPORT_TCP, Handshake.NONE)
                    .close(ConnectionOutcome.NORMAL);
            Counter openedCounter = openedMeter.get(5, TimeUnit.SECONDS);

            observer.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat("First listener stop retains the shared lease", configuredRegistry.meters(), not(empty()));

            observer.connectionOpened(Role.SERVER, TRANSPORT_TCP, Handshake.NONE)
                    .close(ConnectionOutcome.NORMAL);
            observer.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertThat("Both listener events are recorded once", openedCounter.count(), is(2L));
            assertThat("Last listener stop releases the shared lease", configuredRegistry.meters(), empty());
        } finally {
            configuredRegistry.close();
        }
    }

    private static void assertActiveGauges(JsonFormatter formatter, double expected) {
        JsonObject output = (JsonObject) formatter.format().orElseThrow();
        assertThat("JSON active connections",
                   output.numberValue(CONNECTIONS_ACTIVE + ";protocol=http/1.1;role=server;transport=tcp")
                           .map(Number::doubleValue),
                   optionalValue(is(expected)));
        assertThat("JSON active streams",
                   output.numberValue(STREAMS_ACTIVE + ";direction=bidi;initiator=remote;protocol=http/1.1;role=server")
                           .map(Number::doubleValue),
                   optionalValue(is(expected)));
    }

    private static MeterRegistry newRegistry(CompletableFuture<Counter> openedMeter) {
        return Services.get(MetricsFactory.class)
                .createMeterRegistry(MetricsConfig.builder()
                                             .warnOnMultipleRegistries(false)
                                             .build(),
                                     meter -> {
                                         if (meter instanceof Counter counter
                                                 && meter.id().name().equals("helidon.http.connections.opened")) {
                                             openedMeter.complete(counter);
                                         }
                                     },
                                     _ -> { });
    }
}
