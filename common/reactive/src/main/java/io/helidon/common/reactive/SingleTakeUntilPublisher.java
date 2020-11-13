/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.Flow;

/**
 * Relay upstream items until the other source signals an item or completes.
 * @param <T> the upstream and output value type
 * @param <U> the other sequence indicating when the main sequence should stop
 */
final class SingleTakeUntilPublisher<T, U> extends CompletionSingle<T> {

    private final Single<T> source;

    private final Flow.Publisher<U> other;

    SingleTakeUntilPublisher(Single<T> source, Flow.Publisher<U> other) {
        this.source = source;
        this.other = other;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");

        MultiTakeUntilPublisher.TakeUntilMainSubscriber<T> parent =
                new MultiTakeUntilPublisher.TakeUntilMainSubscriber<>(subscriber);
        subscriber.onSubscribe(parent);

        other.subscribe(parent.other());
        source.subscribe(parent);
    }
}
