/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.helidon.common.LazyValue;
import io.helidon.common.configurable.ScheduledThreadPoolSupplier;

import static io.helidon.scheduling.FixedRate.DelayType.SINCE_PREVIOUS_START;

/**
 * Annotations for Scheduling.
 * <p>
 * Scheduling can be done in imperative style as follows:
 * <p>
 * Fixed Rate:
 * <pre>{@code
 * FixedRate.builder()
 *      .interval(Duration.ofSeconds(2))
 *      .task(inv -> System.out.println("Executed every 2 seconds"))
 *      .build();
 * }</pre>
 *
 * Cron expression:
 * <pre>{@code
 * Cron.builder()
 *      .expression("0 45 9 ? * *")
 *      .task(inv -> System.out.println("Executed every day at 9:45"))
 *      .build()
 * }</pre>
 * <p>
 * The same can be achieved in a declarative style as follows:
 * <p>
 * Fixed Rate:
 * <pre>{@code
 * @Scheduling.FixedRate("PT2M")
 * void scheduledMethod() {
 *     System.out.println("Executed every 2 seconds");
 * }
 * }</pre>
 * Cron expression:
 * <pre>{@code
 * @Scheduling.Cron("0 45 9 ? * *")
 * void scheduledMethod() {
 *     System.out.println("Executed every day at 9:45");
 * }
 * }</pre>
 * <p>
 * All other methods and types in this class are now deprecated.
 *
 * @see io.helidon.scheduling.Cron#builder()
 * @see io.helidon.scheduling.FixedRate#builder()
 * @see io.helidon.scheduling.Scheduling.Cron
 * @see io.helidon.scheduling.Scheduling.FixedRate
 */
public final class Scheduling {
    static final String DEFAULT_THREAD_NAME_PREFIX = "scheduled-";
    static final LazyValue<ScheduledExecutorService> DEFAULT_SCHEDULER = LazyValue.create(() -> {
        return ScheduledThreadPoolSupplier.builder()
                .threadNamePrefix(DEFAULT_THREAD_NAME_PREFIX)
                .build()
                .get();
    });

    private Scheduling() {
        //hidden constructor
    }

    /**
     * Build a task executed periodically at a fixed rate.
     *
     * @return this builder
     * @deprecated use {@link io.helidon.scheduling.FixedRate#builder()} instead
     */
    @Deprecated(since = "4.0.2", forRemoval = true)
    public static FixedRateBuilder fixedRateBuilder() {
        return new FixedRateBuilder();
    }

    /**
     * Build a task executed periodically at a fixed rate.
     *
     * @return this builder
     * @deprecated use {@link io.helidon.scheduling.FixedRate#builder()} instead
     */
    @Deprecated(since = "4.0.2", forRemoval = true)
    public static FixedRateConfig.Builder fixedRate() {
        return io.helidon.scheduling.FixedRate.builder();
    }

    /**
     * Build a task executed periodically according to provided cron expression.
     *
     * @return this builder
     * @deprecated use {@link io.helidon.scheduling.Cron#builder()} instead
     */
    @Deprecated(since = "4.0.2", forRemoval = true)
    public static CronBuilder cronBuilder() {
        return new CronBuilder();
    }

    /**
     * Build a task executed periodically according to provided cron expression.
     *
     * @return this builder
     * @deprecated use {@link io.helidon.scheduling.Cron#builder()} instead
     */
    @Deprecated(since = "4.0.2", forRemoval = true)
    public static CronConfig.Builder cron() {
        return io.helidon.scheduling.Cron.builder();
    }

    /**
     * Scheduled to be invoked periodically at fixed rate. Fixed rate tasks are never invoked concurrently.
     *
     * @see #value()
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Target(ElementType.METHOD)
    public @interface FixedRate {
        /**
         * Fixed interval for periodical invocation.
         * The value is parsed using {@link java.time.Duration#parse(CharSequence)}.
         * <p>
         * Examples:
         * <ul>
         *     <li>{@code PT0.1S} - 0.1 seconds</li>
         *     <li>{@code PT10M} - 10 minutes</li>
         * </ul>
         *
         * @return fixed rate interval in {@link java.time.Duration} format
         */
        String value();

        /**
         * Initial delay of the first invocation.
         * Uses {@link java.time.Duration} format.
         *
         * @return initial delay
         * @see #value()
         */
        String delayBy() default "PT0S";

        /**
         * Whether the interval should be calculated from the start or end of the previous task.
         *
         * @return delay type
         */
        io.helidon.scheduling.FixedRate.DelayType delayType() default SINCE_PREVIOUS_START;
    }

    /**
     * Marks the method to be invoked periodically according to supplied cron expression.
     * <br>
     * Cron expression format:
     * <pre>{@code
     *  <seconds> <minutes> <hours> <day-of-month> <month> <day-of-week> <year>
     * }</pre>
     * <br>
     * <table>
     *  <caption><b>Cron expression fields</b></caption>
     *  <tr>
     *      <th>Order</th>
     *      <th>Name</th>
     *      <th>Supported values</th>
     *      <th>Supported field format</th>
     *      <th>Optional</th>
     *  </tr>
     *  <tbody>
     *      <tr>
     *          <td>1</td>
     *          <td>seconds</td>
     *          <td>0-59</td>
     *          <td>CONST, LIST, RANGE, WILDCARD, INCREMENT</td>
     *          <td>false</td>
     *      </tr>
     *      <tr>
     *          <td>2</td>
     *          <td>minutes</td>
     *          <td>0-59</td>
     *          <td>CONST, LIST, RANGE, WILDCARD, INCREMENT</td>
     *          <td>false</td>
     *      </tr>
     *      <tr>
     *          <td>3</td>
     *          <td>hours</td>
     *          <td>0-23</td>
     *          <td>CONST, LIST, RANGE, WILDCARD, INCREMENT</td>
     *          <td>false</td>
     *      </tr>
     *      <tr>
     *          <td>4</td>
     *          <td>day-of-month</td>
     *          <td>1-31</td>
     *          <td>CONST, LIST, RANGE, WILDCARD, INCREMENT, ANY, LAST, WEEKDAY</td>
     *          <td>false</td>
     *      </tr>
     *      <tr>
     *          <td>5</td>
     *          <td>month</td>
     *          <td>1-12 or JAN-DEC</td>
     *          <td>CONST, LIST, RANGE, WILDCARD, INCREMENT</td>
     *          <td>false</td>
     *      </tr>
     *      <tr>
     *          <td>6</td>
     *          <td>day-of-week</td>
     *          <td>1-7 or SUN-SAT</td>
     *          <td>CONST, LIST, RANGE, WILDCARD, INCREMENT, ANY, NTH, LAST</td>
     *          <td>false</td>
     *      </tr>
     *      <tr>
     *          <td>7</td>
     *          <td>year</td>
     *          <td>1970-2099</td>
     *          <td>CONST, LIST, RANGE, WILDCARD, INCREMENT</td>
     *          <td>true</td>
     *      </tr>
     * </tbody>
     * </table>
     * <br>
     * <table>
     *  <caption><b>Field formats</b></caption>
     *  <tr>
     *      <th>Name</th>
     *      <th>Regex format</th>
     *      <th>Example</th>
     *      <th>Description</th>
     *  </tr>
     *  <tbody>
     *      <tr>
     *          <td>CONST</td>
     *          <td>\d+</td>
     *          <td>12</td>
     *          <td>exact value</td>
     *      </tr>
     *      <tr>
     *          <td>LIST</td>
     *          <td>\d+,\d+(,\d+)*</td>
     *          <td>1,2,3,4</td>
     *          <td>list of constants</td>
     *      </tr>
     *      <tr>
     *          <td>RANGE</td>
     *          <td>\d+-\d+</td>
     *          <td>15-30</td>
     *          <td>range of values from-to</td>
     *      </tr>
     *      <tr>
     *          <td>WILDCARD</td>
     *          <td>\*</td>
     *          <td>*</td>
     *          <td>all values withing the field</td>
     *      </tr>
     *      <tr>
     *          <td>INCREMENT</td>
     *          <td>\d+\/\d+</td>
     *          <td>0/5</td>
     *          <td>inital number / increments, 2/5 means 2,7,9,11,16,...</td>
     *      </tr>
     *      <tr>
     *          <td>ANY</td>
     *          <td>\?</td>
     *          <td>?</td>
     *          <td>any day(apply only to day-of-week and day-of-month)</td>
     *      </tr>
     *      <tr>
     *          <td>NTH</td>
     *          <td>\#</td>
     *          <td>1#3</td>
     *          <td>nth day of the month, 2#3 means third monday of the month</td>
     *      </tr>
     *      <tr>
     *          <td>LAST</td>
     *          <td>\d*L(\+\d+|\-\d+)?</td>
     *          <td>3L-3</td>
     *          <td>last day of the month in day-of-month or last nth day in the day-of-week</td>
     *      </tr>
     *      <tr>
     *          <td>WEEKDAY</td>
     *          <td>\#</td>
     *          <td>1#3</td>
     *          <td>nearest weekday of the nth day of month, 1W is the first monday of the week</td>
     *      </tr>
     * </tbody>
     * </table>
     * <br>
     * <table>
     *  <caption><b>Examples</b></caption>
     *  <tr>
     *      <th>Cron expression</th>
     *      <th>Description</th>
     *  </tr>
     *  <tbody>
     *      <tr>
     *          <td>* * * * * ?</td>
     *          <td>Every second</td>
     *      </tr>
     *      <tr>
     *          <td>0/2 * * * * ? *</td>
     *          <td>Every 2 seconds</td>
     *      </tr>
     *      <tr>
     *          <td>0 45 9 ? * *</td>
     *          <td>Every day at 9:45</td>
     *      </tr>
     *      <tr>
     *          <td>0 15 8 ? * MON-FRI</td>
     *          <td>Every workday at 8:15</td>
     *      </tr>
     * </tbody>
     * </table>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Target(ElementType.METHOD)
    public @interface Cron {
        /**
         * Cron expression specifying period for invocation.
         *
         * @return cron expression as string
         */
        String value();

        /**
         * When true, next task is started even if previous didn't finish yet.
         *
         * @return true for allowing concurrent invocation
         */
        boolean concurrent() default true;
    }

    /**
     * Builder for task executed periodically at a fixed rate.
     *
     * @deprecated use {@link io.helidon.scheduling.FixedRateConfig.Builder} instead
     */
    @Deprecated(since = "4.0.2", forRemoval = true)
    public static final class FixedRateBuilder implements io.helidon.common.Builder<FixedRateBuilder, Task> {

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
            ChronoUnit chronoUnit = timeUnit.toChronoUnit();
            return io.helidon.scheduling.FixedRate.builder()
                    .executor(executorService)
                    .delayBy(Duration.of(initialDelay, chronoUnit))
                    .interval(Duration.of(delay, chronoUnit))
                    .delayType(io.helidon.scheduling.FixedRate.DelayType.SINCE_PREVIOUS_START)
                    .task(task)
                    .build();
        }
    }

    /**
     * Builder for task executed periodically according to provided cron expression.
     *
     * @deprecated use {@link io.helidon.scheduling.CronConfig.Builder} instead
     */
    @Deprecated(since = "4.0.2", forRemoval = true)
    public static final class CronBuilder implements io.helidon.common.Builder<CronBuilder, Task> {

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

            return io.helidon.scheduling.Cron.builder()
                    .executor(executorService)
                    .expression(cronExpression)
                    .concurrentExecution(concurrentExecution)
                    .task(task)
                    .build();

        }
    }
}
