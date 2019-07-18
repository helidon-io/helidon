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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A subscriber to collect all published objects into a list.
 *
 * @param <T> type of the collected objects
 */
public final class CollectingSubscriber<T> implements Flow.Subscriber<T> {
    private final List<T> items = new LinkedList<>();
    private final CompletableFuture<List<T>> resultFuture;

    private CollectingSubscriber(CompletableFuture<List<T>> resultFuture) {
        this.resultFuture = resultFuture;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(T item) {
        items.add(item);
    }

    @Override
    public void onError(Throwable throwable) {
        resultFuture.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        resultFuture.complete(items);
    }

    /**
     * Create a subscriber that either completes, or exceptionally completes the {@code resultFuture} depending
     * on the received data.
     *
     * @param resultFuture future to complete once all the objects are obtained from the publisher
     * @param <T> type of the published items
     * @return a new subscriber
     */
    public static <T> CollectingSubscriber<T> create(CompletableFuture<List<T>> resultFuture) {
        return new CollectingSubscriber<>(resultFuture);
    }
}
