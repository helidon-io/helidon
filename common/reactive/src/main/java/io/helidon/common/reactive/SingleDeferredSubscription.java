/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Single item subscription.
 *
 * {@code this} represents the current state changed atomically upon interacting with the Subscription interface.
 */
class SingleDeferredSubscription<T> extends AtomicInteger implements Subscription {

    private final Supplier<? extends T> value;
    private final Subscriber<? super T> subscriber;

    static final int FRESH = 0;
    static final int REQUESTED = 1;
    static final int COMPLETED = 2;
    static final int CANCELED = 3;
    static final int ERRORED = 4;

    SingleDeferredSubscription(Supplier<? extends T> valueSupplier, Subscriber<? super T> subscriber) {
        super(FRESH);
        this.value = valueSupplier;
        this.subscriber = subscriber;
    }

    @Override
    public void request(long n) {
        if (n <= 0L) {
            cancel();
            subscriber.onError(new IllegalArgumentException("Rule §3.9 violated: non-positive requests are forbidden."));
        } else {
            if (compareAndSet(FRESH, REQUESTED)) {
                try {
                    subscriber.onNext(value.get());
                } catch (Throwable t) {
                    set(ERRORED);
                    subscriber.onError(t);
                }
                if (compareAndSet(REQUESTED, COMPLETED)) {
                    subscriber.onComplete();
                }
            }
        }
    }

    @Override
    public void cancel() {
        set(CANCELED);
    }
}
