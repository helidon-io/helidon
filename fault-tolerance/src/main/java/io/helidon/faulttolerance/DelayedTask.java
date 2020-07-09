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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.MultiTappedPublisher;
import io.helidon.common.reactive.Single;

interface DelayedTask<T> {
    // the result completes when the call fully completes (regardless of errors)
    CompletionStage<Void> execute();

    // get the (new) result
    T result();

    // create an error result
    T error(Throwable throwable);

    // cannot retry or fallback when data was already sent (only useful for multi)
    default boolean hadData() {
        return false;
    }

    static <T> DelayedTask<Multi<T>> createMulti(Supplier<? extends Flow.Publisher<T>> supplier) {
        return new DelayedTask<>() {
            private final AtomicBoolean completed = new AtomicBoolean();
            private final AtomicBoolean hasData = new AtomicBoolean();
            private final LazyValue<CompletableFuture<Void>> completionMarker = LazyValue.create(CompletableFuture::new);
            private final LazyValue<CompletableFuture<Flow.Publisher<T>>> publisherFuture = LazyValue
                    .create(CompletableFuture::new);
            private final LazyValue<Multi<T>> multi = LazyValue.create(() -> {
                return MultiTappedPublisher
                        .builder(Multi.create(publisherFuture.get()).flatMap(Function.identity(), 32, true, 32))
                        .onCancelCallback(() -> failMarker(new CancellationException("Multi was cancelled")))
                        .onCompleteCallback(this::completeMarker)
                        .onErrorCallback(this::failMarker)
                        .onNextCallback(it -> hasData.set(true))
                        .build();
            });

            @Override
            public CompletionStage<Void> execute() {
                publisherFuture.get().complete(supplier.get());

                return completionMarker.get();
            }

            @Override
            public Multi<T> result() {
                return multi.get();
            }

            @Override
            public Multi<T> error(Throwable throwable) {
                return Multi.error(throwable);
            }

            @Override
            public String toString() {
                return "multi:" + System.identityHashCode(this);
            }

            @Override
            public boolean hadData() {
                return hasData.get();
            }

            private void failMarker(Throwable throwable) {
                if (completed.compareAndSet(false, true)) {
                    completionMarker.get().completeExceptionally(throwable);
                }
            }

            private void completeMarker() {
                if (completed.compareAndSet(false, true)) {
                    completionMarker.get().complete(null);
                }
            }
        };
    }

    static <T> DelayedTask<Single<T>> createSingle(Supplier<? extends CompletionStage<T>> supplier) {
        return new DelayedTask<>() {
            // future we returned as a result of invoke command
            private final LazyValue<CompletableFuture<T>> resultFuture = LazyValue.create(CompletableFuture::new);

            @Override
            public CompletionStage<Void> execute() {
                CompletionStage<T> result;
                try {
                    result = supplier.get();
                } catch (Exception e) {
                    result = CompletableFuture.failedStage(e);
                }
                CompletableFuture<T> future = resultFuture.get();

                result.handle((it, throwable) -> {
                    if (throwable == null) {
                        future.complete(it);
                    } else {
                        future.completeExceptionally(throwable);
                    }

                    return null;
                });

                return result.thenRun(() -> {
                });
            }

            @Override
            public Single<T> result() {
                return Single.create(resultFuture.get(), true);
            }

            @Override
            public Single<T> error(Throwable throwable) {
                return Single.error(throwable);
            }

            @Override
            public String toString() {
                return "single:" + System.identityHashCode(this);
            }
        };
    }
}
