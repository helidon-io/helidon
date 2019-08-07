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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.reactive.Flow.Subscriber;
import io.helidon.common.reactive.Flow.Subscription;

/**
 * Blocking mono subscriber.
 */
final class MonoBlockingSubscriber<T> extends CountDownLatch
        implements Subscriber<T> {

    private T value;
    private Throwable error;
    private volatile boolean cancelled;

    MonoBlockingSubscriber() {
        super(1);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        if (!cancelled) {
            subscription.request(Long.MAX_VALUE);
        }
    }

    @Override
    public void onComplete() {
        countDown();
    }

    @Override
    public void onNext(T t) {
        if (value == null) {
            value = t;
            countDown();
        }
    }

    @Override
    public void onError(Throwable t) {
        if (value == null) {
            error = t;
        }
        countDown();
    }

    T blockingGet() {
        if (getCount() != 0) {
            try {
                await();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }

        Throwable ex = error;
        if (ex != null) {
            throw new IllegalStateException(
                    "#block terminated with an error", ex);
        }
        return value;
    }

    T blockingGet(long timeout, TimeUnit unit) {
        if (getCount() != 0) {
            try {
                if (!await(timeout, unit)) {
                    throw new IllegalStateException(
                            "Timeout on blocking read for "
                            + timeout + " " + unit);
                }
            } catch (InterruptedException ex) {
                throw new IllegalStateException(
                        "#block has been interrupted", ex);
            }
        }

        Throwable ex = error;
        if (ex != null) {
            throw new IllegalStateException(
                    "#block terminated with an error", ex);
        }
        return value;
    }
}
