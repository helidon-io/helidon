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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.reactive.Single;

public class Async {
    private final LazyValue<? extends ExecutorService> executor;

    public Async(Builder builder) {
        this.executor = LazyValue.create(builder.executor);
    }

    public static Async create() {
        return builder().build();
    }

    public <T> Single<T> invoke(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();

        executor.get()
                .submit(() -> {
                    try {
                        T result = supplier.get();
                        future.complete(result);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });

        return Single.create(future);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder implements io.helidon.common.Builder<Async> {
        private LazyValue<? extends ExecutorService> executor = FaultTolerance.executor();

        private Builder() {
        }

        @Override
        public Async build() {
            return new Async(this);
        }

        public Builder executor(Supplier<ExecutorService> executor) {
            this.executor = LazyValue.create(executor);
            return this;
        }
    }
}
