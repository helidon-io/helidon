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

import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.Multi;
import io.helidon.microprofile.reactive.hybrid.HybridPublisher;
import io.helidon.microprofile.reactive.hybrid.HybridSubscriber;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Conversion methods between Helidon reactive streams and the "Reactive Streams"
 */
public interface MultiRS {

    public static <T> Flow.Subscriber<T> from(Subscriber<T> subscriber) {
        return HybridSubscriber.from(subscriber);
    }

    public static <T> Subscriber<T> from(Flow.Subscriber<T> subscriber) {
        return HybridSubscriber.from(subscriber);
    }

    public static <T> Flow.Publisher<T> from(Publisher<T> publisher) {
        return HybridPublisher.from(publisher);
    }

    public static <T> Publisher<T> from(Flow.Publisher<T> publisher) {
        return HybridPublisher.from(publisher);
    }

    public static <T> Multi<T> toMulti(Publisher<T> publisher) {
        return Multi.from(HybridPublisher.from(publisher));
    }

    public static <T> Multi<T> toMulti(Flow.Publisher<T> publisher) {
        return Multi.from(publisher);
    }

    public static <U> Publisher<U> just(Stream<U> stream) {
        return MultiRS.from(Multi.just(stream.collect(Collectors.toList())));
    }

    public static <U> Publisher<U> just(U... items) {
        return MultiRS.from(Multi.just(items));
    }
}
