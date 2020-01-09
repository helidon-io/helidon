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
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Concat streams to one.
 *
 * @param <T> item type
 */
public class ConcatPublisher<T> implements Flow.Publisher<T>, Multi<T> {
    private FirstProcessor firstProcessor;
    private SecondProcessor secondProcessor;
    private Flow.Subscriber<T> subscriber;
    private Flow.Publisher<T> firstPublisher;
    private Flow.Publisher<T> secondPublisher;
    private AtomicLong requested = new AtomicLong();

    private ConcatPublisher(Flow.Publisher<T> firstPublisher, Flow.Publisher<T> secondPublisher) {
        this.firstPublisher = firstPublisher;
        this.secondPublisher = secondPublisher;
    }

    /**
     * Create new {@link ConcatPublisher}.
     *
     * @param firstPublisher  first stream
     * @param secondPublisher second stream
     * @param <T>             item type
     * @return {@link ConcatPublisher}
     */
    public static <T> ConcatPublisher<T> create(Flow.Publisher<T> firstPublisher, Flow.Publisher<T> secondPublisher) {
        return new ConcatPublisher<>(firstPublisher, secondPublisher);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        this.subscriber = (Flow.Subscriber<T>) subscriber;

        this.firstProcessor = new FirstProcessor();
        this.secondProcessor = new SecondProcessor();

        firstPublisher.subscribe(firstProcessor);

        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                if (!StreamValidationUtils.checkRequestParam(n, subscriber::onError)) {
                    return;
                }
                requested.set(n);
                if (!firstProcessor.complete) {
                    firstProcessor.subscription.request(n);
                } else {
                    secondProcessor.subscription.request(n);
                }
            }

            @Override
            public void cancel() {
                firstProcessor.subscription.cancel();
                secondProcessor.subscription.cancel();
            }
        });
    }

    private class FirstProcessor implements Flow.Processor<Object, Object> {

        private Flow.Subscription subscription;
        private boolean complete = false;

        @Override
        public void subscribe(Flow.Subscriber<? super Object> s) {
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Objects.requireNonNull(subscription);
            this.subscription = subscription;
            secondPublisher.subscribe(secondProcessor);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onNext(Object o) {
            requested.decrementAndGet();
            ConcatPublisher.this.subscriber.onNext((T) o);
        }

        @Override
        public void onError(Throwable t) {
            complete = true;
            Optional.ofNullable(secondProcessor.subscription).ifPresent(Flow.Subscription::cancel);
            subscription.cancel();
            ConcatPublisher.this.subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            complete = true;
            Optional.ofNullable(secondProcessor.subscription).ifPresent(s -> s.request(requested.get()));
        }
    }


    private class SecondProcessor implements Flow.Processor<Object, Object> {

        private Flow.Subscription subscription;

        @Override
        public void subscribe(Flow.Subscriber<? super Object> s) {
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Objects.requireNonNull(subscription);
            this.subscription = subscription;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onNext(Object o) {
            ConcatPublisher.this.subscriber.onNext((T) o);
        }

        @Override
        public void onError(Throwable t) {
            firstProcessor.subscription.cancel();
            subscription.cancel();
            ConcatPublisher.this.subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            ConcatPublisher.this.subscriber.onComplete();
        }
    }
}
