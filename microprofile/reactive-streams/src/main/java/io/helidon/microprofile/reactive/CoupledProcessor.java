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
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.reactive.RecursionUtils;
import io.helidon.microprofile.reactive.hybrid.HybridSubscriber;

import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class CoupledProcessor<T, R> implements Processor<T, R> {

    private HybridSubscriber<T> passedInSubscriber;
    private Publisher<T> passedInPublisher;
    private HybridSubscriber<? super R> outletSubscriber;
    private Subscriber<? super T> inletSubscriber;
    private Subscription inletSubscription;
    private Subscription passedInPublisherSubscription;

    private AtomicBoolean cancelled = new AtomicBoolean(false);


    public CoupledProcessor(Subscriber<T> passedInSubscriber, Publisher<T> passedInPublisher) {
        this.passedInSubscriber = HybridSubscriber.from(passedInSubscriber);
        this.passedInPublisher = passedInPublisher;
        this.inletSubscriber = this;
    }

    @Override
    public void subscribe(Subscriber<? super R> outletSubscriber) {
        this.outletSubscriber = HybridSubscriber.from(outletSubscriber);
        passedInPublisher.subscribe(new Subscriber<T>() {

            @Override
            public void onSubscribe(Subscription passedInPublisherSubscription) {
                Objects.requireNonNull(passedInPublisherSubscription);
                // https://github.com/reactive-streams/reactive-streams-jvm#2.5
                if (Objects.nonNull(CoupledProcessor.this.passedInPublisherSubscription) || cancelled.get()) {
                    passedInPublisherSubscription.cancel();
                    return;
                }
                CoupledProcessor.this.passedInPublisherSubscription = passedInPublisherSubscription;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void onNext(T t) {
                Objects.requireNonNull(t);
                outletSubscriber.onNext((R) t);
            }

            @Override
            public void onError(Throwable t) {
                //Passed in publisher sent onError
                cancelled.set(true);
                Objects.requireNonNull(t);
                outletSubscriber.onError(t);
                passedInSubscriber.onError(t);
                inletSubscriber.onError(t);
                Optional.ofNullable(inletSubscription).ifPresent(Subscription::cancel);//TODO: 203
            }

            @Override
            public void onComplete() {
                //Passed in publisher completed
                cancelled.set(true);
                outletSubscriber.onComplete();
                passedInSubscriber.onComplete();
                Optional.ofNullable(inletSubscription).ifPresent(Subscription::cancel);//TODO: 203
            }
        });

        outletSubscriber.onSubscribe(new Subscription() {

            @Override
            public void request(long n) {
                if (RecursionUtils.recursionDepthExceeded(2)) {
                    // https://github.com/reactive-streams/reactive-streams-jvm#3.3
                    outletSubscriber.onError(new IllegalCallerException("Recursion depth exceeded 3.3"));
                }
                passedInPublisherSubscription.request(n);
            }

            @Override
            public void cancel() {
                // Cancel from outlet subscriber
                passedInSubscriber.onComplete();
                Optional.ofNullable(inletSubscription).ifPresent(Subscription::cancel);
                CoupledProcessor.this.passedInSubscriber.releaseReferences();
                CoupledProcessor.this.outletSubscriber.releaseReferences();
            }
        });
    }

    @Override
    public void onSubscribe(Subscription inletSubscription) {
        Objects.requireNonNull(inletSubscription);
        // https://github.com/reactive-streams/reactive-streams-jvm#2.5
        if (Objects.nonNull(this.inletSubscription) || cancelled.get()) {
            inletSubscription.cancel();
            return;
        }
        this.inletSubscription = inletSubscription;
        passedInSubscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                if (RecursionUtils.recursionDepthExceeded(5)) {
                    // https://github.com/reactive-streams/reactive-streams-jvm#3.3
                    passedInSubscriber.onError(new IllegalCallerException("Recursion depth exceeded 3.3"));
                }
                inletSubscription.request(n);
            }

            @Override
            public void cancel() {
                // Cancel from passed in subscriber
                if (cancelled.getAndSet(true)) {
                    return;
                }
                inletSubscription.cancel();
                outletSubscriber.onComplete();
                passedInPublisherSubscription.cancel();
                passedInSubscriber.releaseReferences();
                outletSubscriber.releaseReferences();
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onNext(T t) {
        passedInSubscriber.onNext(Objects.requireNonNull(t));
    }

    @Override
    public void onError(Throwable t) {
        cancelled.set(true);
        // Inlet/upstream publisher sent error
        passedInSubscriber.onError(Objects.requireNonNull(t));
        outletSubscriber.onError(t);
        passedInPublisherSubscription.cancel();
    }

    @Override
    public void onComplete() {
        // Inlet/upstream publisher completed
        cancelled.set(true);
        passedInSubscriber.onComplete();
        outletSubscriber.onComplete();
        passedInPublisherSubscription.cancel();
    }

}
