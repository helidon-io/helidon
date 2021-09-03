/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.reactive;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * Collects the upstream items into a collection via the help of a
 * {@link java.util.stream.Stream} {@link Collector} instance and emits that result via
 * a CompletableFuture.
 * @param <T> the upstream element type
 * @param <A> the accumulator type
 * @param <R> the result type
 */
final class BasicCollectSubscriber<T, A, R>
extends AtomicReference<Flow.Subscription>
implements Flow.Subscriber<T> {

    private static final long serialVersionUID = -1718297417587197143L;

    private final CompletableFuture<R> completable = new CompletableFuture<>();

    private final Supplier<A> supplier;

    private final BiConsumer<A, T> accumulator;

    private final Function<A, R> finisher;

    private A collection;

    BasicCollectSubscriber(Collector<T, A, R> collector) {
        supplier = collector.supplier();
        accumulator = collector.accumulator();
        finisher = collector.finisher();
    }

    CompletableFuture<R> completable() {
        return completable;
    }

    @Override
    public void onSubscribe(Flow.Subscription s) {
        if (SubscriptionHelper.setOnce(this, s)) {
            try {
                collection = supplier.get();
            } catch (Throwable ex) {
                SubscriptionHelper.cancel(this);
                completable.completeExceptionally(ex);
                return;
            }
            s.request(Long.MAX_VALUE);
        }
    }

    @Override
    public void onNext(T t) {
        Objects.requireNonNull(t, "t is null");
        try {
            accumulator.accept(collection, t);
        } catch (Throwable ex) {
            SubscriptionHelper.cancel(this);
            completable.completeExceptionally(ex);
            return;
        }
    }

    @Override
    public void onError(Throwable t) {
        Objects.requireNonNull(t, "t is null");
        if (get() != SubscriptionHelper.CANCELED) {
            lazySet(SubscriptionHelper.CANCELED);
            collection = null;
            completable.completeExceptionally(t);
        }
    }

    @Override
    public void onComplete() {
        if (get() != SubscriptionHelper.CANCELED) {
            lazySet(SubscriptionHelper.CANCELED);
            A c = collection;
            collection = null;
            R r;
            try {
                r = finisher.apply(c); // null is allowed here for the completable's sake
            } catch (Throwable ex) {
                completable.completeExceptionally(ex);
                return;
            }
            completable.complete(r);
        }
    }

    // Workaround for SpotBugs, Flow classes should never get serialized
    private void writeObject(ObjectOutputStream stream)
            throws IOException {
        stream.defaultWriteObject();
    }

    // Workaround for SpotBugs, Flow classes should never get serialized
    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
    }
}
