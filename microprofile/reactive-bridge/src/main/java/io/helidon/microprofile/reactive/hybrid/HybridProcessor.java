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
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.security.InvalidParameterException;

public class HybridProcessor<T, R> implements Flow.Processor<T, R>, Processor<T, R> {
    private Processor<T, R> reactiveProcessor;
    private Flow.Processor<T, R> flowProcessor;

    private HybridProcessor(Processor<T, R> processor) {
        this.reactiveProcessor = processor;
    }

    private HybridProcessor(Flow.Processor<T, R> processor) {
        this.flowProcessor = processor;
    }

    public static <T, R> HybridProcessor<T, R> from(Flow.Processor<T, R> processor) {
        return new HybridProcessor<T, R>(processor);
    }

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
        this.onSubscribe(subscription);
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
