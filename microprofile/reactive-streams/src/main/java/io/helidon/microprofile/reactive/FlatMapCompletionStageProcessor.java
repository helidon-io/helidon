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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Function;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.RequestedCounter;
import io.helidon.common.reactive.SequentialSubscriber;

/**
 * Flatten the elements emitted by publishers produced by the mapper function to this stream.
 *
 * @param <T> input item type
 * @param <X> output item type
 */
class FlatMapCompletionStageProcessor<T, X> implements Flow.Processor<T, X>, Multi<X> {

    private Function<T, CompletionStage<X>> mapper;
    private RequestedCounter requestCounter = new RequestedCounter();
    private SequentialSubscriber<? super X> subscriber;
    private Flow.Subscription subscription;
    private volatile CompletionStage<X> lastCompletionStage;
    private volatile boolean upstreamsCompleted;
    private Optional<Throwable> error = Optional.empty();

    @SuppressWarnings("unchecked")
    FlatMapCompletionStageProcessor(Function<?, CompletionStage<?>> mapper) {
        Function<Object, CompletionStage<?>> csMapper = (Function<Object, CompletionStage<?>>) mapper;
        this.mapper = t -> (CompletionStage<X>) csMapper.apply(t);
    }

    private class FlatMapSubscription implements Flow.Subscription {
        @Override
        public void request(long n) {
            if (requestCounter.get() > 0) {
                requestCounter.increment(n, FlatMapCompletionStageProcessor.this::onError);
                return;
            }
            requestCounter.increment(n, FlatMapCompletionStageProcessor.this::onError);
            subscription.request(1);
        }

        @Override
        public void cancel() {
            subscriber = null;
            subscription.cancel();
        }
    }

    @Override
    public void subscribe(Flow.Subscriber<? super X> subscriber) {
        this.subscriber = SequentialSubscriber.create(subscriber);
        if (Objects.nonNull(this.subscription)) {
            subscriber.onSubscribe(new FlatMapSubscription());
        }
        error.ifPresent(subscriber::onError);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        if (Objects.nonNull(this.subscription)) {
            subscription.cancel();
            return;
        }
        this.subscription = subscription;
        if (Objects.nonNull(subscriber)) {
            subscriber.onSubscribe(new FlatMapSubscription());
        }
    }

    @Override
    public void onNext(T o) {
        Objects.requireNonNull(o);
        try {
            lastCompletionStage = mapper.apply(o);
            lastCompletionStage.whenComplete(this::completionStageOnComplete);
        } catch (Throwable t) {
            subscription.cancel();
            subscriber.onError(t);
        }
    }

    private void completionStageOnComplete(X item, Throwable t) {
        if (Objects.nonNull(t)) {
            subscription.cancel();
            this.onError(t);
        } else if (Objects.isNull(item)) {
            subscription.cancel();
            this.onError(new NullPointerException());
            return;
        }

        if (requestCounter.tryDecrement()) {
            subscriber.onNext(item);
        } else {
            onError(new IllegalStateException("Not enough request to submit item"));
        }

        if (upstreamsCompleted) {
            subscriber.onComplete();
        }

        lastCompletionStage = null;
        try {
            requestCounter.lock();
            if (requestCounter.get() > 0) {
                subscription.request(1);
            }
        } finally {
            requestCounter.unlock();
        }
    }

    @Override
    public void onError(Throwable t) {
        this.error = Optional.of(t);
        if (Objects.nonNull(subscriber)) {
            subscriber.onError(t);
        }
    }

    @Override
    public void onComplete() {
        upstreamsCompleted = true;
        if (requestCounter.get() == 0 || Objects.isNull(lastCompletionStage)) {
            //Have to wait for all completion stages to be completed
            if (Objects.nonNull(subscriber)) {
                subscriber.onComplete();
            }
        }
    }
}
