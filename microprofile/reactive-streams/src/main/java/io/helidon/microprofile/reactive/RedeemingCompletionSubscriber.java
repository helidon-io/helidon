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
import java.util.concurrent.CompletionStage;

import org.eclipse.microprofile.reactive.streams.operators.spi.SubscriberWithCompletionStage;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Replacement for non redeeming {@link org.eclipse.microprofile.reactive.streams.operators.CompletionSubscriber}'s DefaultCompletionSubscriber.
 * <p>
 *
 * @param <T> {@link org.reactivestreams.Subscriber} item type
 * @param <R> {@link java.util.concurrent.CompletionStage} payload type
 * @see <a href="https://github.com/eclipse/microprofile-reactive-streams-operators/issues/129#issue-521492223">microprofile-reactive-streams-operators #129</a>
 */
class RedeemingCompletionSubscriber<T, R> implements org.eclipse.microprofile.reactive.streams.operators.CompletionSubscriber<T, R>, SubscriberWithCompletionStage<T, R> {

    private final Subscriber<T> subscriber;
    private final CompletionStage<R> completion;

    /**
     * Create a {@link RedeemingCompletionSubscriber} by combining the given subscriber and completion stage.
     * The objects passed to this method should not be associated with more than one stream instance.
     *
     * @param subscriber subscriber to associate with completion stage
     * @param completion completion stage to associate with subscriber
     * @return {@link RedeemingCompletionSubscriber}
     */
    static <T, R> RedeemingCompletionSubscriber<T, R> of(Subscriber<T> subscriber, CompletionStage<R> completion) {
        return new RedeemingCompletionSubscriber<>(subscriber, completion);
    }

    private RedeemingCompletionSubscriber(Subscriber<T> subscriber, CompletionStage<R> completion) {
        this.subscriber = subscriber;
        this.completion = completion;
    }

    @Override
    public CompletionStage<R> getCompletion() {
        return completion;
    }

    @Override
    public Subscriber<T> getSubscriber() {
        return this;
    }

    @Override
    public void onSubscribe(Subscription s) {
        Objects.requireNonNull(s);
        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                s.request(n);
            }

            @Override
            public void cancel() {
                s.cancel();
                //Base processor breaks cancel->onComplete loop, so listen even for downstream call
                //completion.toCompletableFuture().complete(null);
            }
        });
    }

    @Override
    public void onNext(T t) {
        subscriber.onNext(t);
    }

    @Override
    public void onError(Throwable t) {
        subscriber.onError(t);
        completion.toCompletableFuture().completeExceptionally(t);
    }

    @Override
    public void onComplete() {
        subscriber.onComplete();
        //Base processor breaks cancel->onComplete loop, so listen even for upstream call
        //completion.toCompletableFuture().complete(null);
    }
}
