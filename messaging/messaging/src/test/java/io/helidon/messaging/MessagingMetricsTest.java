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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import io.helidon.metrics.api.Clock;
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
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

class MessagingMetricsTest {
    private static final String DURATION = "messaging.processing.duration";
    private static final String FAILURES = "messaging.processing.failures";

    @Test
    void recordsCompletedAttemptsUsingOwningClock() {
        try (Environment environment = new Environment(MetricsConfig.create())) {
            MessagingMetrics metrics = environment.observe(environment.registry);
            MessagingMetrics.Recorder recorder = metrics.channel("orders");

            var started = recorder.start();
            environment.clock.advance(15);
            recorder.finish(started, false);
            started = recorder.start();
            environment.clock.advance(25);
            recorder.finish(started, true);

            Timer timer = environment.timer("orders");
            assertThat(timer.count(), is(2L));
            assertThat(timer.totalTime(TimeUnit.NANOSECONDS), is(40D));
            assertThat(environment.counter("orders").count(), is(1L));
            assertThat(metrics.channel("orders"), sameInstance(recorder));
            metrics.close();
            assertThat(environment.registry.meters(), empty());
        }
    }

    @Test
    void sharesMetersAcrossServiceRegistriesAndRetiresEachOwner() {
        try (Environment environment = new Environment(MetricsConfig.create())) {
            MessagingMetrics first = environment.observe(environment.registry);
            MessagingMetrics second = environment.observe(environment.registry);
            MessagingMetrics.Recorder retired = first.channel("orders");
            MessagingMetrics.Recorder surviving = second.channel("orders");
            retired.finish(retired.start(), false);
            var late = retired.start();
            first.close();

            retired.finish(late, true);
            surviving.finish(surviving.start(), true);
            assertThat(environment.timer("orders").count(), is(2L));
            assertThat(environment.counter("orders").count(), is(1L));
            first.close();
            assertThat(environment.registry.meters().size(), is(2));
            second.close();
            assertThat(environment.registry.meters(), empty());

            MessagingMetrics replacement = environment.observe(environment.registry);
            MessagingMetrics.Recorder current = replacement.channel("orders");
            retired.finish(late, true);
            surviving.finish(late, true);
            current.finish(current.start(), false);
            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.counter("orders").count(), is(0L));
        }
    }

    @Test
    void separateMeterRegistriesRemainIndependent() {
        try (Environment first = new Environment(MetricsConfig.create());
             Environment second = new Environment(MetricsConfig.create())) {
            MessagingMetrics firstMetrics = first.observe(first.registry);
            MessagingMetrics.Recorder firstRecorder = firstMetrics.channel("orders");
            MessagingMetrics.Recorder secondRecorder = second.observe(second.registry).channel("orders");
            firstRecorder.finish(firstRecorder.start(), true);
            secondRecorder.finish(secondRecorder.start(), false);
            firstMetrics.close();

            assertThat(first.registry.meters(), empty());
            assertThat(second.timer("orders").count(), is(1L));
            assertThat(second.counter("orders").count(), is(0L));
        }
    }

    @Test
    void registrationListenerCannotCreateCompetingOwnership() {
        try (Environment environment = new Environment(MetricsConfig.create())) {
            AtomicBoolean entered = new AtomicBoolean();
            AtomicReference<MessagingMetrics> nested = new AtomicReference<>();
            environment.registry.onMeterAdded(_ -> {
                if (entered.compareAndSet(false, true)) {
                    MessagingMetrics metrics = environment.observe(environment.registry);
                    nested.set(metrics);
                    MessagingMetrics.Recorder recorder = metrics.channel("orders");
                    recorder.finish(recorder.start(), true);
                }
            });

            MessagingMetrics outer = environment.observe(environment.registry);
            MessagingMetrics.Recorder recorder = outer.channel("orders");
            nested.get().close();
            recorder.finish(recorder.start(), false);
            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.counter("orders").count(), is(0L));
            outer.close();
            assertThat(environment.registry.meters(), empty());
        }
    }

    @Test
    void removalListenerCannotAcquireRetiringGeneration() {
        try (Environment environment = new Environment(MetricsConfig.create())) {
            MessagingMetrics outer = environment.observe(environment.registry);
            outer.channel("orders");
            AtomicBoolean entered = new AtomicBoolean();
            AtomicReference<MessagingMetrics> nested = new AtomicReference<>();
            environment.registry.onMeterRemoved(_ -> {
                if (entered.compareAndSet(false, true)) {
                    MessagingMetrics metrics = environment.observe(environment.registry);
                    nested.set(metrics);
                    metrics.channel("orders");
                }
            });

            outer.close();
            assertThat(environment.registry.meters(), empty());
            MessagingMetrics replacement = environment.observe(environment.registry);
            MessagingMetrics.Recorder current = replacement.channel("orders");
            nested.get().close();
            current.finish(current.start(), false);
            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.counter("orders").count(), is(0L));
            replacement.close();
            assertThat(environment.registry.meters(), empty());
        }
    }

    @Test
    void registrationListenerCanCloseAnotherChannelOwner() {
        try (Environment environment = new Environment(MetricsConfig.create())) {
            MessagingMetrics previous = environment.observe(environment.registry);
            previous.channel("previous");
            AtomicBoolean entered = new AtomicBoolean();
            environment.registry.onMeterAdded(_ -> {
                if (entered.compareAndSet(false, true)) {
                    previous.close();
                }
            });

            MessagingMetrics next = environment.observe(environment.registry);
            MessagingMetrics.Recorder recorder = next.channel("orders");
            recorder.finish(recorder.start(), false);
            assertThat(environment.timer("orders").count(), is(1L));
            next.close();
            assertThat(environment.registry.meters(), empty());
        }
    }

    @Test
    void disabledMetricsNeedNoRegistryOrClock() {
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                              .discoverServices(false)
                                                                              .build());
        try {
            MessagingMetrics metrics = MessagingMetrics.create(MessagingConfig.builder()
                                                                       .serviceRegistry(manager.registry())
                                                                       .enableMetrics(false)
                                                                       .buildPrototype());
            MessagingMetrics.Recorder recorder = metrics.channel("orders");
            recorder.finish(recorder.start(), true);
            metrics.close();
            assertThat(metrics.channel("other"), sameInstance(recorder));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void registryWithoutMetricsServiceUsesNoopRecorder() {
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                              .discoverServices(false)
                                                                              .build());
        try {
            MessagingMetrics metrics = MessagingMetrics.create(MessagingConfig.builder()
                                                                       .serviceRegistry(manager.registry())
                                                                       .enableMetrics(true)
                                                                       .buildPrototype());
            MessagingMetrics.Recorder recorder = metrics.channel("orders");
            recorder.finish(recorder.start(), true);
            metrics.close();
            assertThat(metrics.channel("other"), sameInstance(recorder));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void globalDisableAvoidsClockAndRegistration() {
        try (Environment environment = new Environment(MetricsConfig.builder().enabled(false).build())) {
            RegistryHandler handler = new RegistryHandler(environment.registry);
            MessagingMetrics.Recorder recorder = environment.observe(handler.proxy).channel("orders");
            recorder.finish(recorder.start(), true);
            assertThat(environment.registry.meters(), empty());
            assertThat(handler.clockRequests.get(), is(0));
        }
    }

    @Test
    void disabledTimerDoesNotReadClockAndFailureCounterRemainsIndependent() {
        MetricsConfig config = MetricsConfig.builder()
                .addMeter(builder -> builder.namePattern(Pattern.compile(Pattern.quote(DURATION))).enabled(false))
                .build();
        try (Environment environment = new Environment(config)) {
            RegistryHandler handler = new RegistryHandler(environment.registry);
            MessagingMetrics.Recorder recorder = environment.observe(handler.proxy).channel("orders");
            int clockReads = environment.clock.reads.get();
            recorder.finish(recorder.start(), true);

            assertThat(environment.registry.timer(DURATION, environment.tags("orders")).isEmpty(), is(true));
            assertThat(environment.counter("orders").count(), is(1L));
            assertThat(handler.clockRequests.get(), is(0));
            assertThat(environment.clock.reads.get(), is(clockReads));
        }
    }

    @Test
    void disabledFailureCounterPreservesProcessingCount() {
        MetricsConfig config = MetricsConfig.builder()
                .addMeter(builder -> builder.namePattern(Pattern.compile(Pattern.quote(FAILURES))).enabled(false))
                .build();
        try (Environment environment = new Environment(config)) {
            MessagingMetrics.Recorder recorder = environment.observe(environment.registry).channel("orders");
            recorder.finish(recorder.start(), true);

            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.registry.counter(FAILURES, environment.tags("orders")).isEmpty(), is(true));
        }
    }

    @Test
    void tagFiltersApplyBeforeAcquiringMeters() {
        try (Environment environment = new Environment(MetricsConfig.create())) {
            RegistryHandler handler = new RegistryHandler(environment.registry);
            handler.filteredChannel = "ignored";
            MessagingMetrics metrics = environment.observe(handler.proxy);
            MessagingMetrics.Recorder ignored = metrics.channel("ignored");
            ignored.finish(ignored.start(), true);
            assertThat(environment.registry.meters(), empty());
            assertThat(handler.clockRequests.get(), is(0));

            MessagingMetrics.Recorder included = metrics.channel("orders");
            included.finish(included.start(), true);
            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.counter("orders").count(), is(1L));
        }
    }

    @Test
    void partialRegistrationFailureRollsBackAndDoesNotPreventLaterAcquisition() {
        try (Environment environment = new Environment(MetricsConfig.create())) {
            RegistryHandler handler = new RegistryHandler(environment.registry);
            handler.fault = Fault.REGISTER;
            MessagingMetrics first = environment.observe(handler.proxy);
            MessagingMetrics.Recorder failed = first.channel("orders");
            failed.finish(failed.start(), true);
            assertThat(environment.registry.meters(), empty());

            handler.fault = Fault.NONE;
            MessagingMetrics.Recorder subsequent = environment.observe(handler.proxy).channel("orders");
            first.close();
            subsequent.finish(subsequent.start(), false);
            assertThat(environment.timer("orders").count(), is(1L));
        }
    }

    @Test
    void throwingAddListenerRemovesOnlyAttemptedIdentitiesWithGlobalTags() {
        for (String failingMeter : List.of(DURATION, FAILURES)) {
            HelidonMetricsFactory factory = HelidonMetricsFactory.create();
            MetricsConfig config = MetricsConfig.builder()
                    .appName("messaging-tests")
                    .tags(List.of(factory.tagCreate("region", "test")))
                    .build();
            try (Environment environment = new Environment(config, factory)) {
                MessagingMetrics existing = environment.observe(environment.registry);
                existing.channel("payments");
                AtomicBoolean failed = new AtomicBoolean();
                environment.registry.onMeterAdded(meter -> {
                    if (meter.id().name().equals(failingMeter) && failed.compareAndSet(false, true)) {
                        throw new IllegalStateException("Listener failed after registration");
                    }
                });

                MessagingMetrics broken = environment.observe(environment.registry);
                broken.channel("orders");
                assertThat(failingMeter, environment.registry.meters().size(), is(2));
                assertThat(environment.registry.timer(DURATION, environment.tags("orders")).isEmpty(), is(true));
                assertThat(environment.registry.counter(FAILURES, environment.tags("orders")).isEmpty(), is(true));

                MessagingMetrics replacement = environment.observe(environment.registry);
                MessagingMetrics.Recorder recorder = replacement.channel("orders");
                recorder.finish(recorder.start(), false);
                assertThat(environment.timer("orders").count(), is(1L));
                broken.close();
                replacement.close();
                assertThat(environment.registry.meters().size(), is(2));
                existing.close();
                assertThat(environment.registry.meters(), empty());
            }
        }
    }

    @Test
    void throwingAddListenerCleanupUsesCustomizerTags() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create(builder -> builder
                .addMeterBuilderCustomizer(meter -> meter.tags(List.of())));
        try (Environment environment = new Environment(MetricsConfig.create(), factory)) {
            environment.registry.onMeterAdded(_ -> {
                throw new IllegalStateException("Listener failed after customized registration");
            });
            environment.observe(environment.registry).channel("orders");
            assertThat(environment.registry.meters(), empty());
        }
    }

    @Test
    void recordingFailureDisablesOnlyAffectedRecorder() {
        try (Environment environment = new Environment(MetricsConfig.create())) {
            RegistryHandler handler = new RegistryHandler(environment.registry);
            MessagingMetrics first = environment.observe(handler.proxy);
            MessagingMetrics.Recorder broken = first.channel("orders");
            MessagingMetrics.Recorder surviving = environment.observe(handler.proxy).channel("orders");
            handler.fault = Fault.RECORD;
            broken.finish(broken.start(), true);
            handler.fault = Fault.NONE;
            broken.finish(broken.start(), true);
            surviving.finish(surviving.start(), false);

            assertThat(environment.timer("orders").count(), is(1L));
            assertThat(environment.counter("orders").count(), is(0L));
        }
    }

    @Test
    void clockFailureDisablesRecorderAndStillReleasesMeters() {
        try (Environment environment = new Environment(MetricsConfig.create())) {
            MessagingMetrics metrics = environment.observe(environment.registry);
            MessagingMetrics.Recorder recorder = metrics.channel("orders");
            environment.clock.fail = true;
            recorder.finish(recorder.start(), true);
            environment.clock.fail = false;
            recorder.finish(recorder.start(), true);
            assertThat(environment.timer("orders").count(), is(0L));
            metrics.close();
            assertThat(environment.registry.meters(), empty());
        }
    }

    @Test
    void removalFailureStillRetiresLeaseAndAttemptsBothMeters() {
        try (Environment environment = new Environment(MetricsConfig.create())) {
            RegistryHandler handler = new RegistryHandler(environment.registry);
            MessagingMetrics metrics = environment.observe(handler.proxy);
            MessagingMetrics.Recorder recorder = metrics.channel("orders");
            var started = recorder.start();
            Timer retainedTimer = environment.timer("orders");
            Counter retainedCounter = environment.counter("orders");
            handler.fault = Fault.REMOVE;
            metrics.close();
            assertThat(handler.removals.get(), is(2));
            recorder.finish(started, true);
            assertThat(environment.timer("orders").count(), is(0L));

            handler.fault = Fault.NONE;
            MessagingMetrics replacement = environment.observe(handler.proxy);
            MessagingMetrics.Recorder current = replacement.channel("orders");
            current.finish(current.start(), false);
            replacement.close();
            // Failed cleanup released internal ownership; a later graph borrows the pre-existing series.
            assertThat(environment.timer("orders"), sameInstance(retainedTimer));
            assertThat(environment.counter("orders"), sameInstance(retainedCounter));
            assertThat(retainedTimer.count(), is(1L));
            assertThat(retainedCounter.count(), is(0L));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private enum Fault {
        NONE,
        REGISTER,
        RECORD,
        REMOVE
    }

    private static final class Environment implements AutoCloseable {
        private final TestClock clock = new TestClock();
        private final HelidonMetricsFactory factory;
        private final List<ServiceRegistryManager> managers = new ArrayList<>();
        private final List<MessagingMetrics> observations = new ArrayList<>();
        private final MeterRegistry registry;

        private Environment(MetricsConfig config) {
            this(config, HelidonMetricsFactory.create());
        }

        private Environment(MetricsConfig config, HelidonMetricsFactory factory) {
            this.factory = factory;
            registry = factory.createMeterRegistry(clock, config);
        }

        @Override
        public void close() {
            observations.forEach(MessagingMetrics::close);
            managers.forEach(ServiceRegistryManager::shutdown);
            factory.close();
        }

        private MessagingMetrics observe(MeterRegistry meterRegistry) {
            ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                  .discoverServices(false)
                                                                                  .putContractInstance(MeterRegistry.class,
                                                                                                       meterRegistry)
                                                                                  .build());
            managers.add(manager);
            MessagingMetrics metrics = MessagingMetrics.create(MessagingConfig.builder()
                                                                       .serviceRegistry(manager.registry())
                                                                       .enableMetrics(true)
                                                                       .buildPrototype());
            observations.add(metrics);
            return metrics;
        }

        private List<Tag> tags(String channel) {
            return List.of(factory.tagCreate("channel", channel));
        }

        private Timer timer(String channel) {
            return registry.timer(DURATION, tags(channel)).orElseThrow();
        }

        private Counter counter(String channel) {
            return registry.counter(FAILURES, tags(channel)).orElseThrow();
        }
    }

    private static final class TestClock implements Clock {
        private final AtomicLong now = new AtomicLong();
        private final AtomicInteger reads = new AtomicInteger();
        private boolean fail;

        @Override
        public long wallTime() {
            return TimeUnit.NANOSECONDS.toMillis(now.get());
        }

        @Override
        public long monotonicTime() {
            reads.incrementAndGet();
            if (fail) {
                throw new IllegalStateException("Clock unavailable");
            }
            return now.get();
        }

        private void advance(long nanos) {
            now.addAndGet(nanos);
        }
    }

    private static final class RegistryHandler implements InvocationHandler {
        private final MeterRegistry delegate;
        private final MeterRegistry proxy = MessagingMetricsTest.proxy(MeterRegistry.class, this);
        private final AtomicInteger clockRequests = new AtomicInteger();
        private final AtomicInteger removals = new AtomicInteger();
        private Fault fault = Fault.NONE;
        private String filteredChannel;

        private RegistryHandler(MeterRegistry delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object instance, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (name.equals("clock")) {
                clockRequests.incrementAndGet();
            }
            if (name.equals("isMeterEnabled") && args.length == 2
                    && args[1] instanceof Map<?, ?> tags && tags.get("channel").equals(filteredChannel)) {
                return false;
            }
            if (name.equals("getOrCreate") && args[0] instanceof Counter.Builder && fault == Fault.REGISTER) {
                throw new IllegalStateException("Counter registration unavailable");
            }
            if (name.equals("remove")) {
                removals.incrementAndGet();
                if (fault == Fault.REMOVE) {
                    throw new IllegalStateException("Removal unavailable");
                }
            }
            Object result = MessagingMetricsTest.invoke(method, delegate, args);
            if (name.equals("getOrCreate") && result instanceof Timer timer) {
                return MessagingMetricsTest.proxy(Timer.class, (_, timerMethod, timerArgs) -> {
                    if (timerMethod.getName().equals("record") && fault == Fault.RECORD) {
                        throw new IllegalStateException("Recording unavailable");
                    }
                    return MessagingMetricsTest.invoke(timerMethod, timer, timerArgs);
                });
            }
            return result;
        }
    }
}
