/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.microprofile.faulttolerance;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A retry policy that increases the delay time following an exponential sequence.
 * Allowed elements that are also annotated with {@code @Retry}.
 * Expected sequence if factor is 2: initial delay, 2 * initial delay + jitter, 4 * initial delay + jitter,
 * 8 * initial delay + jitter, etc. {@code maxDelay} is used to prevent endless waiting.
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface RetryExponentialBackoff {

    /**
     * Initial Delay. Default is 0.
     *
     * @return Milliseconds long
     */
    long initialDelay() default 2;

    /**
     * Multiplication factor. Default is 2.
     *
     * @return multiplication factor int
     */
    int factor() default 2;

}
