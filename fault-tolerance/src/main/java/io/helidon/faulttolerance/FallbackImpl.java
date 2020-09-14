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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

class FallbackImpl<T> implements Fallback<T> {
    private final Function<Throwable, ? extends CompletionStage<T>> fallback;
    private final Function<Throwable, ? extends Flow.Publisher<T>> fallbackMulti;
    private final ErrorChecker errorChecker;

    FallbackImpl(Fallback.Builder<T> builder) {
        this.fallback = builder.fallback();
        this.fallbackMulti = builder.fallbackMulti();
        this.errorChecker = ErrorChecker.create(builder.skipOn(), builder.applyOn());
    }

    @Override
    public Multi<T> invokeMulti(Supplier<? extends Flow.Publisher<T>> supplier) {
        DelayedTask<Multi<T>> delayedTask = DelayedTask.createMulti(supplier);

        delayedTask.execute();

        return delayedTask.result()
                .onErrorResumeWith(throwable -> {
                    Throwable cause = FaultTolerance.cause(throwable);
                    if (delayedTask.hadData() || errorChecker.shouldSkip(cause)) {
                        return Multi.error(cause);
                    } else {
                        return Multi.create(fallbackMulti.apply(cause));
                    }
                });
    }

    @Override
    public Single<T> invoke(Supplier<? extends CompletionStage<T>> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();

        supplier.get()
                .thenAccept(future::complete)
                .exceptionally(throwable -> {
                    Throwable cause = FaultTolerance.cause(throwable);
                    if (errorChecker.shouldSkip(cause)) {
                        future.completeExceptionally(cause);
                    } else {
                        fallback.apply(cause)
                                .thenAccept(future::complete)
                                .exceptionally(t2 -> {
                                    Throwable cause2 = FaultTolerance.cause(t2);
                                    cause2.addSuppressed(throwable);
                                    future.completeExceptionally(cause2);
                                    return null;
                                });
                    }
                    return null;
                });

        return Single.create(future, true);
    }
}
