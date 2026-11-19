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

package io.helidon.declarative.tests.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;
import io.helidon.health.spi.HealthCheckProvider;
import io.helidon.messaging.BatchDeliveryException;
import io.helidon.messaging.MessagingChannel;
import io.helidon.messaging.MessagingGraph;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(15)
@Testing.Test(perMethod = true)
@Execution(ExecutionMode.SAME_THREAD)
class DeclarativeObservabilityTest {
    private static final String DURATION = "messaging.processing.duration";
    private static final String FAILURES = "messaging.processing.failures";

    @Test
    void generatedBatchHandlerAndFanOutCountOneAttempt() {
        // Generated consumers and fan-out still share the runtime dispatch boundary for one emitted batch.
        try (Fixture fixture = new Fixture()) {
            ChannelMessagingTypes.Producer producer = fixture.registry.get(ChannelMessagingTypes.Producer.class);
            producer.emitChannelOneBatch("first", "second");

            assertThat(fixture.registry.get(ChannelMessagingTypes.FirstChannelOneConsumer.class).messages(), hasSize(2));
            assertThat(fixture.registry.get(ChannelMessagingTypes.SecondChannelOneConsumer.class).messages(), hasSize(2));
            assertThat(fixture.registry.get(ChannelMessagingTypes.BatchChannelOneConsumer.class).batches(), hasSize(1));
            fixture.assertCounts(ChannelMessagingTypes.CHANNEL_ONE, 1, 0);
        }
    }

    @Test
    void imperativeAndGeneratedGraphsShareTheSelectedRegistryAndChannelMeters() {
        // Configure one application registry before creating either graph; the extension isolates it to this test.
        try (Fixture fixture = new Fixture()) {
            Services.registry(fixture.registry);
            ChannelMessagingTypes.Producer producer = fixture.registry.get(ChannelMessagingTypes.Producer.class);
            producer.emitChannelTwo("declarative first");

            MessagingChannel<String> channel = MessagingChannel.create(ChannelMessagingTypes.CHANNEL_TWO, String.class);
            List<String> imperative = new ArrayList<>();
            try (MessagingGraph graph = MessagingGraph.builder()
                    .channel(channel).payloadSink(channel, imperative::add).build().start()) {
                graph.emitter(channel).emit("imperative");

                assertThat(imperative, is(List.of("imperative")));
                fixture.assertCounts(channel.name(), 2, 0);
            }

            producer.emitChannelTwo("declarative second");
            assertThat(fixture.registry.get(ChannelMessagingTypes.ChannelTwoConsumer.class).messages().stream()
                               .map(message -> message.entity()).toList(),
                       is(List.of("declarative first", "declarative second")));
            fixture.assertCounts(channel.name(), 3, 0);
        }
    }

    @Test
    void generatedHandlerFailureCountsAndPreservesApplicationFailure() {
        // Generated adapters may wrap the delivery exception; observation must preserve the original application cause.
        try (Fixture fixture = new Fixture()) {
            ChannelMessagingTypes.Producer producer = fixture.registry.get(ChannelMessagingTypes.Producer.class);
            ChannelMessagingTypes.FailingConsumer consumer = fixture.registry.get(ChannelMessagingTypes.FailingConsumer.class);

            BatchDeliveryException failure = assertThrows(BatchDeliveryException.class,
                                                          () -> producer.emitFailingChannel("fail"));

            Throwable cause = failure;
            while (cause != consumer.failure() && cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertThat(cause, sameInstance(consumer.failure()));
            assertThat(consumer.attempts(), is(1));
            fixture.assertCounts(ChannelMessagingTypes.FAILING_CHANNEL, 1, 1);
            assertThat(fixture.checks().stream().map(check -> check.call().status()).toList(),
                       is(List.of(HealthCheckResponse.Status.UP, HealthCheckResponse.Status.UP)));
        }
    }

    @Test
    void previouslyDiscoveredHealthChecksObserveImperativeAndGeneratedGraphs() {
        // Discover checks before either graph exists, then verify that they observe later graph membership and startup.
        try (Fixture fixture = new Fixture()) {
            Services.registry(fixture.registry);
            List<HealthCheck> checks = fixture.checks();
            assertThat(checks, hasSize(2));
            assertThat(checks.stream().map(check -> check.call().status()).toList(),
                       is(List.of(HealthCheckResponse.Status.UP, HealthCheckResponse.Status.UP)));
            HealthCheck readiness = checks.stream().filter(check -> check.type() == HealthCheckType.READINESS)
                    .findFirst().orElseThrow();
            MessagingChannel<String> channel = MessagingChannel.create("late-graph", String.class);
            try (MessagingGraph graph = MessagingGraph.builder()
                    .channel(channel).payloadSink(channel, _ -> { }).build()) {
                assertThat(readiness.call().status(), is(HealthCheckResponse.Status.DOWN));
                fixture.registry.get(ChannelMessagingTypes.Producer.class).emitChannelTwo("start graph");
                assertThat(readiness.call().status(), is(HealthCheckResponse.Status.DOWN));

                graph.start();
                assertThat(readiness.call().status(), is(HealthCheckResponse.Status.UP));
            }
            assertThat(readiness.call().status(), is(HealthCheckResponse.Status.UP));
        }
    }

    @Test
    void declarativeConfigurationCanDisableMetricsAndHealth() {
        // Declarative bootstrap must apply the same nested observer configuration as programmatic graph creation.
        Config config = Config.just(ConfigSources.create(Map.of("messaging.metrics.enabled", "false",
                                                                "messaging.health.enabled", "false")));
        try (Fixture fixture = new Fixture(config)) {
            List<HealthCheck> checks = fixture.checks();
            fixture.registry.get(ChannelMessagingTypes.Producer.class).emitChannelTwo("delivered");

            assertThat(fixture.registry.get(ChannelMessagingTypes.ChannelTwoConsumer.class).messages(), hasSize(1));
            assertThat(fixture.meters.meters(), empty());
            assertThat(checks.stream().map(check -> check.call().status()).toList(),
                       is(List.of(HealthCheckResponse.Status.UP, HealthCheckResponse.Status.UP)));
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        private final MeterRegistry meters = factory.createMeterRegistry(MetricsConfig.create());
        private final ServiceRegistryManager manager;
        private final ServiceRegistry registry;

        private Fixture() {
            this(Config.empty());
        }

        private Fixture(Config config) {
            // Each fixture owns an isolated application registry, including the generated graph's lifecycle.
            manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                    .putContractInstance(Config.class, config)
                    .putContractInstance(MeterRegistry.class, meters)
                    .build());
            registry = manager.registry();
        }

        @Override
        public void close() {
            try {
                manager.shutdown();
            } finally {
                factory.close();
            }
        }

        private void assertCounts(String channel, long attempts, long failures) {
            var tags = List.of(factory.tagCreate("channel", channel));
            assertThat(channel + " processing attempts", meters.timer(DURATION, tags).orElseThrow().count(), is(attempts));
            assertThat(channel + " processing failures", meters.counter(FAILURES, tags).orElseThrow().count(), is(failures));
        }

        private List<HealthCheck> checks() {
            return registry.all(HealthCheckProvider.class).stream()
                    .flatMap(provider -> provider.healthChecks(Config.empty()).stream())
                    .filter(check -> check.name().equals("messaging-ready") || check.name().equals("messaging-live"))
                    .toList();
        }
    }
}
