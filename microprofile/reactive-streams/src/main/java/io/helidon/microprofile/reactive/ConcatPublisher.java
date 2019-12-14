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

package io.helidon.microprofile.reactive;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class ConcatPublisher<T> implements Publisher<T> {
    private FirstProcessor firstProcessor;
    private SecondProcessor secondProcessor;
    private Subscriber<T> subscriber;
    private Publisher<T> firstPublisher;
    private Publisher<T> secondPublisher;
    private AtomicLong requested = new AtomicLong();


    public ConcatPublisher(Publisher<T> firstPublisher, Publisher<T> secondPublisher) {
        this.firstPublisher = firstPublisher;
        this.secondPublisher = secondPublisher;
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        this.subscriber = (Subscriber<T>) subscriber;

        this.firstProcessor = new FirstProcessor();
        this.secondProcessor = new SecondProcessor();

        firstPublisher.subscribe(firstProcessor);

        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                if (n <= 0) {
                    // https://github.com/reactive-streams/reactive-streams-jvm#3.9
                    subscriber.onError(new IllegalArgumentException("non-positive subscription request"));
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

    private class FirstProcessor implements Processor<Object, Object> {

        private Subscription subscription;
        private boolean complete = false;

        @Override
        public void subscribe(Subscriber<? super Object> s) {
        }

        @Override
        public void onSubscribe(Subscription subscription) {
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
            Optional.ofNullable(secondProcessor.subscription).ifPresent(Subscription::cancel);
            subscription.cancel();
            ConcatPublisher.this.subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            complete = true;
            Optional.ofNullable(secondProcessor.subscription).ifPresent(s -> s.request(requested.get()));
        }
    }


    private class SecondProcessor implements Processor<Object, Object> {

        private Subscription subscription;

        @Override
        public void subscribe(Subscriber<? super Object> s) {
        }

        @Override
        public void onSubscribe(Subscription subscription) {
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
