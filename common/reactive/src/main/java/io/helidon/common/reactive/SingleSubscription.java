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
 */
package io.helidon.common.reactive;

import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single fixed item subscription.
 *
 * {@code this} represents the current state changed atomically upon interacting with the Subscription interface.
 */
final class SingleSubscription<T> extends AtomicInteger implements Subscription {

    private final T value;
    private final Subscriber<? super T> subscriber;

    static final int FRESH = 0;
    static final int REQUESTED = 1;
    static final int COMPLETED = 2;
    static final int CANCELED = 3;

    SingleSubscription(T value, Subscriber<? super T> subscriber) {
        super(FRESH);
        this.value = value;
        this.subscriber = subscriber;
    }

    @Override
    public void request(long n) {
        if (n <= 0L) {
            cancel();
            subscriber.onError(new IllegalArgumentException("Rule §3.9 violated: non-positive requests are forbidden."));
        } else {
            if (compareAndSet(FRESH, REQUESTED)) {
                subscriber.onNext(value);
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
