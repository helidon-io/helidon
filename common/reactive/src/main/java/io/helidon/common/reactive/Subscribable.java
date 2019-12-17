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

import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Consumer;

/**
 * Decorated publisher that allows subscribing to individual events with java functions.
 * @param <T> item type
 */
public interface Subscribable<T> extends Publisher<T> {

    /**
     * Subscribe to this {@link Single} instance with the given delegate functions.
     *
     * @param consumer onNext delegate function
     */
    default void subscribe(Consumer<? super T> consumer) {
        this.subscribe(new FunctionalSubscriber<>(consumer, null, null, null));
    }

    /**
     * Subscribe to this {@link Single} instance with the given delegate functions.
     *
     * @param consumer onNext delegate function
     * @param errorConsumer onError delegate function
     */
    default void subscribe(Consumer<? super T> consumer, Consumer<? super Throwable> errorConsumer) {
        this.subscribe(new FunctionalSubscriber<>(consumer, errorConsumer, null, null));
    }

    /**
     * Subscribe to this {@link Single} instance with the given delegate functions.
     *
     * @param consumer onNext delegate function
     * @param errorConsumer onError delegate function
     * @param completeConsumer onComplete delegate function
     */
    default void subscribe(Consumer<? super T> consumer, Consumer<? super Throwable> errorConsumer, Runnable completeConsumer) {
        this.subscribe(new FunctionalSubscriber<>(consumer, errorConsumer, completeConsumer, null));
    }

    /**
     * Subscribe to this {@link Single} instance with the given delegate functions.
     *
     * @param consumer onNext delegate function
     * @param errorConsumer onError delegate function
     * @param completeConsumer onComplete delegate function
     * @param subscriptionConsumer onSusbcribe delegate function
     */
    default void subscribe(Consumer<? super T> consumer, Consumer<? super Throwable> errorConsumer, Runnable completeConsumer,
            Consumer<? super Flow.Subscription> subscriptionConsumer) {

        this.subscribe(new FunctionalSubscriber<>(consumer, errorConsumer, completeConsumer, subscriptionConsumer));
    }
}
