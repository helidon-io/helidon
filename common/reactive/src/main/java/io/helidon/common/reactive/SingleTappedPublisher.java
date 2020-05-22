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

package io.helidon.common.reactive;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import io.helidon.common.reactive.MultiTappedPublisher.MultiTappedSubscriber;

/**
 * Intercept the calls to the various Flow interface methods and calls the appropriate
 * user callbacks.
 * @param <T> the element type of the sequence
 */
final class SingleTappedPublisher<T> extends CompletionSingle<T> {

    private final Single<T> source;

    private final Consumer<? super Flow.Subscription> onSubscribeCallback;

    private final Consumer<? super T> onNextCallback;

    private final Consumer<? super Throwable> onErrorCallback;

    private final Runnable onCompleteCallback;

    private final LongConsumer onRequestCallback;

    private final Runnable onCancelCallback;

    SingleTappedPublisher(Single<T> source,
                          Consumer<? super Flow.Subscription> onSubscribeCallback,
                          Consumer<? super T> onNextCallback,
                          Consumer<? super Throwable> onErrorCallback,
                          Runnable onCompleteCallback,
                          LongConsumer onRequestCallback,
                          Runnable onCancelCallback) {
        this.source = source;
        this.onSubscribeCallback = onSubscribeCallback;
        this.onNextCallback = onNextCallback;
        this.onErrorCallback = onErrorCallback;
        this.onCompleteCallback = onCompleteCallback;
        this.onRequestCallback = onRequestCallback;
        this.onCancelCallback = onCancelCallback;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        source.subscribe(new MultiTappedSubscriber<>(subscriber,
                onSubscribeCallback, onNextCallback,
                onErrorCallback, onCompleteCallback,
                onRequestCallback, onCancelCallback));
    }

    @Override
    public Single<T> onComplete(Runnable onTerminate) {
        return new SingleTappedPublisher<>(
                source,
                onSubscribeCallback,
                onNextCallback,
                onErrorCallback,
                RunnableChain.combine(onCompleteCallback, onTerminate),
                onRequestCallback,
                onCancelCallback
        );
    }

    @Override
    public Single<T> onError(Consumer<? super Throwable> onErrorConsumer) {
        return new SingleTappedPublisher<>(
                source,
                onSubscribeCallback,
                onNextCallback,
                ConsumerChain.combine(onErrorCallback, onErrorConsumer),
                onCompleteCallback,
                onRequestCallback,
                onCancelCallback
        );
    }

    @Override
    public Single<T> onTerminate(Runnable onTerminate) {
        return new SingleTappedPublisher<>(
                source,
                onSubscribeCallback,
                onNextCallback,
                ConsumerChain.combine(onErrorCallback, e -> onTerminate.run()),
                RunnableChain.combine(onCompleteCallback, onTerminate),
                onRequestCallback,
                RunnableChain.combine(onCancelCallback, onTerminate)
        );
    }

    @Override
    public Single<T> peek(Consumer<? super T> consumer) {
        return new SingleTappedPublisher<>(
                source,
                onSubscribeCallback,
                ConsumerChain.combine(onNextCallback, consumer),
                onErrorCallback,
                onCompleteCallback,
                onRequestCallback,
                onCancelCallback
        );
    }

    @Override
    public Single<T> onCancel(Runnable onCancel) {
        return new SingleTappedPublisher<>(
                source,
                onSubscribeCallback,
                onNextCallback,
                onErrorCallback,
                onCompleteCallback,
                onRequestCallback,
                RunnableChain.combine(onCancelCallback, onCancel)
        );
    }
}
