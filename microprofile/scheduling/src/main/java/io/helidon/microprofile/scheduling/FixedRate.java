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

package io.helidon.microprofile.scheduling;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

import io.helidon.scheduling.FixedRate.DelayType;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Scheduled to be invoked periodically at fixed rate. Fixed rate tasks are never invoked concurrently.
 * Value is interpreted as seconds by default, can be overridden by {@link #timeUnit()}.
 *
 * @deprecated kindly use {@link io.helidon.scheduling.Schedule.FixedRate} instead
 */
@Retention(RUNTIME)
@Target({METHOD})
@Deprecated(forRemoval = true, since = "4.3.0")
public @interface FixedRate {

    /**
     * Fixed rate for periodical invocation.
     *
     * @return fixed rate interval
     */
    long value();

    /**
     * Initial delay of the first invocation.
     *
     * @return initial delay
     */
    long initialDelay() default 0;

    /**
     * Time unit for interpreting supplied values.
     *
     * @return time unit for evaluating supplied values
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * Whether the delay should be calculated from the start or end of the previous task.
     *
     * @return delay type
     */
    DelayType delayType() default DelayType.SINCE_PREVIOUS_START;
}
