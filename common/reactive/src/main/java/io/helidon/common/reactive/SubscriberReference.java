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

import java.util.Optional;
import java.util.concurrent.Flow;

class SubscriberReference<T> implements Flow.Subscriber<T> {
    private Optional<Flow.Subscriber<T>> subscriber;

    private SubscriberReference(Flow.Subscriber<T> subscriber) {
        this.subscriber = Optional.of(subscriber);
    }

    static <T> SubscriberReference<T> create(Flow.Subscriber<T> subscriber) {
        return new SubscriberReference<>(subscriber);
    }

    void releaseReference() {
        this.subscriber = Optional.empty();
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscriber.ifPresent(s -> s.onSubscribe(subscription));
    }

    @Override
    public void onNext(T item) {
        subscriber.ifPresent(s -> s.onNext(item));
    }

    @Override
    public void onError(Throwable throwable) {
        subscriber.ifPresent(s -> s.onError(throwable));
    }

    @Override
    public void onComplete() {
        subscriber.ifPresent(Flow.Subscriber::onComplete);
    }
}
