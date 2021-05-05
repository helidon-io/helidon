/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
import java.util.concurrent.Flow;

import io.helidon.common.reactive.Multi;

/**
 * Combines a Subscriber and a Multi into a single processor.
 * @param <T> the input type of the Subscriber
 * @param <R> the output type of the Multi
 */
final class BridgeProcessor<T, R> implements Multi<R>, Flow.Processor<T, R> {

    private final Flow.Subscriber<? super T> front;

    private final Multi<? extends R> tail;

    BridgeProcessor(Flow.Subscriber<? super T> front, Multi<? extends R> tail) {
        this.front = front;
        this.tail = tail;
    }

    @Override
    public void onSubscribe(Flow.Subscription s) {
        Objects.requireNonNull(s, "s is null");
        front.onSubscribe(s);
    }

    @Override
    public void onNext(T t) {
        Objects.requireNonNull(t, "t is null");
        front.onNext(t);
    }

    @Override
    public void onError(Throwable t) {
        Objects.requireNonNull(t, "t is null");
        front.onError(t);
    }

    @Override
    public void onComplete() {
        front.onComplete();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super R> s) {
        tail.subscribe(s);
    }

}
