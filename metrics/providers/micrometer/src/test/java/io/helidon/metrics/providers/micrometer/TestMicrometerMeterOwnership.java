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

package io.helidon.metrics.providers.micrometer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.spi.MeterBuilderCustomizer;

import io.micrometer.core.instrument.config.MeterFilter;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestMicrometerMeterOwnership {
    @Test
    void notifiesOnlyTheCreatingInvocation() {
        // Returning an existing meter must not notify a later caller that it owns the registration.
        try (var fixture = new Fixture(List.of())) {
            List<Meter> created = new ArrayList<>();
            Counter first = fixture.registry.getOrCreate(fixture.factory.counterBuilder("owned"), created::add);
            Counter second = fixture.registry.getOrCreate(fixture.factory.counterBuilder("owned"), created::add);

            assertThat(second, sameInstance(first));
            assertThat(created, contains(first));
        }
    }

    @Test
    void exposesCreatedInstanceBeforeListenerFailure() {
        // The native add listener can fail after the Helidon wrapper exists but before native insertion completes.
        try (var fixture = new Fixture(List.of())) {
            AtomicReference<Counter> created = new AtomicReference<>();
            AssertionError expected = new AssertionError("add-listener");
            AtomicInteger additions = new AtomicInteger();
            fixture.registry.onMeterAdded(_ -> {
                if (additions.getAndIncrement() == 0) {
                    throw expected;
                }
            });

            assertThat(assertThrows(AssertionError.class,
                                    () -> fixture.registry.getOrCreate(fixture.factory.counterBuilder("failed-add"),
                                                                       created::set)),
                       sameInstance(expected));
            assertThat(fixture.registry.meters(), contains(created.get()));
            assertThat(fixture.registry.remove(created.get()).orElseThrow(), sameInstance(created.get()));
            assertThat(fixture.registry.isDeleted(created.get()), is(true));
            assertThat(fixture.registry.meters(), empty());
            assertThat(fixture.registry.counter("failed-add", List.of()).isEmpty(), is(true));
            Counter replacement = fixture.registry.getOrCreate(fixture.factory.counterBuilder("failed-add"));
            assertThat(fixture.registry.counter("failed-add", List.of()).orElseThrow(), sameInstance(replacement));
        }
    }

    @Test
    void staleHandleCannotRemoveReplacement() {
        // Instance removal protects a replacement; ID removal still intentionally targets whichever instance is current.
        try (var fixture = new Fixture(List.of())) {
            Counter original = fixture.registry.getOrCreate(fixture.factory.counterBuilder("replace"));
            fixture.registry.remove(original).orElseThrow();
            Counter replacement = fixture.registry.getOrCreate(fixture.factory.counterBuilder("replace"));

            assertThat(fixture.registry.remove(original).isEmpty(), is(true));
            assertThat(fixture.registry.counter("replace", List.of()).orElseThrow(), sameInstance(replacement));
            assertThat(fixture.registry.remove(original.id()).orElseThrow(), sameInstance(replacement));
        }
    }

    @Test
    void removalListenerReplacementSurvivesOriginalRemoval() {
        // A removal listener registers the same ID reentrantly; finishing the old removal must preserve that new instance.
        try (var fixture = new Fixture(List.of())) {
            Counter original = fixture.registry.getOrCreate(fixture.factory.counterBuilder("listener-replace"));
            AtomicReference<Counter> replacement = new AtomicReference<>();
            fixture.registry.onMeterRemoved(meter -> {
                if (meter == original) {
                    replacement.set(fixture.registry.getOrCreate(fixture.factory.counterBuilder("listener-replace")));
                }
            });

            assertThat(fixture.registry.remove(original).orElseThrow(), sameInstance(original));
            assertThat(fixture.registry.counter("listener-replace", List.of()).orElseThrow(), sameInstance(replacement.get()));
            assertThat(fixture.registry.remove(original).isEmpty(), is(true));
            assertThat(fixture.registry.isDeleted(replacement.get()), is(false));
        }
    }

    @Test
    void concurrentApplicationRegistrationIsBorrowed() throws Exception {
        // Pause one caller before insertion, allowing another caller to win creation of the same customized identity.
        CountDownLatch customizing = new CountDownLatch(1);
        CountDownLatch continueRegistration = new CountDownLatch(1);
        AtomicReference<Thread> delayedThread = new AtomicReference<>();
        MeterBuilderCustomizer customizer = _ -> {
            if (Thread.currentThread() == delayedThread.get()) {
                customizing.countDown();
                await(continueRegistration);
            }
        };
        try (var fixture = new Fixture(List.of(customizer));
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            AtomicInteger creations = new AtomicInteger();
            var future = executor.submit(() -> {
                delayedThread.set(Thread.currentThread());
                return fixture.registry.getOrCreate(fixture.factory.counterBuilder("race"),
                                                    _ -> creations.incrementAndGet());
            });
            try {
                assertThat(customizing.await(5, TimeUnit.SECONDS), is(true));
                Counter application = fixture.registry.getOrCreate(fixture.factory.counterBuilder("race"));
                continueRegistration.countDown();
                assertThat(future.get(5, TimeUnit.SECONDS), sameInstance(application));
                assertThat(creations.get(), is(0));
            } finally {
                continueRegistration.countDown();
            }
        }
    }

    @Test
    void customizedIdentityIsBorrowedOnSubsequentRegistration() {
        // Rewriting both channel tags to one value makes distinct requests resolve to the same native meter.
        MeterBuilderCustomizer customizer = builder -> builder.addTag(MTag.of("channel", "shared"));
        try (var fixture = new Fixture(List.of(customizer))) {
            AtomicInteger creations = new AtomicInteger();
            Counter first = fixture.registry.getOrCreate(fixture.factory.counterBuilder("custom")
                                                                .addTag(fixture.factory.tagCreate("channel", "first")),
                                                        _ -> creations.incrementAndGet());
            Counter second = fixture.registry.getOrCreate(fixture.factory.counterBuilder("custom")
                                                                 .addTag(fixture.factory.tagCreate("channel", "second")),
                                                         _ -> creations.incrementAndGet());

            assertThat(second, sameInstance(first));
            assertThat(creations.get(), is(1));
        }
    }

    @Test
    void nestedApplicationRegistrationDoesNotNotifyOuterInvocation() {
        // A general listener creates a second meter; only the outer meter belongs to the outer creation callback.
        try (var fixture = new Fixture(List.of())) {
            List<String> created = new ArrayList<>();
            fixture.registry.onMeterAdded(meter -> {
                if (meter.id().name().equals("outer")) {
                    fixture.registry.getOrCreate(fixture.factory.counterBuilder("inner"));
                }
            });
            fixture.registry.getOrCreate(fixture.factory.counterBuilder("outer"), meter -> created.add(meter.id().name()));

            assertThat(created, contains("outer"));
            assertThat(fixture.registry.counter("inner", List.of()).isPresent(), is(true));
        }
    }

    @Test
    void reentrantNativeRegistrationIsBorrowed() {
        // A native filter creates the requested meter reentrantly, before the outer Helidon registration can own it.
        try (var fixture = new Fixture(List.of())) {
            var nativeRegistry = fixture.registry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
            AtomicBoolean registerApplicationMeter = new AtomicBoolean(true);
            AtomicReference<io.micrometer.core.instrument.Counter> applicationMeter = new AtomicReference<>();
            nativeRegistry.config().meterFilter(new MeterFilter() {
                @Override
                public io.micrometer.core.instrument.Meter.Id map(io.micrometer.core.instrument.Meter.Id id) {
                    if (id.getName().equals("native-reentry") && registerApplicationMeter.getAndSet(false)) {
                        applicationMeter.set(nativeRegistry.counter(id.getName(), id.getTags()));
                    }
                    return id;
                }
            });
            AtomicInteger creations = new AtomicInteger();
            Counter borrowed = fixture.registry.getOrCreate(fixture.factory.counterBuilder("native-reentry"),
                                                           _ -> creations.incrementAndGet());

            assertThat(borrowed.unwrap(io.micrometer.core.instrument.Counter.class), sameInstance(applicationMeter.get()));
            assertThat(creations.get(), is(0));
        }
    }

    @Test
    void nativeIdentityTransformationConservativelyOmitsOwnership() {
        // A native filter changes the ID, so the adapter cannot prove which registration created the returned instance.
        try (var fixture = new Fixture(List.of())) {
            var nativeRegistry = fixture.registry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
            nativeRegistry.config().meterFilter(new MeterFilter() {
                @Override
                public io.micrometer.core.instrument.Meter.Id map(io.micrometer.core.instrument.Meter.Id id) {
                    return id.withName(id.getName() + ".mapped");
                }
            });
            AtomicInteger creations = new AtomicInteger();
            Counter borrowed = fixture.registry.getOrCreate(fixture.factory.counterBuilder("native-map"),
                                                           _ -> creations.incrementAndGet());

            assertThat(borrowed.id().name(), is("native-map.mapped"));
            assertThat(creations.get(), is(0));
        }
    }

    @Test
    void staleRemovalCannotDeleteConcurrentReplacement() throws Exception {
        // Hold the replacement's add listener while another thread requests removal using the retired instance.
        CountDownLatch replacementAdded = new CountDownLatch(1);
        CountDownLatch releaseListener = new CountDownLatch(1);
        CountDownLatch removalStarted = new CountDownLatch(1);
        try (var fixture = new Fixture(List.of());
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Counter original = fixture.registry.getOrCreate(fixture.factory.counterBuilder("replace-race"));
            fixture.registry.remove(original).orElseThrow();
            fixture.registry.onMeterAdded(_ -> {
                replacementAdded.countDown();
                await(releaseListener);
            });
            var addition = executor.submit(() -> fixture.registry.getOrCreate(fixture.factory.counterBuilder("replace-race")));
            try {
                assertThat(replacementAdded.await(5, TimeUnit.SECONDS), is(true));
                var removal = executor.submit(() -> {
                    removalStarted.countDown();
                    return fixture.registry.remove(original);
                });
                assertThat(removalStarted.await(5, TimeUnit.SECONDS), is(true));
                // Let registration finish before removal checks the stale handle against the current instance.
                releaseListener.countDown();
                Counter replacement = addition.get(5, TimeUnit.SECONDS);
                assertThat(removal.get(5, TimeUnit.SECONDS).isEmpty(), is(true));
                assertThat(fixture.registry.counter("replace-race", List.of()).orElseThrow(), sameInstance(replacement));
            } finally {
                releaseListener.countDown();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS), is(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final MicrometerMetricsFactory factory;
        private final MeterRegistry registry;

        private Fixture(List<MeterBuilderCustomizer> customizers) {
            factory = MicrometerMetricsFactory.create(MetricsConfig.create(),
                                                      List.of(),
                                                      customizers,
                                                      List.of(),
                                                      new NoOpSpanContextSupplierProvider());
            registry = factory.globalRegistry();
        }

        @Override
        public void close() {
            factory.close();
        }
    }
}
