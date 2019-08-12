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

import io.helidon.common.reactive.Flow.Processor;
import io.helidon.common.reactive.Flow.Subscriber;
import io.helidon.common.reactive.Flow.Subscription;

/**
 * Processor of {@code Multi<T>} to {@code Single<T>}.
 * @param <T> item type
 */
final class MultiFirstProcessor<T> implements Subscription, Processor<T, T>, Single<T> {

    private Subscriber<? super T> delegate;
    private boolean done;
    private volatile boolean requested;
    private volatile T item;
    private volatile Throwable error;

    @Override
    public void onSubscribe(Subscription subscription) {
        Objects.requireNonNull(subscription, "subscription cannot be null!");
        subscription.request(1);
    }

    @Override
    public void request(long n) {
        if (n > 0) {
            if (!requested) {
                requested = true;
                if (done) {
                    doComplete();
                }
            }
        }
    }

    @Override
    public void cancel() {
        done = true;
    }

    @Override
    public void onNext(T item) {
        if (this.item == null) {
            this.item = item;
            onComplete();
        }
    }

    @Override
    public void onError(Throwable ex) {
        if (done) {
            return;
        }
        done = true;
        error = ex;
        doComplete();
    }

    @Override
    public void onComplete() {
        if (!done) {
            done = true;
            if (requested) {
                doComplete();
            }
        }
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber cannot be null!");
        if (delegate != null) {
            throw new IllegalStateException("subscriber already set");
        }
        delegate = subscriber;
        delegate.onSubscribe(this);
    }

    private void doComplete() {
        if (delegate != null) {
            if (error != null) {
                delegate.onError(error);
            } else {
                delegate.onNext(item);
                delegate.onComplete();
            }
        }
    }
}
