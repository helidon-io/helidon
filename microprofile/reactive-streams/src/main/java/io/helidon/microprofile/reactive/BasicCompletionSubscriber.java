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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Terminate a CompletableFuture when the upstream terminates, ignoring the items.
 * @param <T> the element type of the upstream
 */
final class BasicCompletionSubscriber<T>
implements Flow.Subscriber<T>, Flow.Subscription {

    private final Flow.Subscriber<? super T> subscriber;

    private final CompletableFuture<Void> completable;

    private Flow.Subscription upstream;

    BasicCompletionSubscriber(Flow.Subscriber<? super T> subscriber) {
        this.subscriber = subscriber;
        this.completable = new CompletableFuture<>();
    }

    public CompletionStage<Void> completable() {
        return completable;
    }

    @Override
    public void request(long n) {
        Flow.Subscription s = upstream;
        if (s != null) {
            s.request(n);
        }
    }

    @Override
    public void cancel() {
        Flow.Subscription s = upstream;
        if (s != null) {
            upstream = SubscriptionHelper.CANCELED;
            s.cancel();
            completable.cancel(true);
        }
    }

    @Override
    public void onSubscribe(Flow.Subscription s) {
        SubscriptionHelper.validate(upstream, s);
        upstream = s;
        subscriber.onSubscribe(this);
    }

    @Override
    public void onNext(T t) {
        Objects.requireNonNull(t, "t is null");
        subscriber.onNext(t);
    }

    @Override
    public void onError(Throwable t) {
        Objects.requireNonNull(t, "t is null");
        subscriber.onError(t);
        completable.completeExceptionally(t);
    }

    @Override
    public void onComplete() {
        subscriber.onComplete();
        completable.complete(null);
    }
}
