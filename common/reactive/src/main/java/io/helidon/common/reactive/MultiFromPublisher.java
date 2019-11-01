/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 */
package io.helidon.common.reactive;

import io.helidon.common.reactive.Flow.Publisher;
import io.helidon.common.reactive.Flow.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;

/**
 * Implementation of {@link Multi} that is backed by a {@link Publisher}.
 *
 * @param <T> items type
 */
final class MultiFromPublisher<T> implements Multi<T>, org.reactivestreams.Publisher<T> {

    private final Flow.Publisher<? extends T> source;

    MultiFromPublisher(Publisher<? extends T> source) {
        Objects.requireNonNull(source, "source cannot be null!");
        this.source = source;
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        source.subscribe(subscriber);
    }

    //TODO: This is just POC
    @Override
    public void subscribe(org.reactivestreams.Subscriber<? super T> s) {
        source.subscribe(new Subscriber<T>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                s.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        subscription.request(n);
                    }

                    @Override
                    public void cancel() {
                        subscription.cancel();
                    }
                });
            }

            @Override
            public void onNext(T item) {
                s.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                s.onError(throwable);
            }

            @Override
            public void onComplete() {
                s.onComplete();
            }
        });
    }
}
