/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A subscriber that expects zero to one items to be published.
 * If more than one item is published, it cancels the publisher and completes exceptionally.
 *
 * @param <T> type of the items
 */
public final class GetSubscriber<T> implements Flow.Subscriber<T> {
    private volatile Flow.Subscription subscription;
    private final AtomicBoolean done = new AtomicBoolean(false);
    // defense against bad publisher - if I receive complete after cancelled...
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<T> theResult = new AtomicReference<>();
    private final CompletableFuture<Optional<T>> resultFuture;
    private final String errorDescription;

    private GetSubscriber(CompletableFuture<Optional<T>> resultFuture, String errorDescription) {
        this.resultFuture = resultFuture;
        this.errorDescription = errorDescription;
    }

    /**
     * Create a new subscriber.
     *
     * @param resultFuture future that completes if we get zero to one items and {@link #onComplete()} is called,
     *                     or completes exceptionally if {@link #onError(Throwable)} is called or when we receive
     *                     more than one item
     *
     * @param errorDescription error message to send when we get more than one items
     * @param <T> type of items this subscriber expected
     * @return a new subscriber
     */
    public static <T> GetSubscriber<T> create(CompletableFuture<Optional<T>> resultFuture, String errorDescription) {
        return new GetSubscriber<T>(resultFuture, errorDescription);
    }

    /**
     * Create a new subscriber with default error message.
     *
     * @param resultFuture future that completes if we get zero to one items and {@link #onComplete()} is called,
     *                     or completes exceptionally if {@link #onError(Throwable)} is called or when we receive
     *                     more than one item
     *
     * @param <T> type of items this subscriber expected
     * @return a new subscriber
     */
    public static <T> GetSubscriber<T> create(CompletableFuture<Optional<T>> resultFuture) {
        return new GetSubscriber<T>(resultFuture, "More than one item received where zero to one were"
                + "expected");
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(2);
    }

    @Override
    public void onNext(T item) {
        if (done.get()) {
            subscription.cancel();
            resultFuture.completeExceptionally(new RuntimeException(errorDescription));
            cancelled.set(true);
        } else {
            theResult.set(item);
            done.set(true);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (cancelled.get()) {
            return;
        }
        resultFuture.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        if (cancelled.get()) {
            return;
        }
        resultFuture.complete(Optional.ofNullable(theResult.get()));
    }
}
