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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.common.configurable.ThreadPoolSupplier;
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
