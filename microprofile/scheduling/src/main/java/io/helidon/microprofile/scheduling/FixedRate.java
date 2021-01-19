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

package io.helidon.microprofile.scheduling;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Scheduled to be invoked periodically at fixed rate.
 * Value is interpreted as seconds by default, can be overridden by {@link #timeUnit()}.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface FixedRate {

    /**
     * Look for fixed rate value in the scheduling config.
     */
    Long EXTERNALLY_CONFIGURED = Long.MIN_VALUE;

    /**
     * Fixed rate for periodical invocation.
     */
    long value() default Long.MIN_VALUE;

    /**
     * Initial delay of the first invocation.
     */
    long initialDelay() default 0;

    /**
     * Time unit for interpreting supplied values.
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
