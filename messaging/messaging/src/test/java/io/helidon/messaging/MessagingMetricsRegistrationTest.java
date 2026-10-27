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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
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
import static org.hamcrest.Matchers.hasSize;

@Timeout(60)
class MessagingMetricsRegistrationTest {
    private static final String DURATION = "messaging.processing.duration";

    @Test
    void applicationRegistrationRacingGraphConstructionSharesTheSameSeries() throws Exception {
        // Pause graph registration, create the timer in the application, then resume and deliver once.
        // Closing the graph must preserve the application timer and its recorded count.
        try (Environment environment = new Environment();
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            environment.registry.pauseRegistration.set(true);
            var building = executor.submit(() -> environment.graph(1));
            try {
                await(environment.registry.entered);
                Timer application = environment.createTimer();
                environment.registry.resume.countDown();
                try (MessagingGraph graph = building.get(30, TimeUnit.SECONDS).start()) {
                    graph.emitter(environment.channel).emit("delivered");
                    assertThat(application.count(), is(1L));
                }
                environment.assertApplicationTimer(application);
            } finally {
                environment.registry.resume.countDown();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 32, 128})
    void graphConstructionDoesNotEnumerateUnrelatedMeters(int channelCount) {
        // Registration cost must depend on channel registrations, not repeated snapshots of every application series.
        try (Environment environment = new Environment()) {
            for (int i = 0; i < 256; i++) {
                environment.nativeRegistry.getOrCreate(environment.factory.counterBuilder("application." + i));
            }
            try (MessagingGraph _ = environment.graph(channelCount)) {
                assertThat(environment.registry.enumerations.get(), is(0));
                assertThat(environment.nativeRegistry.meters(), hasSize(256 + 2 * channelCount));
            }
            assertThat(environment.registry.enumerations.get(), is(0));
            assertThat(environment.nativeRegistry.meters(), hasSize(256 + 2 * channelCount));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat("The coordinated provider operation must complete", latch.await(30, TimeUnit.SECONDS), is(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static final class InterleavingRegistry extends DelegatingMeterRegistry {
        private final AtomicBoolean pauseRegistration = new AtomicBoolean();
        private final AtomicInteger enumerations = new AtomicInteger();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);

        private InterleavingRegistry(MeterRegistry delegate) {
            super(delegate);
        }

        @Override
        public List<Meter> meters() {
            enumerations.incrementAndGet();
            return super.meters();
        }

        @Override
        public <B extends Meter.Builder<B, M>, M extends Meter> M getOrCreate(B builder) {
            if (builder.name().equals(DURATION) && pauseRegistration.compareAndSet(true, false)) {
                entered.countDown();
                await(resume);
            }
            return super.getOrCreate(builder);
        }
    }

    private static final class Environment implements AutoCloseable {
        private final HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        private final MeterRegistry nativeRegistry = factory.createMeterRegistry(MetricsConfig.create());
        private final InterleavingRegistry registry = new InterleavingRegistry(nativeRegistry);
        private final List<Tag> tags = List.of(factory.tagCreate("channel", "orders"));
        private final MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
        // Graphs borrow this application registry; the fixture closes its services and native provider together.
        private final ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .putContractInstance(MeterRegistry.class, registry)
                .build());

        @Override
        public void close() {
            try {
                manager.shutdown();
            } finally {
                factory.close();
            }
        }

        private MessagingGraph graph(int channelCount) {
            var config = MessagingGraph.builder().health(health -> health.enabled(false))
                    .channel(channel).payloadSink(channel, _ -> { });
            for (int i = 1; i < channelCount; i++) {
                MessagingChannel<String> additional = MessagingChannel.create("channel-" + i, String.class);
                config.channel(additional).payloadSink(additional, _ -> { });
            }
            return new MessagingGraphAssembler(config.buildPrototype(), manager.registry()).graph();
        }

        private Timer createTimer() {
            return registry.getOrCreate(factory.timerBuilder(DURATION).tags(tags));
        }

        private void assertApplicationTimer(Timer timer) {
            assertThat(nativeRegistry.timer(DURATION, tags).orElseThrow(), sameInstance(timer));
            assertThat(nativeRegistry.isDeleted(timer), is(false));
            assertThat(timer.count(), is(1L));
            assertThat(nativeRegistry.meters(), hasSize(2));
        }
    }
}
