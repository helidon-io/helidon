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
import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class ConcatPublisher<T> implements Publisher<T> {
    private Subscriber<T> subscriber;
    private Publisher<T> firstPublisher;
    private Publisher<T> secondPublisher;
    private TransparentProcessor firstTransparentProcessor;

    public ConcatPublisher(Publisher<T> firstPublisher, Publisher<T> secondPublisher) {
        this.firstPublisher = firstPublisher;
        this.secondPublisher = secondPublisher;

    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        this.subscriber = (Subscriber<T>) subscriber;
        firstTransparentProcessor = new TransparentProcessor(firstPublisher, secondPublisher);

        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                firstTransparentProcessor.request(n);

            }

            @Override
            public void cancel() {
                firstTransparentProcessor.cancel();
            }
        });
    }

    private class TransparentProcessor implements Processor<Object, Object> {

        private Subscription subscription;
        private boolean isCompleted = false;
        private AtomicLong requests = new AtomicLong();
        private TransparentProcessor secondTransparentProcessor;

        private TransparentProcessor() {
        }

        private TransparentProcessor(Publisher<T> firstPublisher, Publisher<T> secondPublisher) {
            firstPublisher.subscribe(this);
            secondTransparentProcessor = new TransparentProcessor();
            secondPublisher.subscribe(secondTransparentProcessor);
        }

        @Override
        public void subscribe(Subscriber<? super Object> subscriber) {
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
        }

        private void request(long n) {
            requests.set(n);
            if (!isCompleted) {
                this.subscription.request(n);
            } else {
                secondTransparentProcessor.subscription.request(n);
            }
        }

        private void cancel() {
            this.subscription.cancel();
            this.secondTransparentProcessor.subscription.cancel();
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onNext(Object t) {
            requests.decrementAndGet();
            ConcatPublisher.this.subscriber.onNext((T) t);
        }

        @Override
        public void onError(Throwable t) {
                this.isCompleted = true;
                ConcatPublisher.this.subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            if (!Objects.isNull(secondTransparentProcessor)) {
                this.isCompleted = true;
                this.secondTransparentProcessor.subscription.request(requests.get());
            } else {
                ConcatPublisher.this.subscriber.onComplete();
            }
        }
    }
}
