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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void fatalRemovalRetiresEveryChannelAndPreservesFailure(int channelCount) {
        // A fatal provider failure must not prevent the remaining channel recorders from releasing their meters.
        try (Environment environment = new Environment(channelCount)) {
            MessagingGraph graph = environment.graph();
            List<Meter> originalMeters = List.copyOf(environment.meters.meters());
            assertThat(originalMeters, hasSize(channelCount * 2));
            AtomicBoolean fail = new AtomicBoolean(true);
            AssertionError failure = new AssertionError("Fatal meter removal failure");
            environment.afterRemoval = _ -> {
                if (fail.compareAndSet(true, false)) {
                    throw failure;
                }
            };

            assertFatalClose(graph, failure);
            environment.assertRetired(originalMeters);
            environment.assertFreshGraph();
        }
    }

    @Test
    void runtimeRemovalFailureRetiresEveryChannel() {
        // An ordinary provider failure is swallowed after all leases have been retired.
        try (Environment environment = new Environment(2)) {
            MessagingGraph graph = environment.graph();
            List<Meter> originalMeters = List.copyOf(environment.meters.meters());
            assertThat(originalMeters, hasSize(4));
            AtomicBoolean fail = new AtomicBoolean(true);
            environment.afterRemoval = _ -> {
                if (fail.compareAndSet(true, false)) {
                    throw new IllegalStateException("Ordinary meter removal failure");
                }
            };

            graph.close();
            graph.close();
            assertThat(fail.get(), is(false));
            environment.assertRetired(originalMeters);
            environment.assertFreshGraph();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void multipleFatalFailuresRetireEveryChannel(boolean sameFailure) {
        // Preserve the first failure, suppress distinct later failures, and avoid self-suppression.
        try (Environment environment = new Environment(2)) {
            MessagingGraph graph = environment.graph();
            List<Meter> originalMeters = List.copyOf(environment.meters.meters());
            assertThat(originalMeters, hasSize(4));
            AssertionError first = new AssertionError("First channel removal failure");
            AssertionError second = sameFailure ? first : new AssertionError("Second channel removal failure");
            List<AssertionError> failures = List.of(first, second);
            AtomicInteger failuresThrown = new AtomicInteger();
            AtomicBoolean fail = new AtomicBoolean(true);
            environment.afterRemoval = meter -> {
                if (fail.get() && meter.id().name().equals(DURATION)) {
                    throw failures.get(failuresThrown.getAndIncrement());
                }
            };

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

    private static void assertFatalClose(MessagingGraph graph, AssertionError failure) {
        assertThat(assertThrows(AssertionError.class, graph::close), sameInstance(failure));
        assertThat(assertThrows(AssertionError.class, graph::close), sameInstance(failure));
    }

    private static final class Environment implements AutoCloseable {
        private final HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        private final MeterRegistry meters = new DelegatingMeterRegistry(factory.createMeterRegistry(MetricsConfig.create())) {
            @Override
            public Optional<Meter> remove(Meter meter) {
                Optional<Meter> removed = super.remove(meter);
                // Inject at the public provider boundary; native removal listeners intentionally suppress failures.
                removed.ifPresent(afterRemoval);
                return removed;
            }
        };
        private final ServiceRegistryManager graphServices = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .putContractInstance(MeterRegistry.class, meters)
                .build());
        private final List<MessagingChannel<String>> channels;
        private Consumer<Meter> afterRemoval = _ -> { };

        private Environment(int channelCount) {
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
                factory.close();
            }
        }

        private MessagingGraph graph() {
            MessagingConfig.Builder builder = MessagingGraph.builder()
                    .serviceRegistry(graphServices.registry())
                    .health(h -> h.enabled(false));
            for (MessagingChannel<String> channel : channels) {
                builder.channel(channel).payloadSink(channel, payload -> {
                    if (payload.equals("fail")) {
                        throw new IllegalStateException("Processing failed");
                    }
                });
            }
            return MessagingGraph.create(builder.buildPrototype(), graphServices.registry()).start();
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
