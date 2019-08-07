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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.reactive.Flow.Subscriber;
import io.helidon.common.reactive.Flow.Subscription;

/**
 * Mono subscriber.
 */
final class MonoSubscriber<T> implements Subscriber<T>, Subscription {

    private final Subscriber<? super T> actual;
    private final AtomicBoolean requested;
    private Subscription s;
    private boolean done;

    MonoSubscriber(Subscriber<? super T> s) {
        requested = new AtomicBoolean(false);
        actual = s;
    }

    @Override
    public void onSubscribe(Subscription s) {
        Objects.requireNonNull(s, "Subscription cannot be null");
        if (this.s != null) {
            s.cancel();
            this.s.cancel();
        } else {
            this.s = s;
            actual.onSubscribe(this);
        }
    }

    @Override
    public void onNext(T t) {
        if (done) {
            return;
        }

        actual.onNext(t);
        onComplete();
    }

    @Override
    public void onError(Throwable t) {
        if (done) {
            return;
        }
        done = true;
        actual.onError(t);
    }

    @Override
    public void onComplete() {
        if (done) {
            return;
        }
        done = true;
        actual.onComplete();
    }

    @Override
    public void request(long n) {
        if (requested.compareAndSet(false, true)) {
            s.request(1);
        }
    }

    @Override
    public void cancel() {
        s.cancel();
    }
}
