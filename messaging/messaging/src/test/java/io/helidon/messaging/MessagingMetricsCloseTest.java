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

package io.helidon.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.config.Config;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.providers.micrometer.MicrometerMetricsFactoryProvider;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(10)
class MessagingMetricsCloseTest {
    private static final String DURATION = "messaging.processing.duration";
    private static final String FAILURES = "messaging.processing.failures";

    @Test
    void fatalRemovalRetiresEveryChannelAndPreservesFailure() {
        for (int channelCount : List.of(1, 2)) {
            try (Environment environment = new Environment(channelCount)) {
                MessagingGraph graph = environment.graph();
                List<Meter> originalMeters = List.copyOf(environment.meters.meters());
                assertThat(originalMeters, hasSize(channelCount * 2));
                AtomicBoolean fail = new AtomicBoolean(true);
                AssertionError failure = new AssertionError("Fatal meter removal failure");
                environment.meters.onMeterRemoved(_ -> {
                    if (fail.compareAndSet(true, false)) {
                        throw failure;
                    }
                });

                assertFatalClose(graph, failure);
                environment.assertRetired(originalMeters);
                environment.assertFreshGraph();
            }
        }
    }

    @Test
    void runtimeRemovalFailureRetiresEveryChannel() {
        try (Environment environment = new Environment(2)) {
            MessagingGraph graph = environment.graph();
            List<Meter> originalMeters = List.copyOf(environment.meters.meters());
            assertThat(originalMeters, hasSize(4));
            AtomicBoolean fail = new AtomicBoolean(true);
            environment.meters.onMeterRemoved(_ -> {
                if (fail.compareAndSet(true, false)) {
                    throw new IllegalStateException("Ordinary meter removal failure");
                }
            });

            graph.close();
            graph.close();
            assertThat(fail.get(), is(false));
            environment.assertRetired(originalMeters);
            environment.assertFreshGraph();
        }
    }

    @Test
    void distinctFatalFailuresAreSuppressedAfterEveryChannelIsRetired() {
        assertMultipleFailures(false);
    }

    @Test
    void repeatedFatalFailureIsNotSuppressedOnItself() {
        assertMultipleFailures(true);
    }

    private static void assertFatalClose(MessagingGraph graph, AssertionError failure) {
        assertThat(assertThrows(AssertionError.class, graph::close), sameInstance(failure));
        assertThat(assertThrows(AssertionError.class, graph::close), sameInstance(failure));
    }

    private static void assertMultipleFailures(boolean sameFailure) {
        try (Environment environment = new Environment(2)) {
            MessagingGraph graph = environment.graph();
            List<Meter> originalMeters = List.copyOf(environment.meters.meters());
            assertThat(originalMeters, hasSize(4));
            AssertionError first = new AssertionError("First channel removal failure");
            AssertionError second = sameFailure ? first : new AssertionError("Second channel removal failure");
            List<AssertionError> failures = List.of(first, second);
            AtomicInteger failuresThrown = new AtomicInteger();
            AtomicBoolean fail = new AtomicBoolean(true);
            environment.meters.onMeterRemoved(meter -> {
                if (fail.get() && meter.id().name().equals(DURATION)) {
                    throw failures.get(failuresThrown.getAndIncrement());
                }
            });

            try {
                assertFatalClose(graph, first);
                assertThat(failuresThrown.get(), is(2));
                environment.assertRetired(originalMeters);
                if (sameFailure) {
                    assertThat(List.of(first.getSuppressed()), empty());
                } else {
                    assertThat(List.of(first.getSuppressed()), contains(sameInstance(second)));
                }
            } finally {
                fail.set(false);
            }
            environment.assertFreshGraph();
        }
    }

    private static final class Environment implements AutoCloseable {
        private final ServiceRegistryManager factoryServices = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .build());
        private final MetricsConfig config = MetricsConfig.create();
        private final MetricsFactory factory = new MicrometerMetricsFactoryProvider()
                .create(Config.empty(), config, List.of(), factoryServices.registry());
        private final MeterRegistry meters = factory.createMeterRegistry(config);
        private final ServiceRegistryManager graphServices = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .putContractInstance(MeterRegistry.class, meters)
                .build());
        private final List<MessagingChannel<String>> channels;

        private Environment(int channelCount) {
            meters.unwrap(CompositeMeterRegistry.class).add(new SimpleMeterRegistry());
            List<MessagingChannel<String>> configuredChannels = new ArrayList<>();
            for (int i = 0; i < channelCount; i++) {
                configuredChannels.add(MessagingChannel.create("channel-" + i, String.class));
            }
            channels = List.copyOf(configuredChannels);
        }

        @Override
        public void close() {
            try {
                graphServices.shutdown();
            } finally {
                try {
                    factory.close();
                } finally {
                    factoryServices.shutdown();
                }
            }
        }

        private MessagingGraph graph() {
            MessagingConfig.Builder builder = MessagingGraph.builder()
                    .serviceRegistry(graphServices.registry())
                    .enableHealth(false);
            for (MessagingChannel<String> channel : channels) {
                builder.channel(channel).payloadSink(channel, payload -> {
                    if (payload.equals("fail")) {
                        throw new IllegalStateException("Processing failed");
                    }
                });
            }
            return builder.build().start();
        }

        private void assertRetired(List<Meter> retiredMeters) {
            assertThat(meters.meters(), empty());
            for (Meter meter : retiredMeters) {
                assertThat(meters.isDeleted(meter), is(true));
            }
        }

        private void assertFreshGraph() {
            List<Meter> freshMeters;
            try (MessagingGraph graph = graph()) {
                freshMeters = List.copyOf(meters.meters());
                assertThat(freshMeters, hasSize(channels.size() * 2));
                for (MessagingChannel<String> channel : channels) {
                    Emitter<String> emitter = graph.emitter(channel);
                    emitter.emit("delivered");
                    assertThrows(BatchDeliveryException.class, () -> emitter.emit("fail"));
                    var tags = List.of(factory.tagCreate("channel", channel.name()));
                    assertThat(meters.timer(DURATION, tags).orElseThrow().count(), is(2L));
                    assertThat(meters.counter(FAILURES, tags).orElseThrow().count(), is(1L));
                }
            }
            assertRetired(freshMeters);
        }
    }
}
