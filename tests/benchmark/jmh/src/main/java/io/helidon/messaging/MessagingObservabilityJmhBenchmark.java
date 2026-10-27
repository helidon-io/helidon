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
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.spi.MetricsFactoryProvider;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures observability overhead for a short handler and graph registration in a populated registry.
 * Dispatch operations deliver one prebuilt batch; construction operations build and close one graph.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(3)
public class MessagingObservabilityJmhBenchmark {
    private static final String FACTORY_PROVIDER =
            "io.helidon.metrics.providers.helidon.HelidonMetricsFactoryProvider";

    @Benchmark
    public String dispatch(DispatchState state) {
        state.emitter.emit(state.batch);
        return state.consumed;
    }

    @Benchmark
    public MessagingGraph constructAndCloseWithRegisteredMeters(RegisteredMetersState state) {
        return constructAndClose(state.environment, state.channels);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 5, batchSize = 1)
    @Measurement(iterations = 8, batchSize = 1)
    public MessagingGraph constructAndCloseWithNewMeters(NewMetersState state) {
        return constructAndClose(state.environment, state.channels);
    }

    private static MessagingGraph constructAndClose(Environment environment, List<MessagingChannel<String>> channels) {
        MessagingConfig.Builder builder = environment.builder();
        for (MessagingChannel<String> channel : channels) {
            builder.channel(channel).payloadSink(channel, _ -> { });
        }
        MessagingGraph graph = environment.graph(builder);
        graph.close();
        return graph;
    }

    private static void closeAfterFailure(Runnable cleanup, Throwable failure) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error cleanupFailure) {
            if (cleanupFailure != failure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    @State(Scope.Thread)
    public static class DispatchState {
        @Param({"enabled", "disabled", "absent"})
        public String metrics;

        @Param({"1", "32"})
        public int batchSize;

        private Environment environment;
        private MessagingGraph graph;
        private Emitter<String> emitter;
        private MessageBatch<String> batch;
        private volatile String consumed;

        @Setup(Level.Trial)
        public void setUp() {
            environment = new Environment(metrics, 0);
            try {
                MessagingChannel<String> channel = MessagingChannel.create("dispatch", String.class);
                MessagingConfig.Builder builder = environment.builder()
                        .channel(channel)
                        .payloadSink(channel, value -> consumed = value);
                graph = environment.graph(builder);
                environment.verifyMeters(1);
                graph.start();
                emitter = graph.emitter(channel);
                List<Message<String>> messages = new ArrayList<>(batchSize);
                for (int i = 0; i < batchSize; i++) {
                    messages.add(Message.create("payload-" + i));
                }
                batch = MessageBatch.create(messages);
            } catch (RuntimeException | Error e) {
                closeAfterFailure(this::tearDown, e);
                throw e;
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            try {
                if (graph != null) {
                    graph.close();
                }
            } finally {
                if (environment != null) {
                    environment.close();
                }
            }
        }
    }

    @State(Scope.Thread)
    public static class RegisteredMetersState {
        @Param({"enabled", "disabled"})
        public String metrics;

        @Param({"1", "64"})
        public int channelCount;

        @Param({"0", "4096"})
        public int unrelatedMeters;

        private Environment environment;
        private List<MessagingChannel<String>> channels;

        @Setup(Level.Trial)
        public void setUp() {
            environment = new Environment(metrics, unrelatedMeters);
            channels = new ArrayList<>(channelCount);
            for (int i = 0; i < channelCount; i++) {
                channels.add(MessagingChannel.create("channel-" + i, String.class));
            }
            // Validate setup outside measurement so a missing metrics service cannot masquerade as a speedup.
            MessagingConfig.Builder builder = environment.builder();
            for (MessagingChannel<String> channel : channels) {
                builder.channel(channel).payloadSink(channel, _ -> { });
            }
            try (MessagingGraph _ = environment.graph(builder)) {
                environment.verifyMeters(channelCount);
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            environment.close();
        }
    }

    @State(Scope.Thread)
    public static class NewMetersState {
        @Param({"enabled", "disabled"})
        public String metrics;

        @Param({"1", "64"})
        public int channelCount;

        @Param({"0", "4096"})
        public int unrelatedMeters;

        private Environment environment;
        private List<MessagingChannel<String>> channels;

        @Setup(Level.Iteration)
        public void setUp() {
            environment = new Environment(metrics, unrelatedMeters);
            channels = new ArrayList<>(channelCount);
            for (int i = 0; i < channelCount; i++) {
                channels.add(MessagingChannel.create("channel-" + i, String.class));
            }
            environment.verifyMeters(0);
        }

        @TearDown(Level.Iteration)
        public void tearDown() {
            try {
                // Single-shot iterations construct exactly one graph in a registry without matching channel meters.
                environment.verifyMeters(channelCount);
            } finally {
                environment.close();
            }
        }
    }

    static final class Environment implements AutoCloseable {
        private final ServiceRegistryManager factoryServices;
        private final ServiceRegistryManager services;
        private final MetricsFactory factory;
        private final MeterRegistry registry;
        private final Config config;
        private final int unrelatedMeters;
        private final boolean enabled;

        private int registeredChannels;

        Environment(String metrics, int unrelatedMeters) {
            this.unrelatedMeters = unrelatedMeters;
            enabled = metrics.equals("enabled");
            config = Config.just(ConfigSources.create(Map.of("metrics.enabled", Boolean.toString(!metrics.equals("disabled")),
                                                             "health.enabled", "false")));
            factoryServices = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                    .discoverServices(false)
                                                                    .discoverServicesFromServiceLoader(false)
                                                                    .build());
            ServiceRegistryConfig.Builder servicesConfig = ServiceRegistryConfig.builder()
                    .discoverServices(false)
                    .discoverServicesFromServiceLoader(false);
            if (metrics.equals("absent")) {
                factory = null;
                registry = null;
            } else {
                MetricsFactoryProvider provider = ServiceLoader.load(MetricsFactoryProvider.class)
                        .stream()
                        .filter(candidate -> candidate.type().getName().equals(FACTORY_PROVIDER))
                        .map(ServiceLoader.Provider::get)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Missing " + FACTORY_PROVIDER));
                MetricsConfig metricsConfig = MetricsConfig.builder().warnOnMultipleRegistries(false).build();
                factory = provider.create(Config.empty(), metricsConfig, List.of(), factoryServices.registry());
                registry = factory.createMeterRegistry(metricsConfig);
                servicesConfig.putContractInstance(MeterRegistry.class, registry);
                for (int i = 0; i < unrelatedMeters; i++) {
                    registry.getOrCreate(factory.counterBuilder("unrelated." + i));
                }
            }
            services = ServiceRegistryManager.create(servicesConfig.build());
        }

        @Override
        public void close() {
            try {
                verifyMeters(registeredChannels);
            } finally {
                try {
                    services.shutdown();
                } finally {
                    try {
                        if (registry != null) {
                            registry.close();
                        }
                    } finally {
                        try {
                            if (factory != null) {
                                factory.close();
                            }
                        } finally {
                            factoryServices.shutdown();
                        }
                    }
                }
            }
        }

        MessagingConfig.Builder builder() {
            return MessagingGraph.builder().config(config).serviceRegistry(services.registry());
        }

        MessagingGraph graph(MessagingConfig.Builder builder) {
            return new MessagingGraphAssembler(builder.buildPrototype(), services.registry()).graph();
        }

        MessagingMetrics observation() {
            return MessagingMetrics.create(builder().buildPrototype(), services.registry());
        }

        void verifyMeters(int channels) {
            if (registry != null && registry.meters().size() != unrelatedMeters + (enabled ? 2 * channels : 0)) {
                throw new IllegalStateException("Metrics mode did not register the expected meters in the supplied registry");
            }
            registeredChannels = channels;
        }
    }
}
