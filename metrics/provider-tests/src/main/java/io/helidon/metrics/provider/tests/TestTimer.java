/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Timer;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

@Testing.Test
class TestTimer {

    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        meterRegistry = Metrics.globalRegistry();
    }

    @Test
    void testSimpleRecord() {
        Timer t = meterRegistry.getOrCreate(Timer.builder("a"));

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
        Timer t = meterRegistry.getOrCreate(Timer.builder("b"));

        long initialValue = 0L;
        long update = 12L;

        t.record((Callable<Object>) () -> {
            TimeUnit.MILLISECONDS.sleep(update);
            return null;
        });

        assertThat("After update",
                   t.count(),
                   is(1L));
        assertThat("After update",
                   t.totalTime(TimeUnit.MILLISECONDS),
                   greaterThanOrEqualTo((double) initialValue + update));
    }

    @Test
    void testSupplier() {
        Timer t = meterRegistry.getOrCreate(Timer.builder("c"));
        long initialValue = 0L;
        long update = 8L;

        t.record((Supplier<Object>) () -> {
            try {
                TimeUnit.MILLISECONDS.sleep(update);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        assertThat("After update",
                   t.count(),
                   is(1L));
        assertThat("After update",
                   t.totalTime(TimeUnit.MILLISECONDS),
                   greaterThanOrEqualTo((double) initialValue + update));
    }

    @Test
    void testWrapCallable() throws Exception {
        Timer t = meterRegistry.getOrCreate(Timer.builder("d"));
        long initialValue = 0L;
        long update = 18L;

        Callable<?> c = t.wrap((Callable<?>) () -> {
            try {
                TimeUnit.MILLISECONDS.sleep(update);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        assertThat("Before running",
                   t.count(),
                   is(0L));
        assertThat("Before running",
                   t.totalTime(TimeUnit.MILLISECONDS),
                   greaterThanOrEqualTo((double) initialValue));

        c.call();

        assertThat("After running",
                   t.count(),
                   is(1L));
        assertThat("After running",
                   t.totalTime(TimeUnit.MILLISECONDS),
                   greaterThanOrEqualTo((double) initialValue + update));
    }

    @Test
    void testSample() throws InterruptedException {
        Timer t = meterRegistry.getOrCreate(Timer.builder("e"));
        long initialValue = 0L;
        long update = 18L;

        Timer.Sample sample = Timer.start();

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
        Timer t = meterRegistry.getOrCreate(Timer.builder("f"));
        AdjustableClock clock = new AdjustableClock();

        Timer.Sample sample = Timer.start(clock);

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

        MeterRegistry registry = MetricsFactory.getInstance()
                .createMeterRegistry(MetricsConfig.builder().build());

        Timer t = registry.getOrCreate(Timer.builder("g"));

        Timer.Sample sample = Timer.start(registry);

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
