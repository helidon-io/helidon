/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.service.registry.Interception;

/**
 * Fault tolerance annotations and types for Helidon Declarative.
 */
public final class Ft {
    private Ft() {
    }

    /**
     * The annotated method (or all methods on annotated type) will be retried according to the configuration.
     */
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Interception.Intercepted
    public @interface Retry {
        /**
         * If named, an attempt will be made to find a named {@link io.helidon.faulttolerance.Retry} instance in service
         * registry. If not found, a new retry will be created based on values on this annotation.
         *
         * @return name of this retry
         */
        String name() default "";

        /**
         * Number of calls (first try + retries).
         *
         * @return number of desired calls, must be 1 (means no retries) or higher.
         */
        int calls() default 3;

        /**
         * Duration to delay
         * Defaults to {@code 200} milliseconds.
         *
         * @return duration to delay, such as {@code PT1S} (1 second)
         * @see java.time.Duration#parse(CharSequence)
         */
        String delay() default "PT0.2S";

        /**
         * Delay retry policy factor. If unspecified (value of {@code -1}), Jitter retry policy would be used, unless
         * jitter time is also unspecified.
         * <p>
         * Default when {@link io.helidon.faulttolerance.Retry.DelayingRetryPolicy} is used is {@code 2}.
         *
         * @return delay factor for delaying retry policy
         */
        double delayFactor() default -1;

        /**
         * Jitter for {@link io.helidon.faulttolerance.Retry.JitterRetryPolicy}. If unspecified (value of {@code -1}),
         * delaying retry policy is used. If both this value, and {@link #delayFactor()} are specified, delaying retry policy
         * would be used.
         *
         * @return jitter duration
         * @see java.time.Duration#parse(CharSequence)
         */
        String jitter() default "PT-1S";

        /**
         * Duration of overall timeout.
         * Defaults to {@code PT1S} (1 second).
         *
         * @return duration of overall timeout
         */
        String overallTimeout() default "PT1S";

        /**
         * These throwables will be considered retriable.
         *
         * @return throwable classes to trigger retries
         * @see #skipOn()
         */
        Class<? extends Throwable>[] applyOn() default {};

        /**
         * These throwables will not be considered retriable, all other will.
         *
         * @return throwable classes to skip retries
         * @see #applyOn()
         */
        Class<? extends Throwable>[] skipOn() default {};
    }

    /**
     * The annotated method will fallback to the defined method on failure.
     * <p>
     * The fallback method must have the same signature (types and number of parameters), or have one additional parameter of
     * type {@code Throwable} to receive the last exception thrown.
     * <p>
     * Fault tolerance will add all intermediate exceptions as {@link Throwable#addSuppressed(Throwable)}.
     */
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Interception.Intercepted
    @Target(ElementType.METHOD)
    public @interface Fallback {
        /**
         * Name of the method to fallback to.
         * The method must follow the signature rules defined on this class.
         *
         * @return method name on the same instance (can also be a static method)
         */
        String value();

        /**
         * List of exception types that this fallback should be executed on.
         *
         * @return throwables that trigger fallback
         */
        Class<? extends Throwable>[] applyOn() default {Throwable.class};

        /**
         * List of exceptions that will not execute a fallback. For these exceptions, the throwable will be propagated
         * to the caller.
         *
         * @return throwables that are re-thrown
         */
        Class<? extends Throwable>[] skipOn() default {};
    }

    /**
     * Runs the annotated method asynchronously.
     */
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Interception.Intercepted
    @Target(ElementType.METHOD)
    public @interface Async {
        /**
         * If named, an attempt will be made to find a named {@link io.helidon.faulttolerance.Async} instance in service
         * registry. If not found, a new async will be created based on values on this annotation.
         *
         * @return name of this async
         */
        String name() default "";

        /**
         * Name of an executor service to use. An attempt will be done to discover the executor service in service registry.
         * If none found, uses the default fault tolerance executor service.
         *
         * @return name of the executor service to use
         */
        String executorName() default "";
    }

    /**
     * The annotated method (or all methods on annotated type) will time out according to the configuration.
     */
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Interception.Intercepted
    public @interface Timeout {
        /**
         * If named, an attempt will be made to find a named {@link io.helidon.faulttolerance.Timeout} instance in service
         * registry. If not found, a new timeout will be created based on values on this annotation.
         *
         * @return name of this timeout
         */
        String name() default "";

        /**
         * Duration of timeout.
         * Defaults to {@code PT10S} (10 seconds).
         *
         * @return timeout duration
         */
        String time() default "PT10S";

        /**
         * Flag to indicate that code must be executed in current thread instead
         * of in an executor's thread. This flag is {@code false} by default.
         *
         * @return whether to run on current thread ({@code true}), or in an executor's thread ({@code false}, default)
         */
        boolean currentThread() default false;
    }

    /**
     * Bulkhead protects a resource that cannot serve unlimited parallel
     * requests.
     * <p>
     * When the limit of parallel execution is reached, requests are enqueued
     * until the queue length is reached. Once both the limit and queue are full,
     * additional attempts to invoke will end with a failed response with
     * {@link BulkheadException}.
     */
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Interception.Intercepted
    @Target(ElementType.METHOD)
    public @interface Bulkhead {
        /**
         * Maximal number of parallel requests going through this bulkhead.
         * When the limit is reached, additional requests are enqueued.
         *
         * @return maximal number of parallel calls, defaults is {@value BulkheadConfigBlueprint#DEFAULT_LIMIT}
         */
        int limit() default BulkheadConfigBlueprint.DEFAULT_LIMIT;

        /**
         * Maximal number of enqueued requests waiting for processing.
         * When the limit is reached, additional attempts to invoke
         * a request will receive a {@link BulkheadException}.
         *
         * @return length of the queue
         */
        int queueLength() default BulkheadConfigBlueprint.DEFAULT_QUEUE_LENGTH;

        /**
         * Name for debugging, error reporting, monitoring.
         *
         * @return name of this bulkhead
         */
        String name() default "";
    }

    /**
     * The annotated method (or all methods on annotated type) will have circuit breaker according to the configuration.
     */
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Interception.Intercepted
    public @interface CircuitBreaker {
        /**
         * If named, an attempt will be made to find a named {@link io.helidon.faulttolerance.CircuitBreaker} instance
         * in service registry. If not found, a new circuit breaker will be created based on values on this annotation.
         *
         * @return name of this retry
         */
        String name() default "";

        /**
         * Delay duration.
         * Defaults to {@code PT5S} (5 seconds).
         *
         * @return duration to wait before transitioning from open to half-open state
         */
        String delay() default "PT5S";

        /**
         * How many failures out of 100 will trigger the circuit to open.
         * This is adapted to the {@link #volume()} used to handle the window of requests.
         * <p>If errorRatio is 40, and volume is 10, 4 failed requests will open the circuit.
         * Default is {@value CircuitBreakerConfigBlueprint#DEFAULT_ERROR_RATIO}.
         *
         * @return percent of failure that trigger the circuit to open
         * @see #volume()
         */
        int errorRatio() default CircuitBreakerConfigBlueprint.DEFAULT_ERROR_RATIO;

        /**
         * Rolling window size used to calculate ratio of failed requests.
         * Default is {@value CircuitBreakerConfigBlueprint#DEFAULT_VOLUME}.
         *
         * @return how big a window is used to calculate error errorRatio
         * @see #errorRatio()
         */
        int volume() default CircuitBreakerConfigBlueprint.DEFAULT_VOLUME;

        /**
         * How many successful calls will close a half-open circuit.
         * Nevertheless, the first failed call will open the circuit again.
         * Default is {@value CircuitBreakerConfigBlueprint#DEFAULT_SUCCESS_THRESHOLD}.
         *
         * @return number of calls
         */
        int successThreshold() default CircuitBreakerConfigBlueprint.DEFAULT_SUCCESS_THRESHOLD;

        /**
         * These throwables will not be considered failures, all other will.
         *
         * @return throwable classes to not be considered a failure
         * @see #applyOn()
         */
        Class<? extends Throwable>[] skipOn() default {};

        /**
         * These throwables will be considered failures.
         *
         * @return throwable classes to be considered a failure
         * @see #skipOn()
         */
        Class<? extends Throwable>[] applyOn() default {};

    }
}
