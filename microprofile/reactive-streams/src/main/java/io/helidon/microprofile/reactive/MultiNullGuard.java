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
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.reactive.Multi;

/**
 * Ensures the intermediate Subscriber throws {@link NullPointerException}
 * if onNext or onError is called with {@code null}.
 * @param <T> the element type of the flow
 */
final class MultiNullGuard<T> implements Multi<T> {

    private final Flow.Publisher<T> source;

    MultiNullGuard(Flow.Publisher<T> source) {
        this.source = source;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> s) {
        source.subscribe(new NullGuard<>(s));
    }

    static final class NullGuard<T> extends AtomicBoolean implements Flow.Subscriber<T> {

        private static final long serialVersionUID = -5247348177689779682L;

        private final Flow.Subscriber<? super T> downstream;

        NullGuard(Flow.Subscriber<? super T> downstream) {
            this.downstream = downstream;
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
        public void onSubscribe(Flow.Subscription s) {
            if (compareAndSet(false, true)) {
                downstream.onSubscribe(s);
            } else {
                s.cancel();
                //throw new IllegalStateException("Subscription already set!");
            }
        }
    }
}
