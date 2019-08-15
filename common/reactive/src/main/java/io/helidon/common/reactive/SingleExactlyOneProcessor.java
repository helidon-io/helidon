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

import io.helidon.common.reactive.Flow.Processor;
import java.util.Objects;

import io.helidon.common.reactive.Flow.Subscriber;
import io.helidon.common.reactive.Flow.Subscription;

/**
 * Processor of {@code Publisher<T>} to {@code Single<T>} expecting exactly one item.
 * @param <T> item type
 */
final class SingleExactlyOneProcessor<T> implements Processor<T, T>, Subscription, Single<T> {

    private Subscriber<? super T> delegate;
    private Subscription subscription;
    private Throwable error;
    private volatile boolean requested;
    private volatile boolean done;
    private T item;

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
        requested = true;
    }

    @Override
    public void onSubscribe(Subscription s) {
        if (subscription == null) {
            subscription = s;
            if (delegate != null) {
                delegate.onSubscribe(s);
            }
            subscription.request(Long.MAX_VALUE);
        }
    }

    @Override
    public void onNext(T t) {
        if (!done) {
            if (item != null) {
                onError(new IllegalStateException("Single item already published"));
            } else {
                item = t;
            }
        }
    }

    @Override
    public void onError(Throwable ex) {
        if (!done) {
            done = true;
            error = ex;
            doComplete();
        }
    }

    @Override
    public void onComplete() {
        if (!done) {
            done = true;
            doComplete();
        }
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

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber cannot be null!");
        delegate = subscriber;
        delegate.onSubscribe(this);
    }
}
