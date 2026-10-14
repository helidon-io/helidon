/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
package io.helidon.metrics.provider.tests;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.Bucket;
import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class TestTimer {

    private static MetricsFactory metricsFactory;
    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        metricsFactory = Services.get(MetricsFactory.class);
        meterRegistry = metricsFactory.createMeterRegistry(MetricsConfig.create());
    }

    @AfterAll
    static void closeRegistry() {
        meterRegistry.close();
    }

    @Test
    void testSimpleRecord() {
        Timer t = meterRegistry.getOrCreate(metricsFactory.timerBuilder("a"));

        long initialValue = 0L;

        assertThat("Initial value",
                   t.count(),
                   is(0L));
        assertThat("Initial value",
                   t.totalTime(TimeUnit.MILLISECONDS),
                   greaterThanOrEqualTo((double) initialValue));

        long update = 12L;
        t.record(update, TimeUnit.MILLISECONDS);
        assertThat("Updated value",
                   t.count(),
                   is(1L));
        assertThat("Updated value",
                   t.totalTime(TimeUnit.MILLISECONDS),
                   greaterThanOrEqualTo((double) initialValue + update));

        initialValue += update;
        update = 7L;
        t.record(Duration.ofMillis(update));
        assertThat("Second updated value",
                   t.count(),
                   is(2L));
        assertThat("Second updated value",
                   t.totalTime(TimeUnit.MILLISECONDS),
                   greaterThanOrEqualTo((double) initialValue + update));
    }

    @Test
    void testCallable() throws Exception {
        Timer t = meterRegistry.getOrCreate(metricsFactory.timerBuilder("b"));

        long initialValue = 0L;
        long update = 12L;

        String result = t.record((Callable<String>) () -> {
            TimeUnit.MILLISECONDS.sleep(update);
            return "done";
        });

        assertThat("Callable result", result, is("done"));
        assertThat("After update",
                   t.count(),
                   is(1L));
        assertThat("After update",
                   t.totalTime(TimeUnit.MILLISECONDS),
                   greaterThanOrEqualTo((double) initialValue + update));
    }

    @Test
    void testSupplier() {
        Timer t = meterRegistry.getOrCreate(metricsFactory.timerBuilder("c"));
        long initialValue = 0L;
        long update = 8L;

        String result = t.record((Supplier<String>) () -> {
            try {
                TimeUnit.MILLISECONDS.sleep(update);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return "done";
        });

        assertThat("Supplier result", result, is("done"));
        assertThat("After update",
                   t.count(),
                   is(1L));
        assertThat("After update",
                   t.totalTime(TimeUnit.MILLISECONDS),
                   greaterThanOrEqualTo((double) initialValue + update));
    }

    @Test
    void testWrapCallable() throws Exception {
        Timer t = meterRegistry.getOrCreate(metricsFactory.timerBuilder("d"));
        long initialValue = 0L;
        long update = 18L;

        Callable<String> c = t.wrap((Callable<String>) () -> {
            try {
                TimeUnit.MILLISECONDS.sleep(update);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return "done";
        });

        assertThat("Before running",
                   t.count(),
                   is(0L));
        assertThat("Before running",
                   t.totalTime(TimeUnit.MILLISECONDS),
                   greaterThanOrEqualTo((double) initialValue));

        assertThat("Wrapped callable result", c.call(), is("done"));

        assertThat("After running",
                   t.count(),
                   is(1L));
        assertThat("After running",
                   t.totalTime(TimeUnit.MILLISECONDS),
                   greaterThanOrEqualTo((double) initialValue + update));
    }

    @Test
    void testSample() throws InterruptedException {
        Timer t = meterRegistry.getOrCreate(metricsFactory.timerBuilder("e"));
        long initialValue = 0L;
        long update = 18L;

        Timer.Sample sample = metricsFactory.timerStart();

        long waitTime = 110L;
        TimeUnit.MILLISECONDS.sleep(waitTime);

        sample.stop(t);

        assertThat("After sample stop",
                   t.count(),
                   is(1L));
        assertThat("After sample stop",
                   t.totalTime(TimeUnit.MILLISECONDS),
                   greaterThanOrEqualTo((double) waitTime));

    }

    @Test
    void testSampleWithExplicitClock() {
        Timer t = meterRegistry.getOrCreate(metricsFactory.timerBuilder("f"));
        AdjustableClock clock = new AdjustableClock();

        Timer.Sample sample = metricsFactory.timerStart(clock);

        long waitTime = 55L;
        clock.advance(waitTime);

        sample.stop(t);

        assertThat("After sample stop",
                   t.count(),
                   is(1L));
        assertThat("After sample stop",
                   t.totalTime(TimeUnit.MILLISECONDS),
                   greaterThanOrEqualTo((double) waitTime));
    }

    @Test
    void testSampleWithImplicitClock() {

        MeterRegistry registry = metricsFactory.createMeterRegistry(MetricsConfig.builder().build());

        Timer t = registry.getOrCreate(metricsFactory.timerBuilder("g"));

        Timer.Sample sample = metricsFactory.timerStart(registry);

        long waitTime = 35L;

        try {
            TimeUnit.MILLISECONDS.sleep(waitTime);
        } catch (InterruptedException e) {
            fail("Error during delay of timer test", e);
        }
        sample.stop(t);

        assertThat("After sample stop",
                   t.count(),
                   is(1L));
        assertThat("After sample stop",
                   t.totalTime(TimeUnit.MILLISECONDS),
                   greaterThanOrEqualTo((double) waitTime));

    }

    @Test
    void testPublishPercentileHistogramUsesExpectedValueBounds() {
        Timer timer = meterRegistry.getOrCreate(metricsFactory.timerBuilder("histogram.flag.timer")
                                                       .minimumExpectedValue(Duration.ofMillis(1))
                                                       .maximumExpectedValue(Duration.ofMillis(10))
                                                       .publishPercentileHistogram(true));
        timer.record(2, TimeUnit.MILLISECONDS);
        timer.record(11, TimeUnit.MILLISECONDS);

        List<Bucket> buckets = StreamSupport.stream(timer.snapshot().histogramCounts().spliterator(), false).toList();

        assertThat("Published histogram bucket count", buckets.size(), greaterThanOrEqualTo(3));
        assertThat("Published histogram bucket boundaries",
                   buckets.stream().map(bucket -> bucket.boundary(TimeUnit.MILLISECONDS)).toList(),
                   hasItems(1D, 10D));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testExecutionAndResultsWhenEnabledOrDisabled(boolean enabled) throws Exception {
        MeterRegistry registry = metricsFactory.createMeterRegistry(MetricsConfig.builder().enabled(enabled).build());
        try {
            Timer timer = registry.getOrCreate(metricsFactory.timerBuilder("execution"));
            AtomicInteger invocations = new AtomicInteger();
            Supplier<String> supplier = () -> {
                invocations.incrementAndGet();
                return "done";
            };
            Callable<String> callable = supplier::get;

            assertThat("Supplier result", timer.record(supplier), is("done"));
            assertThat("Callable result", timer.record(callable), is("done"));
            assertThat("Wrapped supplier result", timer.wrap(supplier).get(), is("done"));
            assertThat("Wrapped callable result", timer.wrap(callable).call(), is("done"));
            timer.record((Runnable) invocations::incrementAndGet);
            timer.wrap((Runnable) invocations::incrementAndGet).run();

            assertThat("All operations executed", invocations.get(), is(6));
            assertThat("Recorded operations", timer.count(), is(enabled ? 6L : 0L));
            assertThat("Snapshot operations", timer.snapshot().count(), is(enabled ? 6L : 0L));
        } finally {
            registry.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testNullResultsWhenEnabledOrDisabled(boolean enabled) throws Exception {
        MeterRegistry registry = metricsFactory.createMeterRegistry(MetricsConfig.builder().enabled(enabled).build());
        try {
            Timer timer = registry.getOrCreate(metricsFactory.timerBuilder("null.results"));
            assertThat("Null supplier result", timer.record((Supplier<Object>) () -> null), nullValue());
            assertThat("Null callable result", timer.record((Callable<Object>) () -> null), nullValue());
            assertThat("Null wrapped supplier result", timer.wrap((Supplier<Object>) () -> null).get(), nullValue());
            assertThat("Null wrapped callable result", timer.wrap((Callable<Object>) () -> null).call(), nullValue());
            assertThat("Recorded operations", timer.count(), is(enabled ? 4L : 0L));
        } finally {
            registry.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testExceptionsWhenEnabledOrDisabled(boolean enabled) {
        MeterRegistry registry = metricsFactory.createMeterRegistry(MetricsConfig.builder().enabled(enabled).build());
        try {
            Timer timer = registry.getOrCreate(metricsFactory.timerBuilder("exception.results"));
            IllegalStateException failure = new IllegalStateException("operation failed");
            Supplier<Object> supplier = () -> {
                throw failure;
            };
            Callable<Object> callable = supplier::get;

            assertThat("Supplier failure", assertThrows(IllegalStateException.class, () -> timer.record(supplier)),
                       sameInstance(failure));
            assertThat("Callable failure", assertThrows(IllegalStateException.class, () -> timer.record(callable)),
                       sameInstance(failure));
            assertThat("Wrapped supplier failure", assertThrows(IllegalStateException.class, () -> timer.wrap(supplier).get()),
                       sameInstance(failure));
            assertThat("Wrapped callable failure", assertThrows(IllegalStateException.class, () -> timer.wrap(callable).call()),
                       sameInstance(failure));
            assertThat("Recorded failed operations", timer.count(), is(enabled ? 4L : 0L));
        } finally {
            registry.close();
        }
    }

    @Test
    void testCompatibilityWithRelease3() {
        String metricsConfig = """
                metrics:
                  timers:
                    base-units-default: nanoseconds""";

        Config config = Config.just(ConfigSources.create(metricsConfig, MediaTypes.APPLICATION_YAML));
        MeterRegistry localMeterRegistry = metricsFactory.createMeterRegistry(MetricsConfig.builder()
                                                                             .config(config.get("metrics"))
                                                                             .build());
        Timer defaultUnitsTimer = localMeterRegistry.getOrCreate(metricsFactory.timerBuilder("defaultUnitsTimer"));

        defaultUnitsTimer.record(Duration.ofMillis(150));

        String defaultOutput = defaultUnitsTimer.toString();

        try {
            assertThat("Default timer toString", defaultOutput, containsString("PT0.15S"));
        } finally {
            localMeterRegistry.remove(defaultUnitsTimer);
        }
    }

    @Test
    void testUnitsInToStringWithDefaultMilliseconds() {
        String metricsConfig = """
                metrics:
                  timers:
                    base-units-default: milliseconds""";

        Config config = Config.just(ConfigSources.create(metricsConfig, MediaTypes.APPLICATION_YAML));
        MeterRegistry localMeterRegistry = metricsFactory.createMeterRegistry(MetricsConfig.builder()
                                                                             .config(config.get("metrics"))
                                                                             .build());
        Timer timer = localMeterRegistry.getOrCreate(metricsFactory.timerBuilder("forToStringTest")
                                                        .baseUnit("milliseconds"));

        Timer otherTimer = localMeterRegistry.getOrCreate(metricsFactory.timerBuilder("otherToStringTest")
                                                             .baseUnit("seconds"));
        Timer defaultUnitsTimer = localMeterRegistry.getOrCreate(metricsFactory.timerBuilder("defaultUnitsTimer"));
        Timer secondsUnitsTimer = localMeterRegistry.getOrCreate(metricsFactory.timerBuilder("secondsUnitsTimer")
                        .baseUnit("SECONDS"));

        timer.record(Duration.ofMillis(125));
        otherTimer.record(Duration.ofMillis(1300));
        defaultUnitsTimer.record(Duration.ofMillis(150));
        secondsUnitsTimer.record(Duration.ofMillis(4500));

        String output = timer.toString();
        String otherOutput = otherTimer.toString();
        String defaultOutput = defaultUnitsTimer.toString();
        String secondsOutput = secondsUnitsTimer.toString();

        try {
            assertThat("Timer toString", output, containsString("PT0.125S"));
            assertThat("Other timer toString", otherOutput, containsString("PT1.3S"));
            assertThat("Default timer toString", defaultOutput, containsString("PT0.15S"));
            assertThat("Seconds timer toString", secondsOutput, containsString("PT4.5S"));
        } finally {
            Stream.of(timer, otherTimer, defaultUnitsTimer, secondsUnitsTimer)
                            .forEach(localMeterRegistry::remove);
        }

    }

    @Test
    void checkBaseUnitValidation() {
        assertThrows(IllegalArgumentException.class,
                     () -> metricsFactory.timerBuilder("withIllegalUnits")
                             .baseUnit("fortnights"),
                             "Illegal unit 'fortnights'");

    }

    private static class AdjustableClock implements Clock {

        private long wallTime;
        private long monotonicTime;

        private AdjustableClock() {
            this.wallTime = System.currentTimeMillis();
            this.monotonicTime = System.nanoTime();
        }

        @Override
        public long wallTime() {
            return wallTime;
        }

        @Override
        public long monotonicTime() {
            return monotonicTime;
        }

        private void advance(long ms) {
            wallTime += ms;
            monotonicTime += ms * 1000 * 1000;
        }
    }
}
