/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.common.reactive;

import java.util.concurrent.CompletableFuture;

import io.helidon.common.LazyValue;

/**
 * Single as CompletionStage.
 *
 * @param <T> payload type
 */
public abstract class CompletionSingle<T> extends CompletionAwaitable<T> implements Single<T> {

    private final CompletableFuture<Void> cancelFuture = new CompletableFuture<>();

    protected CompletionSingle() {
        LazyValue<CompletableFuture<T>> lazyStage = LazyValue.create(this::toNullableStage);
        setOriginalStage(lazyStage::get);
    }

    protected CompletableFuture<T> toNullableStage() {
        SingleToFuture<T> subscriber = new SingleToFuture<>(this, true);
        this.subscribe(subscriber);
        return subscriber;
    }

    @Override
    public Single<T> onCancel(final Runnable onCancel) {
        cancelFuture.thenRun(onCancel);
        return Single.super.onCancel(onCancel);
    }

    @Override
    public Single<T> cancel() {
        Single<T> single = Single.super.cancel();
        this.cancelFuture.complete(null);
        return single;
    }

}
