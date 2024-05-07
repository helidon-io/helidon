/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.microprofile.cdi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

/**
 * Annotates a CDI bean method that shall be executed on a new thread.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Inherited
@InterceptorBinding
public @interface RunOnThead {

    /**
     * Type of thread to use for invocation.
     */
    enum ThreadType {
        /**
         * A thread of type platform (non-virtual).
         */
        PLATFORM,

        /**
         * A thread of type virtual.
         */
        VIRTUAL,

        /**
         * A named executor lookup using CDI.
         */
        EXECUTOR
    }

    /**
     * Thread type for invocation.
     *
     * @return thread type
     */
    @Nonbinding
    ThreadType value() default ThreadType.PLATFORM;

    /**
     * Waiting timeout.
     *
     * @return waiting timeout
     */
    @Nonbinding
    long timeout() default 10000L;

    /**
     * Waiting time unit.
     *
     * @return waiting time unit
     */
    @Nonbinding
    TimeUnit unit() default TimeUnit.MILLISECONDS;

    /**
     * Name of executor when {@link ThreadType#EXECUTOR} is selected.
     *
     * @return executor name
     */
    @Nonbinding
    String executorName() default "";
}
