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

import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class CoupledProcessor<T, R> implements Processor<T, R> {

    private Subscriber<T> subscriber;
    private Publisher<T> publisher;
    private Subscriber<? super R> downStreamSubscriber;
    private Subscription upStreamSubscription;
    private Subscription downStreamsSubscription;


    public CoupledProcessor(Subscriber<T> subscriber, Publisher<T> publisher) {
        this.subscriber = subscriber;
        this.publisher = publisher;
    }

    @Override
    public void subscribe(Subscriber<? super R> downStreamSubscriber) {

        this.downStreamSubscriber = downStreamSubscriber;
        publisher.subscribe(new Subscriber<T>() {

            @Override
            public void onSubscribe(Subscription downStreamsSubscription) {
                CoupledProcessor.this.downStreamsSubscription = downStreamsSubscription;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void onNext(T t) {
                downStreamSubscriber.onNext((R) t);
            }

            @Override
            public void onError(Throwable t) {
                downStreamSubscriber.onError(t);
            }

            @Override
            public void onComplete() {
                downStreamSubscriber.onComplete();
            }
        });

        downStreamSubscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                downStreamsSubscription.request(n);
            }

            @Override
            public void cancel() {
                subscriber.onComplete();
                downStreamSubscriber.onComplete();
                downStreamsSubscription.cancel();
            }
        });
    }

    @Override
    public void onSubscribe(Subscription upStreamSubscription) {
        this.upStreamSubscription = upStreamSubscription;
        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                upStreamSubscription.request(n);
            }

            @Override
            public void cancel() {
                upStreamSubscription.cancel();
                downStreamsSubscription.cancel();
                downStreamSubscriber.onComplete();
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onNext(T t) {
        subscriber.onNext(t);
    }

    @Override
    public void onError(Throwable t) {
        subscriber.onError(t);
    }

    @Override
    public void onComplete() {
        subscriber.onComplete();
        upStreamSubscription.cancel();
        downStreamSubscriber.onComplete();
        downStreamsSubscription.cancel();
    }
}
