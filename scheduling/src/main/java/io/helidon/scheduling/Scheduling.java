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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.helidon.common.configurable.ScheduledThreadPoolSupplier;

/**
 * Scheduling periodically executed task with specified fixed rate or cron expression.
 *
 * <pre>{@code
 * Scheduling.fixedRate()
 *      .delay(2)
 *      .task(inv -> System.out.println("Executed every 2 seconds"))
 *      .build();
 * }</pre>
 *
 * <pre>{@code
 * Scheduling.cron()
 *      .expression("0 45 9 ? * *")
 *      .task(inv -> System.out.println("Executed every day at 9:45"))
 *      .build()
 * }</pre>
 */
public class Scheduling {

    private Scheduling() {
        //hidden constructor
    }

    /**
     * Build a task executed periodically at a fixed rate.
     *
     * @return this builder
     */
    public static FixedRateBuilder fixedRateBuilder() {
        return new FixedRateBuilder();
    }

    /**
     * Build a task executed periodically according to provided cron expression.
     *
     * @return this builder
     */
    public static CronBuilder cronBuilder() {
        return new CronBuilder();
    }

    /**
     * Builder for task executed periodically at a fixed rate.
     */
    public static final class FixedRateBuilder implements io.helidon.common.Builder<Task> {

        private ScheduledExecutorService executorService;
        private long initialDelay = 0;
        private Long delay;
        private TimeUnit timeUnit = TimeUnit.SECONDS;
        private ScheduledConsumer<FixedRateInvocation> task;

        private FixedRateBuilder() {
            //hidden constructor
        }

        /**
         * Custom {@link ScheduledExecutorService ScheduledExecutorService} used for executing scheduled task.
         *
         * @param executorService custom ScheduledExecutorService
         * @return this builder
         */
        public FixedRateBuilder executor(ScheduledExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        /**
         * Initial delay of the first invocation. Time unit is by default {@link TimeUnit#SECONDS},
         * can be specified with {@link FixedRateBuilder#timeUnit(java.util.concurrent.TimeUnit) timeUnit()}.
         *
         * @param initialDelay initial delay value
         * @return this builder
         */
        public FixedRateBuilder initialDelay(long initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }

        /**
         * Fixed rate delay between each invocation. Time unit is by default {@link TimeUnit#SECONDS},
         * can be specified with {@link FixedRateBuilder#timeUnit(java.util.concurrent.TimeUnit)}.
         *
         * @param delay delay between each invocation
         * @return this builder
         */
        public FixedRateBuilder delay(long delay) {
            this.delay = delay;
            return this;
        }

        /**
         * Task to be scheduled for execution.
         *
         * @param task scheduled for execution
         * @return this builder
         */
        public FixedRateBuilder task(ScheduledConsumer<FixedRateInvocation> task) {
            this.task = task;
            return this;
        }

        /**
         * {@link TimeUnit TimeUnit} used for interpretation of values provided with {@link FixedRateBuilder#delay(long)}
         * and {@link FixedRateBuilder#initialDelay(long)}.
         *
         * @param timeUnit for interpreting delay and in {@link FixedRateBuilder#delay(long)}
         *                 and {@link FixedRateBuilder#initialDelay(long)}
         * @return this builder
         */
        public FixedRateBuilder timeUnit(TimeUnit timeUnit) {
            this.timeUnit = timeUnit;
            return this;
        }

        @Override
        public Task build() {
            if (task == null) {
                throw new SchedulingException("No task to execute provided!");
            }
            if (delay == null) {
                throw new SchedulingException("No delay provided!");
            }

            if (executorService == null) {
                executorService = ScheduledThreadPoolSupplier.builder()
                        .threadNamePrefix("scheduled-")
                        .build()
                        .get();
            }
            return new FixedRateTask(executorService, initialDelay, delay, timeUnit,
                    invocation -> task.run((FixedRateInvocation) invocation));
        }
    }

    /**
     * Builder for task executed periodically according to provided cron expression.
     */
    public static final class CronBuilder implements io.helidon.common.Builder<Task> {

        static final String DEFAULT_THREAD_NAME_PREFIX = "scheduled-";

        private ScheduledExecutorService executorService;
        private String cronExpression;
        private boolean concurrentExecution = true;
        private ScheduledConsumer<CronInvocation> task;

        private CronBuilder() {
            //hidden constructor
        }

        /**
         * Custom {@link ScheduledExecutorService ScheduledExecutorService} used for executing scheduled task.
         *
         * @param executorService custom ScheduledExecutorService
         * @return this builder
         */
        public CronBuilder executor(ScheduledExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        /**
         * Cron expression for specifying period of execution.
         * <p>
         * <b>Examples:</b>
         * <ul>
         * <li>{@code 0/2 * * * * ? *} - Every 2 seconds</li>
         * <li>{@code 0 45 9 ? * *} - Every day at 9:45</li>
         * <li>{@code 0 15 8 ? * MON-FRI} - Every workday at 8:15</li>
         * </ul>
         *
         * @param cronExpression cron expression
         * @return this builder
         */
        public CronBuilder expression(String cronExpression) {
            this.cronExpression = cronExpression;
            return this;
        }

        /**
         * Allow concurrent execution if previous task didn't finish before next execution.
         * Default value is {@code true}.
         *
         * @param allowConcurrentExecution true for allow concurrent execution.
         * @return this builder
         */
        public CronBuilder concurrentExecution(boolean allowConcurrentExecution) {
            this.concurrentExecution = allowConcurrentExecution;
            return this;
        }

        /**
         * Task to be scheduled for execution.
         *
         * @param task scheduled for execution
         * @return this builder
         */
        public CronBuilder task(ScheduledConsumer<CronInvocation> task) {
            this.task = task;
            return this;
        }

        @Override
        public Task build() {
            if (task == null) {
                throw new SchedulingException("No task to execute provided!");
            }
            if (cronExpression == null) {
                throw new SchedulingException("No CRON expression provided!");
            }

            if (executorService == null) {
                executorService = ScheduledThreadPoolSupplier.builder()
                        .threadNamePrefix(DEFAULT_THREAD_NAME_PREFIX)
                        .build()
                        .get();
            }

            CronTask task = new CronTask(executorService, cronExpression, concurrentExecution,
                    invocation -> this.task.run((CronInvocation) invocation));

            return task;
        }
    }
}
