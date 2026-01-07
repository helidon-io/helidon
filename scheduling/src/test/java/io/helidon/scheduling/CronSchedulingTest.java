/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.helidon.common.Errors;
import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsEmptyCollection.empty;

@SuppressWarnings("removal")
@Testing.Test
@Execution(ExecutionMode.CONCURRENT)
class CronSchedulingTest {

    static final long ERROR_MARGIN_MILLIS = 500;

    private final TaskManager taskManager;

    CronSchedulingTest(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @AfterAll
    static void afterAll() {
        assertThat(Services.get(TaskManager.class).tasks(), empty());
    }

    @Test
    void cronTestDeprecated() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        IntervalMeter meter = new IntervalMeter();
        var cron = Scheduling.cronBuilder()
                .executor(executorService)
                .expression("0/2 * * * * ? *")
                .task(cronInvocation -> meter
                        .start()
                        .sleep(200, TimeUnit.MILLISECONDS)
                        .end())
                .build();
        assertThat(taskManager.tasks(), hasItem(cron));
        meter.awaitTill(2, 20, TimeUnit.SECONDS);
        cron.close();
        executorService.shutdownNow();
        meter.assertAverageDuration(Duration.ofSeconds(2), Duration.ofMillis(ERROR_MARGIN_MILLIS));
    }

    @Test
    void cronTest() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        IntervalMeter meter = new IntervalMeter();
        Cron cron = Cron.builder()
                .id("cronTestDeprecated")
                .executor(executorService)
                .expression("0/2 * * * * ? *")
                .task(cronInvocation -> meter
                        .start()
                        .sleep(200, TimeUnit.MILLISECONDS)
                        .end())
                .build();

        meter.awaitTill(2, 20, TimeUnit.SECONDS);
        assertThat(taskManager.tasks(), hasItem(cron));
        cron.close();
        executorService.shutdownNow();
        meter.assertAverageDuration(Duration.ofSeconds(2), Duration.ofMillis(ERROR_MARGIN_MILLIS));
    }

    @Test
    void cronConcurrencyDisabledDeprecated() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        IntervalMeter meter = new IntervalMeter();
        var cron = Scheduling.cronBuilder()
                .executor(executorService)
                .concurrentExecution(false)
                //every 1 sec
                .expression("* * * * * ? *")
                .task(cronInvocation -> meter
                        .start()
                        .sleep(2, TimeUnit.SECONDS)
                        .end())
                .build();

        meter.awaitTill(3, 20, TimeUnit.SECONDS);
        assertThat(taskManager.tasks(), hasItem(cron));
        cron.close();
        executorService.shutdownNow();
        // every 1 sec + 2 secs sleeping
        meter.assertAverageDuration(Duration.ofSeconds(3), Duration.ofMillis(ERROR_MARGIN_MILLIS));
        meter.assertNonConcurrent();
    }

    @Test
    void cronConcurrencyDisabled() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        IntervalMeter meter = new IntervalMeter();
        var cron = Cron.builder()
                .id("cronConcurrencyDisabled")
                .executor(executorService)
                .concurrentExecution(false)
                //every 1 sec
                .expression("* * * * * ? *")
                .task(cronInvocation -> meter
                        .start()
                        .sleep(2, TimeUnit.SECONDS)
                        .end())
                .build();

        meter.awaitTill(3, 20, TimeUnit.SECONDS);
        assertThat(taskManager.tasks(), hasItem(cron));
        cron.close();
        executorService.shutdownNow();
        // every 1 sec + 2 secs sleeping
        meter.assertAverageDuration(Duration.ofSeconds(3), Duration.ofMillis(ERROR_MARGIN_MILLIS));
        meter.assertNonConcurrent();
    }

    @Test
    void cronConcurrencyEnabledDeprecated() {
        IntervalMeter meter = new IntervalMeter();
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        Task cron = null;
        try {
            cron = Scheduling.cronBuilder()
                    .executor(executorService)
                    //every 1 sec
                    .expression("* * * * * ? *")
                    .task(cronInvocation -> meter
                            .start()
                            .sleep(2, TimeUnit.SECONDS)
                            .end())
                    .build();

            meter.awaitTill(3, 20, TimeUnit.SECONDS);
        } finally {
            if (cron != null) {
                cron.close();
            }
            executorService.shutdownNow();
        }
        // every 1 sec + 2 secs sleeping
        meter.assertAverageDuration(Duration.ofSeconds(1), Duration.ofMillis(ERROR_MARGIN_MILLIS));
    }

    @Test
    void cronConcurrencyEnabled() {
        IntervalMeter meter = new IntervalMeter();
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        Task cron = null;
        try {
            cron = Cron.builder()
                    .executor(executorService)
                    //every 1 sec
                    .expression("* * * * * ? *")
                    .task(cronInvocation -> meter
                            .start()
                            .sleep(2, TimeUnit.SECONDS)
                            .end())
                    .build();

            meter.awaitTill(3, 20, TimeUnit.SECONDS);
        } finally {
            if (cron != null) {
                cron.close();
            }
            executorService.shutdownNow();
        }
        // every 1 sec + 2 secs sleeping
        meter.assertAverageDuration(Duration.ofSeconds(1), Duration.ofMillis(ERROR_MARGIN_MILLIS));
    }

    @Test
    void cronDefaultExecutorDeprecated() {
        IntervalMeter meter = new IntervalMeter();
        List<String> threadNames = new ArrayList<>();
        Task task = Scheduling.cronBuilder()
                .expression("0/3 * * * * ? *")
                .task(cronInvocation -> meter
                        .start()
                        .doSomething(() -> threadNames.add(Thread.currentThread().getName()))
                        .sleep(200, TimeUnit.MILLISECONDS)
                        .end())
                .build();

        meter.awaitTill(2, 20, TimeUnit.SECONDS);
        task.close();
        task.executor().shutdown();
        meter.assertAverageDuration(Duration.ofSeconds(3), Duration.ofMillis(ERROR_MARGIN_MILLIS));
        threadNames.stream()
                .map(s -> s.substring(0, Scheduling.DEFAULT_THREAD_NAME_PREFIX.length()))
                .forEach(s -> assertThat(s, Matchers.equalTo(Scheduling.DEFAULT_THREAD_NAME_PREFIX)));
        assertThat(threadNames.size(), Matchers.greaterThan(0));
    }

    @Test
    void cronDefaultExecutor() {
        IntervalMeter meter = new IntervalMeter();
        List<String> threadNames = new ArrayList<>();
        Task task = Cron.builder()
                .expression("0/3 * * * * ? *")
                .task(cronInvocation -> meter
                        .start()
                        .doSomething(() -> threadNames.add(Thread.currentThread().getName()))
                        .sleep(200, TimeUnit.MILLISECONDS)
                        .end())
                .build();

        meter.awaitTill(2, 20, TimeUnit.SECONDS);
        task.close();
        task.executor().shutdown();
        meter.assertAverageDuration(Duration.ofSeconds(3), Duration.ofMillis(ERROR_MARGIN_MILLIS));
        threadNames.stream()
                .map(s -> s.substring(0, Scheduling.DEFAULT_THREAD_NAME_PREFIX.length()))
                .forEach(s -> assertThat(s, Matchers.equalTo(Scheduling.DEFAULT_THREAD_NAME_PREFIX)));
        assertThat(threadNames.size(), Matchers.greaterThan(0));
    }

    @Test
    void cronWrongExpressionDeprecated() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            IntervalMeter meter = new IntervalMeter();
            Assertions.assertThrows(IllegalArgumentException.class, () -> Scheduling.cronBuilder()
                    .executor(executorService)
                    .expression("0/2 I N V A ? D")
                    .task(cronInvocation -> meter
                            .start()
                            .end())
                    .build());
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    void cronWrongExpression() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            IntervalMeter meter = new IntervalMeter();
            Assertions.assertThrows(IllegalArgumentException.class, () -> Cron.builder()
                    .executor(executorService)
                    .expression("0/2 I N V A ? D")
                    .task(cronInvocation -> meter
                            .start()
                            .end())
                    .build());
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    void cronMissingTaskDeprecated() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            Assertions.assertThrows(SchedulingException.class, () -> Scheduling.cronBuilder()
                    .executor(executorService)
                    .expression("* * * * * ? *")
                    .build());
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    void cronMissingTask() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            Assertions.assertThrows(Errors.ErrorMessagesException.class, () -> Cron.builder()
                    .executor(executorService)
                    .expression("* * * * * ? *")
                    .build());
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    void cronDisabledProgrammatically() throws InterruptedException {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        IntervalMeter meter = new IntervalMeter();
        Cron task = null;

        try {
            task = Cron.builder()
                    .id("disabledCronTest")
                    .executor(executorService)
                    .expression("* * * * * ? *")  // Every second
                    .enabled(false)  // Disabled
                    .task(cronInvocation -> meter.start().end())
                    .build();

            // Wait and verify the task never executed
            Thread.sleep(3000);
            assertThat(meter.size(), Matchers.equalTo(0));
            assertThat(taskManager.tasks(), hasItem(task));
            assertThat(task.enabled(), is(false));
        } finally {
            if (task != null) {
                task.close();
            }
            executorService.shutdownNow();
        }
    }

    /**
     * Verifies that when a custom timezone is configured via {@code .zone()},
     * the cron task calculates the next execution time relative to that zone.
     * <p>
     * <b>Concept:</b>
     * By default, Cron schedulers use the system's default timezone. This test ensures that
     * providing a specific {@code ZoneId} forces the internal calculation to align with that zone's
     * wall-clock time.
     * <p>
     * <b>Test Strategy:</b>
     * <ol>
     *   <li>Identify a test zone guaranteed to have a different wall-clock hour than the system zone.</li>
     *   <li>Calculate a specific target time exactly <b>N seconds in the future</b> relative to that test zone.</li>
     *   <li>Generate a strict Cron expression for that exact second (Sec, Min, Hour, Day, Month, Year).</li>
     *   <li>Start the scheduler with the configured test zone.</li>
     * </ol>
     * <p>
     * <b>Expected Result:</b>
     * <ul>
     *   <li><b>If timezone is respected:</b> The scheduler waits N seconds and fires when the wall-clock time matches the target.</li>
     *   <li><b>If timezone is ignored:</b> The scheduler compares the strict Cron expression (e.g., 09:00) against the System clock (e.g., 14:00), finds no match, and the task does not execute.</li>
     * </ul>
     */
    @Test
    void cronWithCustomZone() throws InterruptedException {
        var executorService = ScheduledThreadPoolSupplier.create().get();
        var latch = new CountDownLatch(1);
        var task = (Cron) null;

        try {
            var now = Instant.now();
            var systemZone = ZoneId.systemDefault();
            var systemNow = now.atZone(systemZone);

            // Choose a test zone guaranteed to be different from system:
            // If system is UTC, use New York (UTC-5 or UTC-4 depending on DST)
            // If system is NOT UTC, use UTC
            // This ensures the hour will be different in most cases
            var testZone = "UTC".equals(systemZone.getId())
                    ? ZoneId.of("America/New_York")
                    : ZoneId.of("UTC");

            var testZoneNow = now.atZone(testZone);

            // If the random choice resulted in the same hour (e.g. London Winter vs UTC),
            // switch to Tokyo to guarantee a difference.
            if (testZoneNow.getHour() == systemNow.getHour()) {
                testZone = ZoneId.of("Asia/Tokyo");
            }

            // Calculate a "Sniper" Target: 3 seconds in the future
            var targetTime = now.atZone(testZone).plusSeconds(3);

            // Strict Cron: Run exactly at [Sec] [Min] [Hour]
            // Format: "sec min hour day month day-of-week year"
            var cronExpression = String.format("%d %d %d %d %d ? %d",
                    targetTime.getSecond(),
                    targetTime.getMinute(),
                    targetTime.getHour(),
                    targetTime.getDayOfMonth(),
                    targetTime.getMonthValue(),
                    targetTime.getYear()
            );

            task = Cron.builder()
                    .id("cronWithCustomZone")
                    .executor(executorService)
                    .expression(cronExpression)
                    .zone(testZone)
                    .task(inv -> latch.countDown())
                    .build();

            // If timezone is respected: scheduler waits N seconds, fires, latch opens
            // If timezone is ignored: scheduler compares System Time to Target Time, they don't match, nothing happens
            var executed = latch.await(10, TimeUnit.SECONDS);

            assertThat("Task should execute at the specific scheduled time in the target timezone", executed, is(true));
        } finally {
            if (task != null) {
                task.close();
            }

            executorService.shutdownNow();
        }
    }
}
