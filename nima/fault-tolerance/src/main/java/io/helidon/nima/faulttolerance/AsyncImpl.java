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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;

/**
 * Implementation of {@code Async}. If no executor specified in builder, then it will
 * use {@link Executors#newVirtualThreadPerTaskExecutor}. Note that this default executor
 * is not configurable using Helidon's config.
 */
class AsyncImpl implements Async {
    private final LazyValue<? extends ExecutorService> executor;

    AsyncImpl() {
        this.executor = LazyValue.create(Executors.newVirtualThreadPerTaskExecutor());
    }

    AsyncImpl(Builder builder) {
        this.executor = builder.executor() != null ? builder.executor()
                : LazyValue.create(Executors.newVirtualThreadPerTaskExecutor());
    }

    @Override
    public <T> CompletableFuture<T> invoke(Supplier<T> supplier) {
        CompletableFuture<T> result = new CompletableFuture<>();
        executor.get().submit(() -> {
            try {
                T t = supplier.get();
                result.complete(t);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    /**
     * Default {@code Async} instance that uses {@link Executors#newVirtualThreadPerTaskExecutor}.
     */
    static final class DefaultAsyncInstance {
        private static final Async INSTANCE = new AsyncImpl();

        static Async instance() {
            return INSTANCE;
        }
    }
}
