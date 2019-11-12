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

import io.helidon.common.reactive.Flow;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.security.InvalidParameterException;

public class HybridSubscriber<T> implements Flow.Subscriber<T>, Subscriber<T> {

    private Flow.Subscriber<T> flowSubscriber;
    private Subscriber<T> reactiveSubscriber;

    private HybridSubscriber(Flow.Subscriber<T> subscriber) {
        this.flowSubscriber = subscriber;
    }

    private HybridSubscriber(Subscriber<T> subscriber) {
        this.reactiveSubscriber = subscriber;
    }

    public static <T> HybridSubscriber<T> from(Flow.Subscriber<T> subscriber) {
        return new HybridSubscriber<T>(subscriber);
    }

    public static <T> HybridSubscriber<T> from(Subscriber<T> subscriber) {
        return new HybridSubscriber<T>(subscriber);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        reactiveSubscriber.onSubscribe(HybridSubscription.from(subscription));
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        flowSubscriber.onSubscribe(HybridSubscription.from(subscription));
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
