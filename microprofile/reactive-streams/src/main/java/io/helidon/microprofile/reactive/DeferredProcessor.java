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
import java.util.concurrent.Flow.Processor;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.reactive.Multi;

/**
 * A processor that once subscribed by the downstream and connected to the upstream,
 * relays signals between the two.
 * @param <T> the element type of the flow
 */
final class DeferredProcessor<T> implements Multi<T>, Processor<T, T>, Subscription {

    private final AtomicBoolean once = new AtomicBoolean();

    private final AtomicReference<Subscription> upstreamDeferred = new AtomicReference<>();

    private final AtomicReference<Subscription> upstreamActual = new AtomicReference<>();

    private final AtomicLong requested = new AtomicLong();

    private final AtomicReference<Subscriber<? super T>> downstream = new AtomicReference<>();

    private final AtomicInteger wip = new AtomicInteger();

    private Throwable error;

    @Override
    public void onSubscribe(Subscription s) {
        if (downstream.get() != null) {
            upstreamActual.lazySet(SubscriptionHelper.CANCELED);
            SubscriptionHelper.deferredSetOnce(upstreamDeferred, requested, s);
        } else {
            if (SubscriptionHelper.setOnce(upstreamActual, s)) {
                if (downstream.get() != null) {
                    if (upstreamActual.compareAndSet(s, SubscriptionHelper.CANCELED)) {
                        SubscriptionHelper.deferredSetOnce(upstreamDeferred, requested, s);
                    }
                }
            }
        }
    }

    @Override
    public void onNext(T t) {
        Objects.requireNonNull(t, "t is null");
        downstream.get().onNext(t);
    }

    @Override
    public void onError(Throwable t) {
        Objects.requireNonNull(t, "t is null");
        error = t;
        // null -> DONE: deliver onError later
        // s -> DONE: deliver error now
        // CANCELED -> DONE_CANCELED: RxJavaPlugins.onError
        for (;;) {
            Subscriber<? super T> s = downstream.get();
            Subscriber<? super T> u;
            if (s == TerminatedSubscriber.CANCELED) {
                u = TerminatedSubscriber.DONE_CANCELED;
            } else
            if (s == TerminatedSubscriber.DONE_CANCELED) {
                break;
            } else {
                u = TerminatedSubscriber.DONE;
            }
            if (downstream.compareAndSet(s, u)) {
                if (s == TerminatedSubscriber.CANCELED) {
                    pluginError(t);
                } else
                if (s != null) {
                    s.onError(t);
                }
                break;
            }
        }
    }

    @Override
    public void onComplete() {
        // null -> DONE: deliver onComplete later
        // s -> DONE: deliver onComplete now
        // CANCELED -> DONE_CANCELED -> ignore onComplete
        for (;;) {
            Subscriber<? super T> s = downstream.get();
            Subscriber<? super T> u;
            if (s == TerminatedSubscriber.CANCELED) {
                u = TerminatedSubscriber.DONE_CANCELED;
            } else
            if (s == TerminatedSubscriber.DONE_CANCELED) {
                break;
            } else {
                u = TerminatedSubscriber.DONE;
            }
            if (downstream.compareAndSet(s, u)) {
                if (s != null && s != TerminatedSubscriber.CANCELED) {
                    s.onComplete();
                }
                break;
            }
        }
    }

    boolean isDone() {
        Subscriber<? super T> s = downstream.get();
        return s == TerminatedSubscriber.DONE || s == TerminatedSubscriber.DONE_CANCELED;
    }

    @Override
    public void subscribe(
            Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        if (once.compareAndSet(false, true)) {
            subscriber.onSubscribe(this);
            if (downstream.compareAndSet(null, subscriber)) {
                Subscription s = upstreamActual.get();
                if (s != null
                        && s != SubscriptionHelper.CANCELED
                        && upstreamActual.compareAndSet(s, SubscriptionHelper.CANCELED)) {
                    SubscriptionHelper.deferredSetOnce(upstreamDeferred, requested, s);
                }
            } else {
                // CANCELED || DONE_CANCELED : ignore
                // DONE -> DONE_CANCELED : signal terminal event
                for (;;) {
                    Subscriber<? super T> s = downstream.get();
                    if (s == TerminatedSubscriber.CANCELED || s == TerminatedSubscriber.DONE_CANCELED) {
                        break;
                    }
                    if (downstream.compareAndSet(s, TerminatedSubscriber.DONE_CANCELED)) {
                        Throwable ex = error;
                        if (ex != null) {
                            subscriber.onError(ex);
                        } else {
                            subscriber.onComplete();
                        }
                        break;
                    }
                }
            }
        } else {
            subscriber.onSubscribe(SubscriptionHelper.EMPTY);
            subscriber.onError(new IllegalStateException("Only one Subscriber allowed"));
        }
    }

    @Override
    public void request(long n) {
        if (n <= 0L && upstreamDeferred.get() == null) {
            onError(new IllegalArgumentException("Rule §3.9 violated: non-positive requests are forbidden"));
        } else {
            SubscriptionHelper.deferredRequest(upstreamDeferred, requested, n);
        }
    }

    @Override
    public void cancel() {
        // null -> CANCEL : do nothing
        // s -> CANCEL : do nothing
        // DONE -> DONE_CANCEL : RxJavaPlugins.onError if error != null
        for (;;) {
            Subscriber<? super T> s = downstream.get();
            Subscriber<? super T> u;
            if (s == TerminatedSubscriber.CANCELED || s == TerminatedSubscriber.DONE_CANCELED) {
                break;
            }
            if (s == TerminatedSubscriber.DONE) {
                u = TerminatedSubscriber.DONE_CANCELED;
            } else {
                u = TerminatedSubscriber.CANCELED;
            }
            if (downstream.compareAndSet(s, u)) {
                if (s == TerminatedSubscriber.DONE) {
                    Throwable ex = error;
                    if (ex != null) {
                        pluginError(ex);
                    }
                }
                break;
            }
        }

        SubscriptionHelper.cancel(upstreamActual);
        SubscriptionHelper.cancel(upstreamDeferred);
    }

    enum TerminatedSubscriber implements Subscriber<Object> {
        DONE, CANCELED, DONE_CANCELED;

        @Override
        public void onSubscribe(Subscription s) {
            // deliberately no-op
        }

        @Override
        public void onNext(Object t) {
            // deliberately no-op
        }

        @Override
        public void onError(Throwable t) {
            // deliberately no-op
        }

        @Override
        public void onComplete() {
            // deliberately no-op
        }
    }

    static void pluginError(Throwable ex) {
        Thread t = Thread.currentThread();
        t.getUncaughtExceptionHandler().uncaughtException(t, ex);
    }
}
