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

package io.helidon.microprofile.reactive;

import java.util.Objects;
import java.util.concurrent.Flow;

import io.helidon.common.reactive.Multi;

/**
 * Implements a Processor over the Flowable API that simply stores one
 * downstream subscriber and relays signals to it directly.
 * <p>
 * Basically it gives access to the Subscriber-operator chain for a synchronous
 * subscriber chained onto it.
 * @param <T> the element type of the input and output flows
 */
final class BasicProcessor<T> implements Multi<T>, Flow.Processor<T, T> {

    private Flow.Subscriber<? super T> downstream;

    @Override
    public void onSubscribe(Flow.Subscription s) {
        downstream.onSubscribe(s);
    }

    @Override
    public void onNext(T t) {
        Objects.requireNonNull(t, "t is null");
        downstream.onNext(t);
    }

    @Override
    public void onError(Throwable t) {
        Objects.requireNonNull(t, "t is null");
        downstream.onError(t);
    }

    @Override
    public void onComplete() {
        downstream.onComplete();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        this.downstream = subscriber;
    }
}
