/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.Services;

import static java.lang.System.Logger.Level.ERROR;

/**
 * System-wide fault tolerance configuration and access to a customized sequence of fault tolerance handlers.
 * <p>
 * Fault tolerance provides the following features:
 * <ul>
 *     <li>{@link io.helidon.faulttolerance.Bulkhead} - limit number of parallel requests to a resource</li>
 *     <li>{@link io.helidon.faulttolerance.CircuitBreaker} - stop trying to request a failing resource until it becomes
 *     available</li>
 *     <li>{@link io.helidon.faulttolerance.Fallback} - fall back to another supplier of result in case the usual one
 *     fails</li>
 *     <li>{@link io.helidon.faulttolerance.Retry} - try to call a supplier again if invocation fails</li>
 *     <li>{@link io.helidon.faulttolerance.Timeout} - time out a request if it takes too long</li>
 * </ul>
 *
 * @see #executor()
 * @see #builder()
 */
public final class FaultTolerance {

    /**
     * Config key to enable metrics in Fault Tolerance. This flag can be overridden by
     * each FT command builder. See for example {@link BulkheadConfigBlueprint#enableMetrics()}.
     * All metrics are disabled by default.
     */
    public static final String FT_METRICS_DEFAULT_ENABLED = "ft.metrics.default-enabled";

    static final double WEIGHT_RETRY = 10;
    static final double WEIGHT_BULKHEAD = 20;
    static final double WEIGHT_CIRCUIT_BREAKER = 30;
    static final double WEIGHT_TIMEOUT = 40;
    static final double WEIGHT_ASYNC = 50;
    static final double WEIGHT_FALLBACK = 60;

    private static final System.Logger LOGGER = System.getLogger(FaultTolerance.class.getName());

    private static final AtomicReference<LazyValue<ExecutorService>> EXECUTOR = new AtomicReference<>();
    private static final AtomicReference<Config> CONFIG = new AtomicReference<>();

    static {
        EXECUTOR.set(LazyValue.create(() -> ThreadPoolSupplier.builder()
                .threadNamePrefix("helidon-ft-")
                .virtualThreads(true)
                .build()
                .get()));
    }

    private FaultTolerance() {
    }

    /**
     * Configure Helidon wide defaults from a config instance.
     * The default is now to use {@link io.helidon.service.registry.Services#get(Class)} to get
     * a configuration. This method will work as it used to, but fallback will always
     * be to the config instance provided by service registry.
     *
     * @param config config to read fault tolerance configuration
     */
    @Deprecated(forRemoval = true, since = "4.3.0")
    public static void config(Config config) {
        CONFIG.set(config);
    }

    /**
     * Configure Helidon wide executor service for Fault Tolerance.
     *
     * @param executor executor service to use
     */
    public static void executor(Supplier<? extends ExecutorService> executor) {
        EXECUTOR.set(LazyValue.create(executor::get));
    }

    /**
     * A builder to configure a customized sequence of fault tolerance handlers.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A typed builder to configure a customized sequence of fault tolerance handlers.
     *
     * @param <T> type of result
     * @return a new builder
     */
    public static <T> TypedBuilder<T> typedBuilder() {
        return new TypedBuilder<>();
    }

    /**
     * Converts a {@code Runnable} into another that sleeps for {@code millis} before
     * executing. Simulates a scheduled executor when using VTs.
     *
     * @param runnable the runnable
     * @param millis the time to sleep
     * @return the new runnable
     */
    public static Runnable toDelayedRunnable(Runnable runnable, long millis) {
        return () -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                // should never be interrupted
                LOGGER.log(ERROR, "Delayed runnable was unexpectedly interrupted");
            }
            runnable.run();
        };
    }

    /**
     * Converts a {@code Callable} into another that sleeps for {@code millis} before
     * executing. Simulates a scheduled executor when using VTs.
     *
     * @param callable the callable
     * @param millis the time to sleep
     * @return the new callable
     * @param <T> type of value returned
     */
    public static <T> Callable<T> toDelayedCallable(Callable<T> callable, long millis) {
        return () -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                // should never be interrupted
                LOGGER.log(ERROR, "Delayed callable was unexpectedly interrupted");
            }
            return callable.call();
        };
    }

    static LazyValue<? extends ExecutorService> executor() {
        return EXECUTOR.get();
    }

    static Config config() {
        var config = CONFIG.get();
        if (config == null) {
            return Services.get(Config.class);
        }
        return config;
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

    abstract static class BaseBuilder<B extends BaseBuilder<B>> {
        /**
         * Add a bulkhead to the list.
         *
         * @param bulkhead bulkhead handler
         * @return updated builder instance
         */
        public B addBulkhead(io.helidon.faulttolerance.Bulkhead bulkhead) {
            add(bulkhead);
            return me();
        }

        /**
         * Add a circuit breaker to the list.
         *
         * @param breaker circuit breaker handler
         * @return updated builder instance
         */
        public B addBreaker(io.helidon.faulttolerance.CircuitBreaker breaker) {
            add(breaker);
            return me();
        }

        /**
         * Add a timeout to the list.
         *
         * @param timeout timeout handler
         * @return updated builder instance
         */
        public B addTimeout(io.helidon.faulttolerance.Timeout timeout) {
            add(timeout);
            return me();
        }

        /**
         * Add a retry to the list.
         *
         * @param retry retry handler
         * @return updated builder instance
         */
        public B addRetry(io.helidon.faulttolerance.Retry retry) {
            add(retry);
            return me();
        }

        /**
         * Add a handler to the list. This may be a custom handler or one of the predefined ones.
         *
         * @param ft fault tolerance handler to add
         * @return updated builder instance
         */
        public abstract B add(FtHandler ft);

        @SuppressWarnings("unchecked")
        private B me() {
            return (B) this;
        }
    }

    /**
     * A builder used for fault tolerance handlers that require a specific type to be used, such as
     * {@link io.helidon.faulttolerance.Fallback}.
     * An instance is returned from
     * {@link FaultTolerance.Builder#addFallback(io.helidon.faulttolerance.Fallback)}.
     *
     * @param <T> type of result
     */
    public static class TypedBuilder<T> extends BaseBuilder<TypedBuilder<T>>
            implements io.helidon.common.Builder<TypedBuilder<T>, FtHandlerTyped<T>> {
        private final List<FtHandlerTyped<T>> fts = new LinkedList<>();

        private TypedBuilder() {
        }

        @Override
        public FtHandlerTyped<T> build() {
            return new FtHandlerTypedImpl<T>(fts);
        }

        @Override
        public TypedBuilder<T> add(FtHandler ft) {
            fts.add(new TypedWrapper(ft));
            return this;
        }

        /**
         * Add a fallback to the list of handlers.
         *
         * @param fallback fallback instance
         * @return updated builder instance
         */
        public TypedBuilder<T> addFallback(io.helidon.faulttolerance.Fallback<T> fallback) {
            fts.add(fallback);
            return this;
        }

        private TypedBuilder<T> builder(Builder builder) {
            builder.fts
                    .forEach(it -> {
                        fts.add(new TypedWrapper(it));
                    });
            return this;
        }

        private static class FtHandlerTypedImpl<T> implements FtHandlerTyped<T> {
            private final List<FtHandlerTyped<T>> validFts;

            private FtHandlerTypedImpl(List<FtHandlerTyped<T>> validFts) {
                this.validFts = new LinkedList<>(validFts);
            }

            @Override
            public T invoke(Supplier<? extends T> supplier) {
                Supplier<? extends T> next = supplier;

                for (FtHandlerTyped<T> validFt : validFts) {
                    final var finalNext = next;
                    next = () -> validFt.invoke(finalNext);
                }

                return next.get();
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                for (int i = validFts.size() - 1; i >= 0; i--) {
                    sb.append(validFts.get(i).toString());
                    sb.append("\n");
                }
                return sb.toString();
            }
        }

        private class TypedWrapper implements FtHandlerTyped<T> {
            private final FtHandler handler;

            private TypedWrapper(FtHandler handler) {
                this.handler = handler;
            }

            @Override
            public T invoke(Supplier<? extends T> supplier) {
                return handler.invoke(supplier);
            }

            @Override
            public String toString() {
                return handler.getClass().getSimpleName();
            }
        }
    }

    /**
     * A builder used for setting up a customized list of fault tolerance handlers.
     */
    public static class Builder extends BaseBuilder<Builder> implements io.helidon.common.Builder<Builder, FtHandler> {
        private final List<FtHandler> fts = new LinkedList<>();

        private Builder() {
        }

        @Override
        public FtHandler build() {
            return new FtHandlerImpl(fts);
        }

        /**
         * Add a fallback to the list of handlers.
         *
         * @param fallback fallback instance
         * @param <U>      type of future
         * @return a new typed builder instance
         */
        public <U> TypedBuilder<U> addFallback(io.helidon.faulttolerance.Fallback<U> fallback) {
            return new TypedBuilder<U>()
                    .builder(this)
                    .addFallback(fallback);
        }

        @Override
        public Builder add(FtHandler ft) {
            fts.add(ft);
            return this;
        }

        private static class FtHandlerImpl implements FtHandler {
            private final List<FtHandler> validFts;
            private final String name = "FtHandler-" + System.identityHashCode(this);

            private FtHandlerImpl(List<FtHandler> validFts) {
                this.validFts = new LinkedList<>(validFts);
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public <T> T invoke(Supplier<? extends T> supplier) {
                Supplier<? extends T> next = supplier;

                for (FtHandler validFt : validFts) {
                    final var finalNext = next;
                    next = () -> validFt.invoke(finalNext);
                }

                return next.get();
            }
        }
    }
}
