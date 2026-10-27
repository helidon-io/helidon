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

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagingMetricsReplacementTest {
    private static final String DURATION = "messaging.processing.duration";
    private static final String FAILURES = "messaging.processing.failures";

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void laterGraphRecordsIntoReplacementSeries(boolean applicationCreatesReplacement, boolean closeOriginalFirst) {
        // The application removes the first graph's series. A new graph must use the replacement series,
        // whether the application or the new graph creates them, and either graph-close order must preserve them.
        try (Environment environment = new Environment()) {
            MessagingChannel<String> originalChannel = MessagingChannel.create("orders", String.class);
            MessagingChannel<String> laterChannel = MessagingChannel.create("orders", String.class);
            try (MessagingGraph original = environment.graph(originalChannel)) {
                assertThrows(BatchDeliveryException.class, () -> original.emitter(originalChannel).emit("fail"));
                Timer originalTimer = environment.timer();
                Counter originalCounter = environment.counter();
                assertThat(originalTimer.count(), is(1L));
                assertThat(originalCounter.count(), is(1L));
                environment.meters.remove(originalTimer);
                environment.meters.remove(originalCounter);

                if (applicationCreatesReplacement) {
                    environment.meters.getOrCreate(environment.factory.timerBuilder(DURATION).tags(environment.tags));
                    environment.meters.getOrCreate(environment.factory.counterBuilder(FAILURES).tags(environment.tags));
                }
                try (MessagingGraph later = environment.graph(laterChannel)) {
                    assertThat(environment.timer().count(), is(0L));
                    assertThat(environment.counter().count(), is(0L));
                    assertThrows(BatchDeliveryException.class, () -> later.emitter(laterChannel).emit("fail"));
                    if (closeOriginalFirst) {
                        original.close();
                        assertThrows(BatchDeliveryException.class, () -> later.emitter(laterChannel).emit("fail"));
                    } else {
                        later.close();
                        original.close();
                    }
                }

                long expected = closeOriginalFirst ? 2 : 1;
                assertThat(environment.timer().count(), is(expected));
                assertThat(environment.counter().count(), is(expected));
                assertThat(originalTimer.count(), is(1L));
                assertThat(originalCounter.count(), is(1L));
                try (MessagingGraph recreated = environment.graph(laterChannel)) {
                    recreated.emitter(laterChannel).emit("success");
                    assertThat(environment.timer().count(), is(expected + 1));
                    assertThat(environment.counter().count(), is(expected));
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
            return MessagingGraph.create(MessagingGraph.builder()
                    .serviceRegistry(manager.registry())
                    .channel(channel)
                    .payloadSink(channel, payload -> {
                        if (payload.equals("fail")) {
                            throw new IllegalStateException("Processing failed");
                        }
                    })
                    .buildPrototype(), manager.registry())
                    .start();
        }

        private Timer timer() {
            return meters.timer(DURATION, tags).orElseThrow();
        }

        private Counter counter() {
            return meters.counter(FAILURES, tags).orElseThrow();
        }
    }
}
