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

import java.security.InvalidParameterException;
import java.util.Objects;

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

    private Flow.Subscriber<T> flowSubscriber;
    private Subscriber<T> reactiveSubscriber;

    private HybridSubscriber(Flow.Subscriber<T> subscriber) {
        this.flowSubscriber = subscriber;
    }

    private HybridSubscriber(Subscriber<T> subscriber) {
        this.reactiveSubscriber = subscriber;
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
     * @param <T>       type of items
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
        if (flowSubscriber != null) {
            flowSubscriber.onSubscribe(HybridSubscription.from(subscription));
        } else if (reactiveSubscriber != null) {
            reactiveSubscriber.onSubscribe(HybridSubscription.from(subscription));
        } else {
            throw new InvalidParameterException("Hybrid subscriber has no subscriber");
        }
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        if (flowSubscriber != null) {
            flowSubscriber.onSubscribe(HybridSubscription.from(subscription));
        } else if (reactiveSubscriber != null) {
            reactiveSubscriber.onSubscribe(HybridSubscription.from(subscription));
        } else {
            throw new InvalidParameterException("Hybrid subscriber has no subscriber");
        }
    }

    @Override
    public void onNext(T item) {
        if (flowSubscriber != null) {
            flowSubscriber.onNext(item);
        } else if (reactiveSubscriber != null) {
            reactiveSubscriber.onNext(item);
        } else {
            throw new InvalidParameterException("Hybrid subscriber has no subscriber");
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (flowSubscriber != null) {
            flowSubscriber.onError(throwable);
        } else if (reactiveSubscriber != null) {
            reactiveSubscriber.onError(throwable);
        } else {
            throw new InvalidParameterException("Hybrid subscriber has no subscriber");
        }
    }

    @Override
    public void onComplete() {
        if (flowSubscriber != null) {
            flowSubscriber.onComplete();
        } else if (reactiveSubscriber != null) {
            reactiveSubscriber.onComplete();
        } else {
            throw new InvalidParameterException("Hybrid subscriber has no subscriber");
        }
    }

}
