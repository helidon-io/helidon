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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(10)
class MessagingMetricsFailureTest {
    private static final String DURATION = "messaging.processing.duration";
    private static final String FAILURES = "messaging.processing.failures";

    @Test
    void fatalCounterRegistrationReleasesThePreviouslyRegisteredTimer() {
        assertFatalRegistrationCleanup(true);
    }

    @Test
    void fatalCounterListenerReleasesBothInsertedMeters() {
        assertFatalRegistrationCleanup(false);
    }

    private static void assertFatalRegistrationCleanup(boolean beforeInsertion) {
        try (Environment environment = new Environment(beforeInsertion)) {
            AssertionError thrown = assertThrows(AssertionError.class, environment::graph);

            assertThat(thrown, sameInstance(environment.failure));
            assertThat(environment.additions, hasSize(beforeInsertion ? 1 : 2));
            assertThat(environment.additions.getFirst().id().name(), is(DURATION));
            assertThat(environment.meters.meters(), empty());
            for (Meter meter : environment.additions) {
                assertThat(environment.meters.isDeleted(meter), is(true));
            }

            try (MessagingGraph graph = environment.graph().start()) {
                graph.emitter(environment.channel).emit("delivered");
                assertThat(environment.meters.timer(DURATION, environment.tags).orElseThrow().count(), is(1L));
                assertThat(environment.meters.counter(FAILURES, environment.tags).orElseThrow().count(), is(0L));
            }
            assertThat(environment.meters.meters(), empty());
        }
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static final class Environment implements AutoCloseable {
        private final HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        private final MeterRegistry meters = factory.createMeterRegistry(MetricsConfig.create());
        private final List<Tag> tags = List.of(factory.tagCreate("channel", "orders"));
        private final List<Meter> additions = new ArrayList<>();
        private final AtomicBoolean fail = new AtomicBoolean(true);
        private final AssertionError failure = new AssertionError("Fatal counter registration failure");
        private final MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);
        private final ServiceRegistryManager manager;

        private Environment(boolean beforeInsertion) {
            meters.onMeterAdded(meter -> {
                additions.add(meter);
                if (!beforeInsertion && meter.id().name().equals(FAILURES) && fail.compareAndSet(true, false)) {
                    throw failure;
                }
            });
            MeterRegistry selected = beforeInsertion
                    ? (MeterRegistry) Proxy.newProxyInstance(MeterRegistry.class.getClassLoader(),
                                                            new Class<?>[] {MeterRegistry.class},
                                                            (_, method, args) -> {
                                                                if (method.getName().equals("getOrCreate")
                                                                        && args[0] instanceof Counter.Builder builder
                                                                        && builder.name().equals(FAILURES)
                                                                        && fail.compareAndSet(true, false)) {
                                                                    throw failure;
                                                                }
                                                                return invoke(method, meters, args);
                                                            })
                    : meters;
            manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                    .discoverServices(false)
                    .discoverServicesFromServiceLoader(false)
                    .putContractInstance(MeterRegistry.class, selected)
                    .build());
        }

        @Override
        public void close() {
            try {
                manager.shutdown();
            } finally {
                factory.close();
            }
        }

        private MessagingGraph graph() {
            return MessagingGraph.builder()
                    .serviceRegistry(manager.registry())
                    .enableHealth(false)
                    .channel(channel)
                    .payloadSink(channel, _ -> { })
                    .build();
        }
    }
}
