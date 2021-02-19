/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.scheduling;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.helidon.common.configurable.ScheduledThreadPoolSupplier;

import static org.hamcrest.MatcherAssert.assertThat;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public class FixedRateSchedulingTest {

    static final long ERROR_MARGIN_MILLIS = 500;

    @Test
    void fixedRateDelay() {
        IntervalMeter meter = new IntervalMeter();
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            Scheduling.fixedRate()
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
    void fixedRateInitialDelay() {
        IntervalMeter meter = new IntervalMeter();
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();

        final long expectedInitialDelay = 2000;

        try {

            Scheduling.FixedRateBuilder builder = Scheduling.fixedRate()
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
    void fixedRateInvalidDelay() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            Assertions.assertThrows(IllegalArgumentException.class, () -> Scheduling.fixedRate()
                    .executor(executorService)
                    .delay(0)
                    .task(inv -> {})
                    .build());

        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void fixedRateInvalidMissingDelay() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            Assertions.assertThrows(SchedulingException.class, () -> Scheduling.fixedRate()
                    .executor(executorService)
                    .task(inv -> {})
                    .build());

        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void fixedRateMissingTask() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            Assertions.assertThrows(SchedulingException.class, () -> Scheduling.fixedRate()
                    .executor(executorService)
                    .delay(2)
                    .build());

        } finally {
            executorService.shutdownNow();
        }
    }
}
