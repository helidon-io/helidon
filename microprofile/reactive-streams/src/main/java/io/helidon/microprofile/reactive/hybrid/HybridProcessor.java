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
import java.util.concurrent.Flow;

import io.helidon.common.reactive.Multi;

import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Wrapper for {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Processor}
 * or {@link io.helidon.common.reactive Helidon reactive streams} {@link java.util.concurrent.Flow.Processor},
 * to be used interchangeably.
 *
 * @param <T> type of items processor consumes
 * @param <R> type of items processor emits
 */
public class HybridProcessor<T, R> implements Flow.Processor<T, R>, Processor<T, R>, Multi<R> {
    private Processor<T, R> reactiveProcessor;
    private Flow.Processor<T, R> flowProcessor;

    private HybridProcessor(Processor<T, R> processor) {
        this.reactiveProcessor = processor;
    }

    private HybridProcessor(Flow.Processor<T, R> processor) {
        this.flowProcessor = processor;
    }

    /**
     * Create new {@link io.helidon.microprofile.reactive.hybrid.HybridProcessor}
     * from {@link java.util.concurrent.Flow.Processor}.
     *
     * @param processor {@link java.util.concurrent.Flow.Processor} to wrap
     * @param <T>       type of items processor consumes
     * @param <R>       type of items processor emits
     * @return {@link io.helidon.microprofile.reactive.hybrid.HybridProcessor}
     * compatible with {@link org.reactivestreams Reactive Streams}
     * and {@link io.helidon.common.reactive Helidon reactive streams}
     */
    public static <T, R> HybridProcessor<T, R> from(Flow.Processor<T, R> processor) {
        return new HybridProcessor<T, R>(processor);
    }

    /**
     * Create new {@link io.helidon.microprofile.reactive.hybrid.HybridProcessor}
     * from {@link org.reactivestreams.Processor}.
     *
     * @param processor {@link org.reactivestreams.Processor} to wrap
     * @param <T>       type of items processor consumes
     * @param <R>       type of items processor emits
     * @return {@link io.helidon.microprofile.reactive.hybrid.HybridProcessor}
     * compatible with {@link org.reactivestreams Reactive Streams}
     * and {@link io.helidon.common.reactive Helidon reactive streams}
     */
    public static <T, R> HybridProcessor<T, R> from(Processor<T, R> processor) {
        return new HybridProcessor<T, R>(processor);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super R> subscriber) {
        this.subscribe((Subscriber<? super R>) HybridSubscriber.from(subscriber));
    }

    @Override
    public void subscribe(Subscriber<? super R> s) {
        if (reactiveProcessor != null) {
            reactiveProcessor.subscribe(s);
        } else if (flowProcessor != null) {
            flowProcessor.subscribe(HybridSubscriber.from(s));
        } else {
            throw new InvalidParameterException("Hybrid processor has no processor");
        }
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        if (reactiveProcessor != null) {
            reactiveProcessor.onSubscribe(HybridSubscription.from(subscription));
        } else if (flowProcessor != null) {
            flowProcessor.onSubscribe(subscription);
        } else {
            throw new InvalidParameterException("Hybrid processor has no processor");
        }
    }

    @Override
    public void onSubscribe(Subscription s) {
        if (reactiveProcessor != null) {
            reactiveProcessor.onSubscribe(s);
        } else if (flowProcessor != null) {
            flowProcessor.onSubscribe(HybridSubscription.from(s));
        } else {
            throw new InvalidParameterException("Hybrid processor has no processor");
        }
    }

    @Override
    public void onNext(T item) {
        if (reactiveProcessor != null) {
            reactiveProcessor.onNext(item);
        } else if (flowProcessor != null) {
            flowProcessor.onNext(item);
        } else {
            throw new InvalidParameterException("Hybrid processor has no processor");
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (reactiveProcessor != null) {
            reactiveProcessor.onError(throwable);
        } else if (flowProcessor != null) {
            flowProcessor.onError(throwable);
        } else {
            throw new InvalidParameterException("Hybrid processor has no processor");
        }
    }

    @Override
    public void onComplete() {
        if (reactiveProcessor != null) {
            reactiveProcessor.onComplete();
        } else if (flowProcessor != null) {
            flowProcessor.onComplete();
        } else {
            throw new InvalidParameterException("Hybrid processor has no processor");
        }
    }

    @Override
    public String toString() {
        return reactiveProcessor != null ? reactiveProcessor.toString() : String.valueOf(flowProcessor);
    }
}
