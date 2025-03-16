/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.scheduling;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.common.Errors;
import io.helidon.common.configurable.ScheduledThreadPoolSupplier;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Execution(ExecutionMode.CONCURRENT)
public class FixedRateSchedulingTest {

    static final long ERROR_MARGIN_MILLIS = 500;

    @SuppressWarnings("removal")
    @Test
    void fixedRateDelayDeprecated() {
        IntervalMeter meter = new IntervalMeter();
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            Scheduling.fixedRateBuilder()
                    .executor(executorService)
                    .delay(2)
                    .task(cronInvocation -> meter
                            .start()
                            .sleep(200, TimeUnit.MILLISECONDS)
                            .end())
                    .build();

            meter.awaitTill(2, 20, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }
        meter.assertAverageDuration(Duration.ofSeconds(2), Duration.ofMillis(ERROR_MARGIN_MILLIS));
    }

    @Test
    void fixedRateDelay() {
        IntervalMeter meter = new IntervalMeter();
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            Scheduling.fixedRate()
                    .executor(executorService)
                    .interval(Duration.ofSeconds(2))
                    .task(cronInvocation -> meter
                            .start()
                            .sleep(200, TimeUnit.MILLISECONDS)
                            .end())
                    .build();

            meter.awaitTill(2, 20, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }
        meter.assertAverageDuration(Duration.ofSeconds(2), Duration.ofMillis(ERROR_MARGIN_MILLIS));
    }

    @Test
    void fixedRateDelayFromStart() throws InterruptedException {
        long delayMillis = 2 * 1000;

        try (var executorService = ScheduledThreadPoolSupplier.create().get()) {
            AtomicLong lastEndTime = new AtomicLong();
            AtomicLong lastStartTime = new AtomicLong();
            CountDownLatch latch = new CountDownLatch(2);
            Scheduling.fixedRate()
                    .executor(executorService)
                    .delayType(FixedRate.DelayType.SINCE_PREVIOUS_START)
                    .delayBy(Duration.ZERO)
                    .interval(Duration.ofMillis(delayMillis))
                    .task(i -> {
                        lastStartTime.set(System.currentTimeMillis());
                        Thread.sleep(300);
                        latch.countDown();
                        Thread.sleep(300);
                        lastEndTime.set(System.currentTimeMillis());
                    })
                    .build();

            assertTrue(latch.await(20, TimeUnit.SECONDS));
            long lastStart = lastStartTime.get();
            long lastEnd = lastEndTime.get();
            assertThat(lastStart - lastEnd, Matchers.lessThanOrEqualTo(delayMillis));
        }
    }

    @Test
    void fixedRateDelayFromEnd() throws InterruptedException {
        long delayMillis = 2 * 1000;

        try (var executorService = ScheduledThreadPoolSupplier.create().get()) {
            AtomicLong lastEndTime = new AtomicLong();
            AtomicLong lastStartTime = new AtomicLong();
            CountDownLatch latch = new CountDownLatch(2);
            Scheduling.fixedRate()
                    .executor(executorService)
                    .delayType(FixedRate.DelayType.SINCE_PREVIOUS_END)
                    .delayBy(Duration.ZERO)
                    .interval(Duration.ofMillis(delayMillis))
                    .task(i -> {
                        lastStartTime.set(System.currentTimeMillis());
                        Thread.sleep(300);
                        latch.countDown();
                        Thread.sleep(300);
                        lastEndTime.set(System.currentTimeMillis());
                    })
                    .build();

            assertTrue(latch.await(20, TimeUnit.SECONDS));
            long lastStart = lastStartTime.get();
            long lastEnd = lastEndTime.get();
            assertThat(lastStart - lastEnd, greaterThanOrEqualTo(delayMillis));
        }
    }

    @SuppressWarnings("removal")
    @Test
    void fixedRateInitialDelayDeprecated() {
        IntervalMeter meter = new IntervalMeter();
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();

        final long expectedInitialDelay = 2000;

        try {

            Scheduling.FixedRateBuilder builder = Scheduling.fixedRateBuilder()
                    .executor(executorService)
                    .initialDelay(expectedInitialDelay)
                    .delay(1000)
                    .timeUnit(TimeUnit.MILLISECONDS)
                    .task(cronInvocation -> meter
                            .start()
                            .sleep(200, TimeUnit.MILLISECONDS)
                            .end());

            long initialTime = System.currentTimeMillis();
            builder.build();

            meter.awaitTill(2, 20, TimeUnit.SECONDS);

            long actualInitialDelay = meter.get(0).startTime().toEpochMilli() - initialTime;
            long difference = Math.abs(actualInitialDelay - expectedInitialDelay);

            assertThat("Initial delay " + Duration.ofMillis(actualInitialDelay).toString()
                               + " differs from expected " + Duration.ofMillis(expectedInitialDelay),
                       difference, Matchers.lessThanOrEqualTo(ERROR_MARGIN_MILLIS));
        } finally {
            executorService.shutdownNow();
        }
        meter.assertAverageDuration(Duration.ofSeconds(1), Duration.ofMillis(ERROR_MARGIN_MILLIS));
    }

    @Test
    void fixedRateInitialDelay() {
        IntervalMeter meter = new IntervalMeter();
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();

        final long expectedInitialDelay = 2000;

        try {

            var builder = Scheduling.fixedRate()
                    .executor(executorService)
                    .delayBy(Duration.ofMillis(expectedInitialDelay))
                    .interval(Duration.ofSeconds(1))
                    .task(cronInvocation -> meter
                            .start()
                            .sleep(200, TimeUnit.MILLISECONDS)
                            .end());

            long initialTime = System.currentTimeMillis();
            builder.build();

            meter.awaitTill(2, 20, TimeUnit.SECONDS);

            long actualInitialDelay = meter.get(0).startTime().toEpochMilli() - initialTime;
            long difference = Math.abs(actualInitialDelay - expectedInitialDelay);

            assertThat("Initial delay " + Duration.ofMillis(actualInitialDelay).toString()
                               + " differs from expected " + Duration.ofMillis(expectedInitialDelay),
                       difference, Matchers.lessThanOrEqualTo(ERROR_MARGIN_MILLIS));
        } finally {
            executorService.shutdownNow();
        }
        meter.assertAverageDuration(Duration.ofSeconds(1), Duration.ofMillis(ERROR_MARGIN_MILLIS));
    }

    @SuppressWarnings("removal")
    @Test
    void fixedRateInvalidDelayDeprecated() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            Assertions.assertThrows(IllegalArgumentException.class, () -> Scheduling.fixedRateBuilder()
                    .executor(executorService)
                    .delay(0)
                    .task(inv -> {
                    })
                    .build());

        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void fixedRateInvalidDelay() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            Assertions.assertThrows(IllegalArgumentException.class, () -> Scheduling.fixedRate()
                    .executor(executorService)
                    .interval(Duration.ZERO)
                    .task(inv -> {
                    })
                    .build());

        } finally {
            executorService.shutdownNow();
        }
    }

    @SuppressWarnings("removal")
    @Test
    void fixedRateInvalidMissingDelayDeprecated() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            Assertions.assertThrows(SchedulingException.class, () -> Scheduling.fixedRateBuilder()
                    .executor(executorService)
                    .task(inv -> {
                    })
                    .build());

        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void fixedRateInvalidMissingDelay() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            Assertions.assertThrows(Errors.ErrorMessagesException.class, () -> Scheduling.fixedRate()
                    .executor(executorService)
                    .task(inv -> {
                    })
                    .build());

        } finally {
            executorService.shutdownNow();
        }
    }

    @SuppressWarnings("removal")
    @Test
    void fixedRateMissingTaskDeprecated() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            Assertions.assertThrows(SchedulingException.class, () -> Scheduling.fixedRateBuilder()
                    .executor(executorService)
                    .delay(2)
                    .build());

        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void fixedRateMissingTask() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            Assertions.assertThrows(Errors.ErrorMessagesException.class, () -> Scheduling.fixedRate()
                    .executor(executorService)
                    .interval(Duration.ofSeconds(2))
                    .build());

        } finally {
            executorService.shutdownNow();
        }
    }
}
