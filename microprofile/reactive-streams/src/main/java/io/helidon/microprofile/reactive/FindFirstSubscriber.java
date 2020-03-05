/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import io.helidon.microprofile.reactive.hybrid.HybridSubscriber;

import org.eclipse.microprofile.reactive.streams.operators.spi.SubscriberWithCompletionStage;
import org.reactivestreams.Subscriber;

class FindFirstSubscriber<T> implements Flow.Subscriber<T>, SubscriberWithCompletionStage<T, Optional<T>> {
    private Flow.Subscription subscription;
    private CompletableFuture<Optional<T>> completionStage = new CompletableFuture<>();

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        Objects.requireNonNull(subscription);
        // https://github.com/reactive-streams/reactive-streams-jvm#2.5
        if (Objects.nonNull(this.subscription)) {
            subscription.cancel();
        } else {
            this.subscription = subscription;
            this.subscription.request(1);
        }
    }


    @Override
    public void onNext(T item) {
        subscription.cancel();
        completionStage.complete(Optional.of(item));
    }

    @Override
    public void onError(Throwable throwable) {
        completionStage.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        if (!completionStage.isDone()) {
            completionStage.complete(Optional.empty());
        }
    }

    @Override
    public CompletionStage<Optional<T>> getCompletion() {
        return completionStage;
    }

    @Override
    public Subscriber<T> getSubscriber() {
        return HybridSubscriber.from(this);
    }
}
