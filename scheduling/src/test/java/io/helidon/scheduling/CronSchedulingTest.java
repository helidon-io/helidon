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
import java.util.ArrayList;
import java.util.List;
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
public class CronSchedulingTest {

    static final long ERROR_MARGIN_MILLIS = 500;

    @Test
    void cronTest() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        IntervalMeter meter = new IntervalMeter();
        Scheduling.cron()
                .executor(executorService)
                .expression("0/2 * * * * ? *")
                .task(cronInvocation -> meter
                        .start()
                        .sleep(200, TimeUnit.MILLISECONDS)
                        .end())
                .build();

        meter.awaitTill(2, 20, TimeUnit.SECONDS);
        executorService.shutdownNow();
        meter.assertAverageDuration(Duration.ofSeconds(2), Duration.ofMillis(ERROR_MARGIN_MILLIS));
    }

    @Test
    void cronConcurrencyDisabled() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        IntervalMeter meter = new IntervalMeter();
        Scheduling.cron()
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
        executorService.shutdownNow();
        // every 1 sec + 2 secs sleeping
        meter.assertAverageDuration(Duration.ofSeconds(3), Duration.ofMillis(ERROR_MARGIN_MILLIS));
        meter.assertNonConcurrent();
    }

    @Test
    void cronConcurrencyEnabled() {
        IntervalMeter meter = new IntervalMeter();
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            Scheduling.cron()
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
            executorService.shutdownNow();
        }
        // every 1 sec + 2 secs sleeping
        meter.assertAverageDuration(Duration.ofSeconds(1), Duration.ofMillis(ERROR_MARGIN_MILLIS));
    }

    @Test
    void cronDefaultExecutor() {
        IntervalMeter meter = new IntervalMeter();
        List<String> threadNames = new ArrayList<>();
        Task task = Scheduling.cron()
                .expression("0/3 * * * * ? *")
                .task(cronInvocation -> meter
                        .start()
                        .doSomething(() -> threadNames.add(Thread.currentThread().getName()))
                        .sleep(200, TimeUnit.MILLISECONDS)
                        .end())
                .build();

        meter.awaitTill(2, 20, TimeUnit.SECONDS);
        task.executor().shutdown();
        meter.assertAverageDuration(Duration.ofSeconds(3), Duration.ofMillis(ERROR_MARGIN_MILLIS));
        threadNames.stream()
                .map(s -> s.substring(0, Scheduling.CronBuilder.DEFAULT_THREAD_NAME_PREFIX.length()))
                .forEach(s -> assertThat(s, Matchers.equalTo(Scheduling.CronBuilder.DEFAULT_THREAD_NAME_PREFIX)));
        assertThat(threadNames.size(), Matchers.greaterThan(0));
    }

    @Test
    void cronWrongExpression() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            IntervalMeter meter = new IntervalMeter();
            Assertions.assertThrows(IllegalArgumentException.class, () -> Scheduling.cron()
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
    void cronMissingTask() {
        ScheduledExecutorService executorService = ScheduledThreadPoolSupplier.create().get();
        try {
            Assertions.assertThrows(SchedulingException.class, () -> Scheduling.cron()
                    .executor(executorService)
                    .expression("* * * * * ? *")
                    .build());
        } finally {
            executorService.shutdown();
        }
    }

}
