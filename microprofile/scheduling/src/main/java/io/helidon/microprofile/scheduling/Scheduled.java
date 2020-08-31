/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Scheduled to be invoked periodically.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface Scheduled {

    /**
     * Cron expression specifying period for invocation.
     *
     * @return cron expression as string
     */
    String cron() default "";

    /**
     * Fixed rate at milliseconds.
     *
     * @return period of invocation in milliseconds
     */
    long fixedRate() default -1;
}
