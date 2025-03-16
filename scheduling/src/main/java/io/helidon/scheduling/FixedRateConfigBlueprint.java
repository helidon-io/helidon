/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
import java.util.concurrent.TimeUnit;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

@Prototype.Blueprint(decorator = FixedRateDecorator.class)
@Prototype.Configured
interface FixedRateConfigBlueprint extends TaskConfigBlueprint, Prototype.Factory<FixedRate> {

    /**
     * Initial delay of the first invocation. Time unit is by default {@link java.util.concurrent.TimeUnit#SECONDS},
     * can be specified with
     * {@link io.helidon.scheduling.FixedRateConfig.Builder#timeUnit(java.util.concurrent.TimeUnit) timeUnit()}.
     *
     * @return initial delay value
     * @deprecated use {@link io.helidon.scheduling.FixedRateConfig#delayBy()} instead
     */
    @Option.Configured
    @Option.Deprecated("delayBy")
    @Option.Required
    @Option.Decorator(FixedRateDecorator.InitialDelayDecorator.class)
    @Deprecated(forRemoval = true, since = "4.2.0")
    long initialDelay();

    /**
     * Fixed rate delay between each invocation. Time unit is by default {@link java.util.concurrent.TimeUnit#SECONDS},
     * can be specified with {@link io.helidon.scheduling.FixedRateConfig.Builder#timeUnit(java.util.concurrent.TimeUnit)}.
     *
     * @return delay between each invocation
     * @deprecated use {@link io.helidon.scheduling.FixedRateConfig#interval()} instead
     */
    @Option.Configured
    @Option.Deprecated("rate")
    @Option.Required
    @Option.Decorator(FixedRateDecorator.DelayDecorator.class)
    @Deprecated(forRemoval = true, since = "4.2.0")
    long delay();

    /**
     * {@link java.util.concurrent.TimeUnit TimeUnit} used for interpretation of values provided with
     * {@link io.helidon.scheduling.FixedRateConfig.Builder#delay(long)}
     * and {@link io.helidon.scheduling.FixedRateConfig.Builder#initialDelay(long)}.
     *
     * @return time unit for interpreting values
     *         in {@link io.helidon.scheduling.FixedRateConfig.Builder#delay(long)}
     *         and {@link io.helidon.scheduling.FixedRateConfig.Builder#initialDelay(long)}
     * @deprecated as duration is used for new options, this option is not needed
     */
    @Option.Configured
    @Option.Default("TimeUnit.SECONDS")
    @Option.Decorator(FixedRateDecorator.TimeUnitDecorator.class)
    @Deprecated(forRemoval = true, since = "4.2.0")
    TimeUnit timeUnit();

    /**
     * Initial delay of the first invocation.
     *
     * @return initial delay duration
     */
    @Option.Configured
    @Option.Default("PT0S")
    Duration delayBy();

    /**
     * Fixed interval between each invocation.
     *
     * @return interval between each invocation
     */
    @Option.Configured
    @Option.Required
    Duration interval();

    /**
     * Configure whether the interval between the invocations should be calculated from the time when previous task
     * started or ended.
     * Delay type is by default {@link FixedRate.DelayType#SINCE_PREVIOUS_START}.
     *
     * @return delay type
     */
    @Option.Configured
    @Option.Default("SINCE_PREVIOUS_START")
    FixedRate.DelayType delayType();

    /**
     * Task to be scheduled for execution.
     *
     * @return scheduled for execution
     */
    ScheduledConsumer<FixedRateInvocation> task();
}
