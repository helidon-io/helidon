/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
 *
 * @param <T> the element type of the sequence
 */
final class SingleTappedPublisher<T> extends CompletionSingle<T> implements NamedOperator {

    private final Single<T> source;

    private final Consumer<? super Flow.Subscription> onSubscribeCallback;

    private final Consumer<? super T> onNextCallback;

    private final Consumer<? super Throwable> onErrorCallback;

    private final Runnable onCompleteCallback;

    private final LongConsumer onRequestCallback;

    private final Runnable onCancelCallback;
    private String operatorName;

    SingleTappedPublisher(SingleTappedPublisher.Builder<T> builder) {
        this.source = builder.source;
        this.onSubscribeCallback = builder.onSubscribeCallback;
        this.onNextCallback = builder.onNextCallback;
        this.onErrorCallback = builder.onErrorCallback;
        this.onCompleteCallback = builder.onCompleteCallback;
        this.onRequestCallback = builder.onRequestCallback;
        this.onCancelCallback = builder.onCancelCallback;
        this.operatorName = builder.operatorName;
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
        return SingleTappedPublisher.builder(source)
                .onSubscribeCallback(onSubscribeCallback)
                .onNextCallback(onNextCallback)
                .onErrorCallback(onErrorCallback)
                .onCompleteCallback(RunnableChain.combine(onCompleteCallback, onTerminate))
                .onRequestCallback(onRequestCallback)
                .onCancelCallback(onCancelCallback)
                .build();
    }

    @Override
    public Single<T> onError(Consumer<? super Throwable> onErrorConsumer) {
        return SingleTappedPublisher.builder(source)
                .onSubscribeCallback(onSubscribeCallback)
                .onNextCallback(onNextCallback)
                .onErrorCallback(ConsumerChain.combine(onErrorCallback, onErrorConsumer))
                .onCompleteCallback(onCompleteCallback)
                .onRequestCallback(onRequestCallback)
                .onCancelCallback(onCancelCallback)
                .build();
    }

    @Override
    public Single<T> onTerminate(Runnable onTerminate) {
        return SingleTappedPublisher.builder(source)
                .onSubscribeCallback(onSubscribeCallback)
                .onNextCallback(onNextCallback)
                .onErrorCallback(ConsumerChain.combine(onErrorCallback, e -> onTerminate.run()))
                .onCompleteCallback(RunnableChain.combine(onCompleteCallback, onTerminate))
                .onRequestCallback(onRequestCallback)
                .onCancelCallback(RunnableChain.combine(onCancelCallback, onTerminate))
                .build();
    }

    @Override
    public Single<T> peek(Consumer<? super T> consumer) {
        return SingleTappedPublisher.builder(source)
                .onSubscribeCallback(onSubscribeCallback)
                .onNextCallback(ConsumerChain.combine(onNextCallback, consumer))
                .onErrorCallback(onErrorCallback)
                .onCompleteCallback(onCompleteCallback)
                .onRequestCallback(onRequestCallback)
                .onCancelCallback(onCancelCallback)
                .build();
    }

    @Override
    public Single<T> onCancel(Runnable onCancel) {
        return SingleTappedPublisher.builder(source)
                .onSubscribeCallback(onSubscribeCallback)
                .onNextCallback(onNextCallback)
                .onErrorCallback(onErrorCallback)
                .onCompleteCallback(onCompleteCallback)
                .onRequestCallback(onRequestCallback)
                .onCancelCallback(RunnableChain.combine(onCancelCallback, onCancel))
                .build();
    }

    @Override
    public String operatorName() {
        return operatorName;
    }

    /**
     * A builder to customize a multi tapped publisher instance.
     *
     * @param source source to wrap
     * @param <T>    type of the multi
     * @return a new builder
     */
    public static <T> SingleTappedPublisher.Builder<T> builder(Single<T> source) {
        return new SingleTappedPublisher.Builder<>(source);
    }

    /**
     * Multi tapped publisher builder to register custom callbacks.
     *
     * @param <T> type of returned multi
     */
    public static class Builder<T> implements io.helidon.common.Builder<SingleTappedPublisher<T>> {
        private final Single<T> source;
        private Consumer<? super Flow.Subscription> onSubscribeCallback;
        private Consumer<? super T> onNextCallback;
        private Runnable onCompleteCallback;
        private LongConsumer onRequestCallback;
        private Runnable onCancelCallback;
        private Consumer<? super Throwable> onErrorCallback;
        private String operatorName = MultiTappedPublisher.class.getName();

        private Builder(Single<T> source) {
            this.source = source;
        }

        @Override
        public SingleTappedPublisher<T> build() {
            return new SingleTappedPublisher<>(this);
        }

        SingleTappedPublisher.Builder<T> operatorName(String operatorName) {
            this.operatorName = operatorName;
            return this;
        }

        SingleTappedPublisher.Builder<T> onSubscribeCallback(Consumer<? super Flow.Subscription> onSubscribeCallback) {
            this.onSubscribeCallback = onSubscribeCallback;
            return this;
        }

        /**
         * Subscription callback.
         *
         * @param onSubscribeCallback runnable to run when
         *                            {@link Flow.Subscriber#onSubscribe(java.util.concurrent.Flow.Subscription)} is called
         * @return updated builder instance
         */
        public SingleTappedPublisher.Builder<T> onSubscribeCallback(Runnable onSubscribeCallback) {
            this.onSubscribeCallback = subscription -> onSubscribeCallback.run();
            return this;
        }

        /**
         * On next callback.
         *
         * @param onNextCallback runnable to run when
         *                       {@link Flow.Subscriber#onNext(Object)} is called
         * @return updated builder instance
         */
        public SingleTappedPublisher.Builder<T> onNextCallback(Consumer<? super T> onNextCallback) {
            this.onNextCallback = onNextCallback;
            return this;
        }

        /**
         * On complete callback.
         *
         * @param onCompleteCallback runnable to run when
         *                           {@link java.util.concurrent.Flow.Subscriber#onComplete()} is called
         * @return updated builder instance
         */
        public SingleTappedPublisher.Builder<T> onCompleteCallback(Runnable onCompleteCallback) {
            this.onCompleteCallback = onCompleteCallback;
            return this;
        }

        /**
         * On request callback.
         *
         * @param onRequestCallback runnable to run when
         *                          {@link Flow.Subscription#request(long)} is called
         * @return updated builder instance
         */
        public SingleTappedPublisher.Builder<T> onRequestCallback(LongConsumer onRequestCallback) {
            this.onRequestCallback = onRequestCallback;
            return this;
        }

        /**
         * On cancel callback.
         *
         * @param onCancelCallback runnable to run when
         *                         {@link java.util.concurrent.Flow.Subscription#cancel()} is called
         * @return updated builder instance
         */
        public SingleTappedPublisher.Builder<T> onCancelCallback(Runnable onCancelCallback) {
            this.onCancelCallback = onCancelCallback;
            return this;
        }

        /**
         * On error callback.
         *
         * @param onErrorCallback runnable to run when
         *                        {@link Flow.Subscriber#onError(Throwable)} is called
         * @return updated builder instance
         */
        public SingleTappedPublisher.Builder<T> onErrorCallback(Consumer<? super Throwable> onErrorCallback) {
            this.onErrorCallback = onErrorCallback;
            return this;
        }
    }
}
