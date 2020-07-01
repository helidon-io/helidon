/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;

/**
 * System wide fault tolerance configuration and access to a customized sequence of fault tolerance handlers.
 * <p>
 * Fault tolerance provides the following features:
 * <ul>
 *     <li>{@link io.helidon.faulttolerance.Async} - invoke a blocking synchronous call asynchronously in an executor service</li>
 *     <li>{@link io.helidon.faulttolerance.Bulkhead} - limit number of parallel requests to a resource</li>
 *     <li>{@link io.helidon.faulttolerance.CircuitBreaker} - stop trying to request a failing resource until it becomes
 *     available</li>
 *     <li>{@link io.helidon.faulttolerance.Fallback} - fall back to another supplier of result in case the usual one fails</li>
 *     <li>{@link io.helidon.faulttolerance.Retry} - try to call a supplier again if invocation fails</li>
 *     <li>{@link io.helidon.faulttolerance.Timeout} - time out a request if it takes too long</li>
 * </ul>
 * @see #config(io.helidon.config.Config)
 * @see #scheduledExecutor(java.util.function.Supplier)
 * @see #executor()
 * @see #builder()
 */
public final class FaultTolerance {
    private static final AtomicReference<LazyValue<? extends ScheduledExecutorService>> SCHEDULED_EXECUTOR =
            new AtomicReference<>();
    private static final AtomicReference<LazyValue<ExecutorService>> EXECUTOR = new AtomicReference<>();
    private static final AtomicReference<Config> CONFIG = new AtomicReference<>(Config.empty());

    static {
        SCHEDULED_EXECUTOR.set(LazyValue.create(ScheduledThreadPoolSupplier.builder()
                                                        .threadNamePrefix("ft-schedule-")
                                                        .corePoolSize(2)
                                                        .config(CONFIG.get().get("scheduled-executor"))
                                                        .build()));

        EXECUTOR.set(LazyValue.create(ThreadPoolSupplier.builder()
                                              .threadNamePrefix("ft-")
                                              .config(CONFIG.get().get("executor"))
                                              .build()));
    }

    private FaultTolerance() {
    }

    /**
     * Configure Helidon wide defaults from a config instance.
     *
     * @param config config to read fault tolerance configuration
     */
    public static void config(Config config) {
        CONFIG.set(config);

        SCHEDULED_EXECUTOR.set(LazyValue.create(ScheduledThreadPoolSupplier.create(CONFIG.get().get("scheduled-executor"))));
        EXECUTOR.set(LazyValue.create(ThreadPoolSupplier.create(CONFIG.get().get("executor"))));
    }

    /**
     * Configure Helidon wide executor service for Fault Tolerance.
     *
     * @param executor executor service to use, such as for {@link io.helidon.faulttolerance.Async}
     */
    public static void executor(Supplier<? extends ExecutorService> executor) {
        EXECUTOR.set(LazyValue.create(executor::get));
    }

    /**
     * Configure Helidon wide scheduled executor service for Fault Tolerance.
     *
     * @param executor scheduled executor service to use, such as for {@link io.helidon.faulttolerance.Retry} scheduling
     */
    public static void scheduledExecutor(Supplier<? extends ScheduledExecutorService> executor) {
        SCHEDULED_EXECUTOR.set(LazyValue.create(executor));
    }

    static LazyValue<? extends ExecutorService> executor() {
        return EXECUTOR.get();
    }

    static LazyValue<? extends ScheduledExecutorService> scheduledExecutor() {
        return SCHEDULED_EXECUTOR.get();
    }

    /**
     * A builder to configure a customized sequence of fault tolerance handlers.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    static Config config() {
        return CONFIG.get();
    }

    static Throwable cause(Throwable throwable) {
        if (throwable instanceof CompletionException) {
            return cause(throwable.getCause());
        }
        if (throwable instanceof ExecutionException) {
            return cause(throwable.getCause());
        }
        return throwable;
    }

    abstract static class BaseBuilder<B extends BaseBuilder<B>> {
        @SuppressWarnings("unchecked")
        private B me() {
            return (B) this;
        }

        /**
         * Add a bulkhead to the list.
         *
         * @param bulkhead bulkhead handler
         * @return updated builder instance
         */
        public B addBulkhead(Bulkhead bulkhead) {
            add(bulkhead);
            return me();
        }

        /**
         * Add a circuit breaker to the list.
         *
         * @param breaker circuit breaker handler
         * @return updated builder instance
         */
        public B addBreaker(CircuitBreaker breaker) {
            add(breaker);
            return me();
        }

        /**
         * Add a timeout to the list.
         *
         * @param timeout timeout handler
         * @return updated builder instance
         */
        public B addTimeout(Timeout timeout) {
            add(timeout);
            return me();
        }

        /**
         * Add a retry to the list.
         *
         * @param retry retry handler
         * @return updated builder instance
         */
        public B addRetry(Retry retry) {
            add(retry);
            return me();
        }

        /**
         * Add a handler to the list. This may be a custom handler or one of the predefined ones.
         *
         * @param ft fault tolerance handler to add
         */
        public abstract B add(FtHandler ft);
    }

    /**
     * A builder used for fault tolerance handlers that require a specific type to be used, such as
     * {@link io.helidon.faulttolerance.Fallback}.
     * An instance is returned from {@link io.helidon.faulttolerance.FaultTolerance.Builder#addFallback(Fallback)}.
     *
     * @param <T> type of results handled by {@link io.helidon.common.reactive.Single} or {@link io.helidon.common.reactive.Multi}
     */
    public static class TypedBuilder<T> extends BaseBuilder<TypedBuilder<T>>
            implements io.helidon.common.Builder<FtHandlerTyped<T>> {
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
        public TypedBuilder<T> addFallback(Fallback<T> fallback) {
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
            public Multi<T> invokeMulti(Supplier<? extends Flow.Publisher<T>> supplier) {
                Supplier<? extends Flow.Publisher<T>> next = supplier;

                for (FtHandlerTyped<T> validFt : validFts) {
                    final var finalNext = next;
                    next = () -> validFt.invokeMulti(finalNext);
                }

                return Multi.create(next.get());
            }

            @Override
            public Single<T> invoke(Supplier<? extends CompletionStage<T>> supplier) {
                Supplier<? extends CompletionStage<T>> next = supplier;

                for (FtHandlerTyped<T> validFt : validFts) {
                    final var finalNext = next;
                    next = () -> validFt.invoke(finalNext);
                }

                return Single.create(next.get());
            }
        }

        private class TypedWrapper implements FtHandlerTyped<T> {
            private final FtHandler handler;

            private TypedWrapper(FtHandler handler) {
                this.handler = handler;
            }

            @Override
            public Single<T> invoke(Supplier<? extends CompletionStage<T>> supplier) {
                return handler.invoke(supplier);
            }

            @Override
            public Multi<T> invokeMulti(Supplier<? extends Flow.Publisher<T>> supplier) {
                return handler.invokeMulti(supplier);
            }
        }
    }

    /**
     * A builder used for setting up a customized list of fault tolerance handlers.
     */
    public static class Builder extends BaseBuilder<Builder> implements io.helidon.common.Builder<FtHandler> {
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
         * @param <U> type of future
         * @return a new typed builder instance
         */
        public <U> TypedBuilder<U> addFallback(Fallback<U> fallback) {
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

            private FtHandlerImpl(List<FtHandler> validFts) {
                this.validFts = new LinkedList<>(validFts);
            }

            @Override
            public <T> Multi<T> invokeMulti(Supplier<? extends Flow.Publisher<T>> supplier) {
                Supplier<? extends Flow.Publisher<T>> next = supplier;

                for (FtHandler validFt : validFts) {
                    final var finalNext = next;
                    next = () -> validFt.invokeMulti(finalNext);
                }

                return Multi.create(next.get());
            }

            @Override
            public <T> Single<T> invoke(Supplier<? extends CompletionStage<T>> supplier) {
                Supplier<? extends CompletionStage<T>> next = supplier;

                for (FtHandler validFt : validFts) {
                    final var finalNext = next;
                    next = () -> validFt.invoke(finalNext);
                }

                return Single.create(next.get());
            }
        }
    }
}
