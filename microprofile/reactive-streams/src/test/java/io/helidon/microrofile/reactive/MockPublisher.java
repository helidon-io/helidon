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

package io.helidon.microrofile.reactive;

import java.util.Optional;
import java.util.function.Consumer;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class MockPublisher implements Publisher<Long> {
    private Subscriber<? super Long> subscriber;
    private Optional<Consumer<Subscriber<? super Long>>> subscriberObserver = Optional.empty();

    @Override
    public void subscribe(Subscriber<? super Long> subscriber) {
        this.subscriber = subscriber;
        subscriberObserver.ifPresent(o -> o.accept(subscriber));
        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {

            }

            @Override
            public void cancel() {

            }
        });
    }

    public void observeSubscribe(Consumer<Subscriber<? super Long>> subscriberObserver) {
        this.subscriberObserver = Optional.of(subscriberObserver);
    }

    public void sendNext(long value) {
        subscriber.onNext(value);
    }

    public void sendOnComplete() {
        subscriber.onComplete();
    }

    public void sendOnError(Throwable t) {
        subscriber.onError(t);
    }


}
