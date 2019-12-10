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

package io.helidon.microprofile.reactive.hybrid;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;

import io.helidon.common.reactive.Flow;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Wrapper for {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Subscriber}
 * or {@link io.helidon.common.reactive Helidon reactive streams} {@link io.helidon.common.reactive.Flow.Subscriber},
 * to be used interchangeably.
 *
 * @param <T> type of items
 */
public class HybridSubscriber<T> implements Flow.Subscriber<T>, Subscriber<T> {

    private static final Logger LOGGER = Logger.getLogger(HybridSubscriber.class.getName());

    private Optional<Flow.Subscriber<T>> flowSubscriber = Optional.empty();
    private Optional<Subscriber<T>> reactiveSubscriber = Optional.empty();
    private Type type;

    private HybridSubscriber(Flow.Subscriber<T> subscriber) {
        this.type = Type.FLOW;
        this.flowSubscriber = Optional.of(subscriber);
    }

    private HybridSubscriber(Subscriber<T> subscriber) {
        this.type = Type.RS;
        this.reactiveSubscriber = Optional.of(subscriber);
    }

    /**
     * Create new {@link io.helidon.microprofile.reactive.hybrid.HybridSubscriber}
     * from {@link io.helidon.common.reactive.Flow.Subscriber}.
     *
     * @param subscriber {@link io.helidon.common.reactive.Flow.Subscriber} to wrap
     * @param <T>        type of items
     * @return {@link io.helidon.microprofile.reactive.hybrid.HybridSubscriber}
     * compatible with {@link org.reactivestreams Reactive Streams}
     * and {@link io.helidon.common.reactive Helidon reactive streams}
     */
    public static <T> HybridSubscriber<T> from(Flow.Subscriber<T> subscriber) {
        Objects.requireNonNull(subscriber);
        return new HybridSubscriber<T>(subscriber);
    }

    /**
     * Create new {@link io.helidon.microprofile.reactive.hybrid.HybridSubscriber}
     * from {@link org.reactivestreams.Subscriber}.
     *
     * @param subscriber {@link org.reactivestreams.Subscriber} to wrap
     * @param <T>        type of items
     * @return {@link io.helidon.microprofile.reactive.hybrid.HybridSubscriber}
     * compatible with {@link org.reactivestreams Reactive Streams}
     * and {@link io.helidon.common.reactive Helidon reactive streams}
     */
    public static <T> HybridSubscriber<T> from(Subscriber<T> subscriber) {
        Objects.requireNonNull(subscriber);
        return new HybridSubscriber<T>(subscriber);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        flowSubscriber.ifPresent(s -> s.onSubscribe(HybridSubscription.from(subscription).onCancel(this::releaseReferences)));
        reactiveSubscriber.ifPresent(s -> s.onSubscribe(HybridSubscription.from(subscription).onCancel(this::releaseReferences)));
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        Objects.requireNonNull(subscription);
        flowSubscriber.ifPresent(s -> s.onSubscribe(HybridSubscription.from(subscription)));
        reactiveSubscriber.ifPresent(s -> s.onSubscribe(subscription));
    }

    @Override
    public void onNext(T item) {
        flowSubscriber.ifPresent(s -> s.onNext(item));
        reactiveSubscriber.ifPresent(s -> s.onNext(item));
    }

    @Override
    public void onError(Throwable t) {
        flowSubscriber.ifPresent(s -> s.onError(t));
        reactiveSubscriber.ifPresent(s -> s.onError(t));
    }

    @Override
    public void onComplete() {
        flowSubscriber.ifPresent(Flow.Subscriber::onComplete);
        reactiveSubscriber.ifPresent(Subscriber::onComplete);
    }

    public void releaseReferences() {
        flowSubscriber = Optional.empty();
        reactiveSubscriber = Optional.empty();
    }

    private enum Type {
        RS, FLOW
    }

}
