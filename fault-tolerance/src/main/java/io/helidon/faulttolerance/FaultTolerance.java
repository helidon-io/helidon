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

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;

/**
 * Access to fault tolerance features.
 */
public final class FaultTolerance {
    private static final AtomicReference<LazyValue<? extends ScheduledExecutorService>> SCHEDULED_EXECUTOR =
            new AtomicReference<>();
    private static final AtomicReference<LazyValue<ExecutorService>> EXECUTOR = new AtomicReference<>();
    private static final AtomicReference<Config> CONFIG = new AtomicReference<>(Config.empty());

    static {
        SCHEDULED_EXECUTOR.set(LazyValue.create(ScheduledThreadPoolSupplier.builder()
                                                        .threadNamePrefix("ft-schedule-")
                                                        .config(CONFIG.get().get("scheduled-executor"))
                                                        .build()));

        EXECUTOR.set(LazyValue.create(ThreadPoolSupplier.builder()
                                              .threadNamePrefix("ft-")
                                              .config(CONFIG.get().get("executor"))
                                              .build()));
    }

    private FaultTolerance() {
    }

    public static void config(Config config) {
        CONFIG.set(config);

        SCHEDULED_EXECUTOR.set(LazyValue.create(ScheduledThreadPoolSupplier.create(CONFIG.get().get("scheduled-executor"))));
        EXECUTOR.set(LazyValue.create(ThreadPoolSupplier.create(CONFIG.get().get("executor"))));
    }

    public static void executor(Supplier<? extends ExecutorService> executor) {
        EXECUTOR.set(LazyValue.create(executor::get));
    }

    public static void scheduledExecutor(Supplier<? extends ScheduledExecutorService> executor) {
        SCHEDULED_EXECUTOR.set(LazyValue.create(executor));
    }

    static LazyValue<? extends ExecutorService> executor() {
        return EXECUTOR.get();
    }

    static LazyValue<? extends ScheduledExecutorService> scheduledExecutor() {
        return SCHEDULED_EXECUTOR.get();
    }

    public static <T> Single<T> async(Supplier<T> syncSupplier) {
        return Async.builder()
                .executor(EXECUTOR.get())
                .build()
                .invoke(syncSupplier);
    }

    public static <T> Single<T> fallback(Supplier<? extends CompletionStage<T>> primary,
                                         Function<Throwable, ? extends CompletionStage<T>> fallback) {
        return Fallback.<T>builder()
                .fallback(fallback)
                .build()
                .invoke(primary);
    }

    @SuppressWarnings("unchecked")
    public static <T> Single<T> retry(Supplier<? extends CompletionStage<T>> command) {
        return Retry.builder()
                .build()
                .invoke(command);
    }

    public static <T> Single<T> timeout(Duration timeout,
                                        Supplier<? extends CompletionStage<T>> command) {
        Timeout ft = Timeout.builder()
                .timeout(timeout)
                .build();

        return ft.invoke(command);
    }

    public static Builder builder() {
        return new Builder();
    }

    static Throwable getCause(Throwable throwable) {
        if (throwable instanceof CompletionException) {
            return getCause(throwable.getCause());
        }
        if (throwable instanceof ExecutionException) {
            return getCause(throwable.getCause());
        }
        return throwable;
    }

    abstract static class BaseBuilder<B extends BaseBuilder<B>> {
        @SuppressWarnings("unchecked")
        private B me() {
            return (B) this;
        }

        public B addBulkhead(Bulkhead bulkhead) {
            add(bulkhead);
            return me();
        }

        public B addBreaker(CircuitBreaker breaker) {
            add(breaker);
            return me();
        }

        public B addTimeout(Timeout timeout) {
            add(timeout);
            return me();
        }

        public B addRetry(Retry retry) {
            add(retry);
            return me();
        }

        public abstract void add(Handler ft);
    }

    public static class TypedBuilder<T> extends BaseBuilder<TypedBuilder<T>> implements io.helidon.common.Builder<TypedHandler<T>> {
        private final List<TypedHandler<T>> fts = new LinkedList<>();

        private TypedBuilder() {
        }

        @Override
        public TypedHandler<T> build() {
            return new TypedHandlerImpl<T>(fts);
        }

        @Override
        public void add(Handler ft) {
            fts.add(ft::invoke);
        }

        public TypedBuilder<T> addFallback(Fallback<T> fallback) {
            fts.add(fallback);
            return this;
        }

        private TypedBuilder<T> builder(Builder builder) {
            builder.fts
                    .forEach(it -> {
                        fts.add(it::invoke);
                    });
            return this;
        }

        private static class TypedHandlerImpl<T> implements TypedHandler<T> {
            private final List<TypedHandler<T>> validFts;

            private TypedHandlerImpl(List<TypedHandler<T>> validFts) {
                this.validFts = new LinkedList<>(validFts);
            }

            @Override
            public Single<T> invoke(Supplier<? extends CompletionStage<T>> supplier) {
                Supplier<? extends CompletionStage<T>> next = supplier;

                for (TypedHandler<T> validFt : validFts) {
                    final var finalNext = next;
                    next = () -> validFt.invoke(finalNext);
                }

                return Single.create(next.get());
            }
        }
    }

    public static class Builder extends BaseBuilder<Builder> implements io.helidon.common.Builder<Handler> {
        private final List<Handler> fts = new LinkedList<>();

        private Builder() {
        }

        @Override
        public Handler build() {
            return new HandlerImpl(fts);
        }

        public <U> TypedBuilder<U> addFallback(Fallback<U> fallback) {
            return new TypedBuilder<U>()
                    .builder(this)
                    .addFallback(fallback);
        }

        @Override
        public void add(Handler ft) {
            fts.add(ft);
        }

        private static class HandlerImpl implements Handler {
            private final List<Handler> validFts;

            private HandlerImpl(List<Handler> validFts) {
                this.validFts = new LinkedList<>(validFts);
            }

            @Override
            public <T> Single<T> invoke(Supplier<? extends CompletionStage<T>> supplier) {
                Supplier<? extends CompletionStage<T>> next = supplier;

                for (Handler validFt : validFts) {
                    final var finalNext = next;
                    next = () -> validFt.invoke(finalNext);
                }

                return Single.create(next.get());
            }
        }
    }
}
