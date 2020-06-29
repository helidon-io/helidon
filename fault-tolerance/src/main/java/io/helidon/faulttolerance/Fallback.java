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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.reactive.Single;

public class Fallback<T> implements TypedHandler<T> {
    private final Function<Throwable, ? extends CompletionStage<T>> fallback;

    private Fallback(Builder<T> builder) {
        this.fallback = builder.fallback;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    @Override
    public Single<T> invoke(Supplier<? extends CompletionStage<T>> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();

        supplier.get()
                .thenAccept(future::complete)
                .exceptionally(throwable -> {
                    Throwable cause = FaultTolerance.getCause(throwable);
                    fallback.apply(cause)
                            .thenAccept(future::complete)
                            .exceptionally(t2 -> {
                                Throwable cause2 = FaultTolerance.getCause(t2);
                                cause2.addSuppressed(throwable);
                                future.completeExceptionally(cause2);
                                return null;
                            });
                    return null;
                });

        return Single.create(future);
    }

    public static class Builder<T> implements io.helidon.common.Builder<Fallback<T>> {
        private Function<Throwable, ? extends CompletionStage<T>> fallback;

        private Builder() {
        }

        @Override
        public Fallback<T> build() {
            Objects.requireNonNull(fallback, "Fallback method must be specified");
            return new Fallback<>(this);
        }

        public Builder<T> fallback(Function<Throwable, ? extends CompletionStage<T>> fallback) {
            this.fallback = fallback;
            return this;
        }
    }
}
