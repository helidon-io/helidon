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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;

/**
 * Runs synchronous suppliers asynchronously using virtual threads. Includes
 * convenient static method to avoid creating instances of this class.
 */
public interface Async {

    /**
     * Invoke a synchronous operation asynchronously.
     * This method never throws an exception. Any exception is returned via the
     * {@link java.util.concurrent.CompletableFuture} result.
     *
     * @param supplier supplier of value (or a method reference)
     * @param <T> type of returned value
     * @return a Single that is a "promise" of the future result
     */
    <T> CompletableFuture<T> invoke(Supplier<T> supplier);

    /**
     * Async with default executor service.
     *
     * @return a default async instance
     */
    static Async create() {
        return AsyncImpl.DefaultAsyncInstance.instance();
    }

    /**
     * Convenience method to avoid having to call {@link #create()}.
     *
     * @param supplier supplier of value (or a method reference)
     * @param <T> type of returned value
     * @return a Single that is a "promise" of the future result
     */
    static <T> CompletableFuture<T> invokeStatic(Supplier<T> supplier) {
        return create().invoke(supplier);
    }

    /**
     * A new builder to build a customized {@link Async} instance.
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent API Builder for {@link Async}.
     */
    class Builder implements io.helidon.common.Builder<Builder, Async> {
        private LazyValue<? extends ExecutorService> executor = FaultTolerance.executor();

        private Builder() {
        }

        @Override
        public Async build() {
            return new AsyncImpl(this);
        }

        /**
         * Configure executor service to use for executing tasks asynchronously.
         *
         * @param executor executor service supplier
         * @return updated builder instance
         */
        public Builder executor(Supplier<? extends ExecutorService> executor) {
            this.executor = LazyValue.create(Objects.requireNonNull(executor));
            return this;
        }

        /**
         * Configure executor service to use for executing tasks asynchronously.
         *
         * @param executor executor service
         * @return updated builder instance
         */
        public Builder executor(ExecutorService executor) {
            this.executor = LazyValue.create(Objects.requireNonNull(executor));
            return this;
        }

        LazyValue<? extends ExecutorService> executor() {
            return executor;
        }
    }
}
