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

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.reactive.Single;

/**
 * Support for asynchronous execution of synchronous (blocking) calls.
 * While this does not miraculously make a synchronous call non-blocking, it at least moves
 * the blocking calls to a separate executor service, degrading the service gracefully.
 * <p>
 * Example of usage:
 * <pre>
 *     // async instance with default executor service
 *     Async async = Async.create();
 *
 *     // call a method with no parameters
 *     Single&lt;String> result = async.invoke(this::slowSync);
 *
 *     // call a method with parameters
 *     async.invoke(() -> processRequest(request))
 *      .thenApply(response::send);
 *
 *     // use async to obtain a Multi (from a method returning List of strings)
 *     Multi&lt;String> stringMulti = async.invoke(this::syncList)
 *                 .flatMap(Multi::create);
 * </pre>
 */
public interface Async {
    /**
     * Invoke a synchronous operation asynchronously.
     * This method never throws an exception. Any exception is returned via the {@link io.helidon.common.reactive.Single}
     * result.
     *
     * @param supplier supplier of value (may be a method reference)
     * @param <T> type of returned value
     * @return a Single that is a "promise" of the future result
     */
    <T> Single<T> invoke(Supplier<T> supplier);

    /**
     * Async with default executor service.
     *
     * @return a default async instance
     */
    static Async create() {
        return AsyncImpl.DefaultAsyncInstance.instance();
    }

    /**
     * A new builder to build a customized {@link io.helidon.faulttolerance.Async} instance.
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent API Builder for {@link io.helidon.faulttolerance.Async}.
     */
    class Builder implements io.helidon.common.Builder<Async> {
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
