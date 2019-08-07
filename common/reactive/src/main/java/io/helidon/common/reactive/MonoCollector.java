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
 * Processor of {@link Multi} to {@link Mono} that collects items from the
 * {@link Multi} and publishes a single collector object as a {@link Mono}.
 *
 * @param <T> subscribed type (collected)
 * @param <U> published type (collector)
 */
public abstract class MonoCollector<T, U>
        implements Processor<T, U>, Mono<U>, Subscription {

    private Subscriber<? super U> delegate;
    private Flow.Subscription subscription;
    private Throwable error;
    private volatile boolean requested;
    private volatile boolean done;

    /**
     * Collect the given item.
     *
     * @param item item to collect
     */
    public abstract void collect(T item);

    /**
     * Get the collected items container.
     *
     * @return T
     */
    public abstract U value();

    @Override
    public final void request(long n) {
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
    public final void cancel() {
        if (subscription != null) {
            subscription.cancel();
        }
    }

    @Override
    public final void onSubscribe(Subscription subscription) {
        Objects.requireNonNull(subscription, "subscription cannot be null!");
        if (this.subscription != null) {
            this.subscription.cancel();
        } else {
            this.subscription = subscription;
            if (delegate != null) {
                delegate.onSubscribe(this);
            }
            subscription.request(Long.MAX_VALUE);
        }
    }

    @Override
    public final void onNext(T item) {
        if (!done) {
            try {
                collect(item);
            } catch (Throwable e) {
                onError(e);
            }
        }
    }

    @Override
    public final void onError(Throwable ex) {
        if (!done) {
            done = true;
            error = ex;
            doComplete();
        }
    }

    @Override
    public final void onComplete() {
        if (!done) {
            done = true;
            if (requested) {
                doComplete();
            }
        }
    }

    private void doComplete() {
        if (delegate != null) {
            if (error != null) {
                delegate.onError(error);
            } else {
                delegate.onNext(value());
                delegate.onComplete();
            }
        }
    }

    @Override
    public final void subscribe(Subscriber<? super U> subscriber) {
        this.delegate = subscriber;
        delegate.onSubscribe(this);
    }
}
