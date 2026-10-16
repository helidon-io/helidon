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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagingMetricsReplacementTest {
    private static final String DURATION = "messaging.processing.duration";
    private static final String FAILURES = "messaging.processing.failures";

    @Test
    void laterGraphUsesApplicationReplacementsInEitherCloseOrder() {
        for (boolean borrowedOriginal : List.of(false, true)) {
            for (boolean closeOriginalFirst : List.of(false, true)) {
                try (Environment environment = new Environment()) {
                    if (borrowedOriginal) {
                        environment.createTimer();
                        environment.createCounter();
                    }
                    MessagingChannel<String> originalChannel = MessagingChannel.create("orders", String.class);
                    MessagingChannel<String> laterChannel = MessagingChannel.create("orders", String.class);
                    try (MessagingGraph original = environment.graph(originalChannel)) {
                        assertThrows(BatchDeliveryException.class, () -> original.emitter(originalChannel).emit("fail"));
                        Timer originalTimer = environment.timer();
                        Counter originalCounter = environment.counter();
                        assertThat(originalTimer.count(), is(1L));
                        assertThat(originalCounter.count(), is(1L));
                        environment.remove(originalTimer, originalCounter);

                        Timer replacementTimer = environment.createTimer();
                        Counter replacementCounter = environment.createCounter();
                        assertThat(replacementTimer, not(sameInstance(originalTimer)));
                        assertThat(replacementCounter, not(sameInstance(originalCounter)));
                        try (MessagingGraph later = environment.graph(laterChannel)) {
                            assertThrows(BatchDeliveryException.class, () -> later.emitter(laterChannel).emit("fail"));
                            environment.assertRegistered(replacementTimer, replacementCounter);
                            assertThat(replacementTimer.count(), is(1L));
                            assertThat(replacementCounter.count(), is(1L));

                            if (closeOriginalFirst) {
                                original.close();
                                environment.assertRegistered(replacementTimer, replacementCounter);
                                assertThrows(BatchDeliveryException.class, () -> later.emitter(laterChannel).emit("fail"));
                                later.close();
                            } else {
                                later.close();
                                environment.assertRegistered(replacementTimer, replacementCounter);
                                original.close();
                            }
                        }
                        environment.assertRegistered(replacementTimer, replacementCounter);
                        long expectedCount = closeOriginalFirst ? 2 : 1;
                        assertThat(replacementTimer.count(), is(expectedCount));
                        assertThat(replacementCounter.count(), is(expectedCount));
                        replacementTimer.record(10, TimeUnit.NANOSECONDS);
                        replacementCounter.increment();
                        assertThat(replacementTimer.count(), is(expectedCount + 1));
                        assertThat(replacementCounter.count(), is(expectedCount + 1));
                    }
                }
            }
        }
    }

    @Test
    void closingOriginalGraphPreservesApplicationReplacementsWithoutLaterGraph() {
        for (boolean borrowedOriginal : List.of(false, true)) {
            try (Environment environment = new Environment()) {
                if (borrowedOriginal) {
                    environment.createTimer();
                    environment.createCounter();
                }
                MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
                try (MessagingGraph graph = environment.graph(channel)) {
                    environment.remove(environment.timer(), environment.counter());
                    Timer replacementTimer = environment.createTimer();
                    Counter replacementCounter = environment.createCounter();
                    replacementTimer.record(10, TimeUnit.NANOSECONDS);
                    replacementCounter.increment();

                    graph.close();

                    environment.assertRegistered(replacementTimer, replacementCounter);
                    assertThat(replacementTimer.count(), is(1L));
                    assertThat(replacementCounter.count(), is(1L));
                }
            }
        }
    }

    @Test
    void laterGraphOwnsRecreatedMetersInEitherCloseOrder() {
        for (boolean borrowedOriginal : List.of(false, true)) {
            for (boolean closeOriginalFirst : List.of(false, true)) {
                try (Environment environment = new Environment()) {
                    if (borrowedOriginal) {
                        environment.createTimer();
                        environment.createCounter();
                    }
                    MessagingChannel<String> originalChannel = MessagingChannel.create("orders", String.class);
                    MessagingChannel<String> laterChannel = MessagingChannel.create("orders", String.class);
                    try (MessagingGraph original = environment.graph(originalChannel)) {
                        Timer originalTimer = environment.timer();
                        Counter originalCounter = environment.counter();
                        environment.remove(originalTimer, originalCounter);

                        try (MessagingGraph later = environment.graph(laterChannel)) {
                            Timer replacementTimer = environment.timer();
                            Counter replacementCounter = environment.counter();
                            assertThat(replacementTimer, not(sameInstance(originalTimer)));
                            assertThat(replacementCounter, not(sameInstance(originalCounter)));
                            assertThrows(BatchDeliveryException.class, () -> later.emitter(laterChannel).emit("fail"));
                            assertThat(replacementTimer.count(), is(1L));
                            assertThat(replacementCounter.count(), is(1L));

                            if (closeOriginalFirst) {
                                original.close();
                                environment.assertRegistered(replacementTimer, replacementCounter);
                                assertThrows(BatchDeliveryException.class, () -> later.emitter(laterChannel).emit("fail"));
                                assertThat(replacementTimer.count(), is(2L));
                                assertThat(replacementCounter.count(), is(2L));
                                later.close();
                            } else {
                                later.close();
                                assertThat(environment.meters.meters(), empty());
                                original.close();
                            }
                            assertThat(environment.meters.isDeleted(replacementTimer), is(true));
                            assertThat(environment.meters.isDeleted(replacementCounter), is(true));
                        }
                        assertThat(environment.meters.meters(), empty());
                    }
                }
            }
        }
    }

    @Test
    void failedRecreationRollsBackOnlyTheNewMeters() {
        for (String failingMeter : List.of(DURATION, FAILURES)) {
            try (Environment environment = new Environment()) {
                MessagingChannel<String> originalChannel = MessagingChannel.create("orders", String.class);
                MessagingChannel<String> laterChannel = MessagingChannel.create("orders", String.class);
                try (MessagingGraph original = environment.graph(originalChannel)) {
                    environment.remove(environment.timer(), environment.counter());
                    AtomicBoolean failed = new AtomicBoolean();
                    environment.meters.onMeterAdded(meter -> {
                        if (meter.id().name().equals(failingMeter) && failed.compareAndSet(false, true)) {
                            throw new IllegalStateException("Listener failed after replacement registration");
                        }
                    });

                    try (MessagingGraph later = environment.graph(laterChannel)) {
                        later.emitter(laterChannel).emit("delivered");
                        assertThat(failed.get(), is(true));
                        assertThat(environment.meters.meters(), empty());
                    }

                    try (MessagingGraph replacement = environment.graph(laterChannel)) {
                        assertThrows(BatchDeliveryException.class, () -> replacement.emitter(laterChannel).emit("fail"));
                        Timer replacementTimer = environment.timer();
                        Counter replacementCounter = environment.counter();
                        assertThat(replacementTimer.count(), is(1L));
                        assertThat(replacementCounter.count(), is(1L));
                        original.close();
                        environment.assertRegistered(replacementTimer, replacementCounter);
                    }
                    assertThat(environment.meters.meters(), empty());
                }
            }
        }
    }

    private static final class Environment implements AutoCloseable {
        private final HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        private final MeterRegistry meters = factory.createMeterRegistry(MetricsConfig.create());
        private final List<Tag> tags = List.of(factory.tagCreate("channel", "orders"));
        private final ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .putContractInstance(MeterRegistry.class, meters)
                .build());

        @Override
        public void close() {
            try {
                manager.shutdown();
            } finally {
                factory.close();
            }
        }

        private MessagingGraph graph(MessagingChannel<String> channel) {
            return MessagingGraph.builder()
                    .serviceRegistry(manager.registry())
                    .channel(channel)
                    .payloadSink(channel, payload -> {
                        if (payload.equals("fail")) {
                            throw new IllegalStateException("Processing failed");
                        }
                    })
                    .build()
                    .start();
        }

        private Timer timer() {
            return meters.timer(DURATION, tags).orElseThrow();
        }

        private Counter counter() {
            return meters.counter(FAILURES, tags).orElseThrow();
        }

        private Timer createTimer() {
            return meters.getOrCreate(factory.timerBuilder(DURATION).tags(tags));
        }

        private Counter createCounter() {
            return meters.getOrCreate(factory.counterBuilder(FAILURES).tags(tags));
        }

        private void remove(Timer timer, Counter counter) {
            assertThat(meters.remove(timer).orElseThrow(), sameInstance(timer));
            assertThat(meters.remove(counter).orElseThrow(), sameInstance(counter));
            assertThat(meters.isDeleted(timer), is(true));
            assertThat(meters.isDeleted(counter), is(true));
            assertThat(meters.meters(), empty());
        }

        private void assertRegistered(Timer timer, Counter counter) {
            assertThat(timer(), sameInstance(timer));
            assertThat(counter(), sameInstance(counter));
            assertThat(meters.isDeleted(timer), is(false));
            assertThat(meters.isDeleted(counter), is(false));
        }
    }
}
