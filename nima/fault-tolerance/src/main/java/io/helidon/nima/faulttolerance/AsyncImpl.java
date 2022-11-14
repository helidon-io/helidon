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

package io.helidon.nima.faulttolerance;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;

import static io.helidon.nima.faulttolerance.SupplierHelper.unwrapThrowable;

/**
 * Implementation of {@code Async}. Default executor accessed from {@link FaultTolerance#executor()}.
 */
class AsyncImpl implements Async {
    private final LazyValue<? extends ExecutorService> executor;

    AsyncImpl() {
        this(Async.builder());
    }

    AsyncImpl(Builder builder) {
        this.executor = builder.executor();
    }

    @Override
    public <T> CompletableFuture<T> invoke(Supplier<T> supplier) {
        AtomicBoolean mayInterrupt = new AtomicBoolean(false);
        CompletableFuture<T> result = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                mayInterrupt.set(mayInterruptIfRunning);
                return super.cancel(mayInterruptIfRunning);
            }
        };
        Future<?> future = executor.get().submit(() -> {
            Thread thread = Thread.currentThread();
            thread.setName(thread.getName() + ": async");
            try {
                T t = supplier.get();
                result.complete(t);
            } catch (Throwable t) {
                Throwable throwable = unwrapThrowable(t);
                result.completeExceptionally(throwable);
            }
        });
        result.exceptionally(t -> {
            if (t instanceof CancellationException) {
                future.cancel(mayInterrupt.get());
            }
            return null;
        });
        return result;
    }

    /**
     * Default {@code Async} instance.
     */
    static final class DefaultAsyncInstance {
        private static final Async INSTANCE = new AsyncImpl();

        static Async instance() {
            return INSTANCE;
        }
    }
}
