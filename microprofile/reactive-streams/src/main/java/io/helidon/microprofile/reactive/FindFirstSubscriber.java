/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.common.reactive.Flow;
import io.helidon.microprofile.reactive.hybrid.HybridSubscriber;

import org.eclipse.microprofile.reactive.streams.operators.spi.SubscriberWithCompletionStage;
import org.reactivestreams.Subscriber;

public class FindFirstSubscriber<Object> implements Flow.Subscriber<Object>, SubscriberWithCompletionStage<Object, Object> {
    private Flow.Subscription subscription;
    private CompletableFuture<Object> completionStage = new CompletableFuture<>();

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
    @SuppressWarnings("unchecked")
    public void onNext(Object item) {
        subscription.cancel();
        Object optItem = (Object) Optional.of(item);
        completionStage.complete(optItem);
    }

    @Override
    public void onError(Throwable throwable) {
        completionStage.completeExceptionally(throwable);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onComplete() {
        if (!completionStage.isDone()) {
            Object optItem = (Object) Optional.empty();
            completionStage.complete(optItem);
        }
    }

    @Override
    public CompletionStage<Object> getCompletion() {
        return completionStage;
    }

    @Override
    public Subscriber<Object> getSubscriber() {
        return HybridSubscriber.from(this);
    }
}
