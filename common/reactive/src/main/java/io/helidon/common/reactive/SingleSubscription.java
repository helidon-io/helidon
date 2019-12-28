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

import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single fixed item subscription.
 */
final class SingleSubscription<T> implements Subscription {

    private final T value;
    private final Subscriber<? super T> subscriber;
    private final AtomicBoolean delivered;
    private final AtomicBoolean canceled;

    SingleSubscription(T value, Subscriber<? super T> subscriber) {
        this.value = value;
        this.subscriber = subscriber;
        this.delivered = new AtomicBoolean(false);
        this.canceled = new AtomicBoolean(false);
    }

    @Override
    public void request(long n) {
        if (n >= 0 && !canceled.get()) {
            if (delivered.compareAndSet(false, true)) {
                subscriber.onNext(value);
                subscriber.onComplete();
            }
        }
    }

    @Override
    public void cancel() {
        canceled.set(true);
    }
}
