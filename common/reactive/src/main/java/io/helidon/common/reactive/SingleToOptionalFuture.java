/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.common.reactive;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link io.helidon.common.reactive.Single} exposed as a {@link java.util.concurrent.CompletableFuture}.
 */
class SingleToOptionalFuture<T> extends CompletableFuture<Optional<T>> implements Subscriber<T> {

    private final AtomicReference<Subscription> ref = new AtomicReference<>();
    private final AtomicReference<T> item = new AtomicReference<>();

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = super.cancel(mayInterruptIfRunning);
        if (cancelled) {
            Subscription s = ref.getAndSet(null);
            if (s != null) {
                s.cancel();
            }
        }
        return cancelled;
    }

    @Override
    public void onSubscribe(Subscription next) {
        Subscription current = ref.getAndSet(next);
        Objects.requireNonNull(next, "Subscription cannot be null");
        if (current != null) {
            next.cancel();
            current.cancel();
        } else {
            next.request(Long.MAX_VALUE);
        }
    }

    @Override
    public void onNext(T item) {
        // we expect exactly one item
        if (!this.item.compareAndSet(null, item)) {
            super.completeExceptionally(new IllegalStateException("Received more than one value for a single."));
            Subscription s = ref.getAndSet(null);
            s.cancel();
        }
    }

    @Override
    public void onError(Throwable ex) {
        if (ref.getAndSet(null) != null) {
            super.completeExceptionally(ex);
        }
    }

    @Override
    public void onComplete() {
        if (ref.getAndSet(null) != null) {
            super.complete(Optional.ofNullable(item.get()));
        }
    }

    @Override
    public boolean complete(Optional<T> value) {
        throw new UnsupportedOperationException("This future cannot be completed manually");
    }

    @Override
    public boolean completeExceptionally(Throwable ex) {
        throw new UnsupportedOperationException("This future cannot be completed manually");
    }
}
