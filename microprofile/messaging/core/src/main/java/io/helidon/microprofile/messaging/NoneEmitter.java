/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.microprofile.messaging;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Emitter used for {@link org.eclipse.microprofile.reactive.messaging.OnOverflow.Strategy#NONE}.
 */
class NoneEmitter extends OutgoingEmitter implements Subscription {

    private volatile Subscriber<? super Object> subscriber;
    private final AtomicLong requested = new AtomicLong();
    private volatile boolean cancelled = false;

    NoneEmitter(String channelName, String fieldName, OnOverflow onOverflow) {
        super(channelName, fieldName, onOverflow);
    }

    @Override
    public void request(long n) {
        if (n < 1) {
            subscriber.onError(new IllegalArgumentException("Rule §3.9 violated: non-positive request amount is forbidden"));
            cancelled = true;
            return;
        }
        requested.updateAndGet(r -> Long.MAX_VALUE - r > n ? n + r : Long.MAX_VALUE);
    }

    @Override
    public void cancel() {
        cancelled = true;
        subscriber = null;
    }

    @Override
    public CompletionStage<Void> send(Object msg) {
        try {
            lock().lock();
            if (subscriber == null) {
                return CompletableFuture.failedStage(new IllegalStateException("Not subscribed yet!"));
            }
            validate(msg);
            CompletableFuture<Void> acked = new CompletableFuture<>();
            send(MessageUtils.create(msg, acked));
            return acked;
        } finally {
            lock().unlock();
        }
    }

    @Override
    public <M extends Message<? extends Object>> void send(M msg) {
        try {
            lock().lock();
            validate(msg);
            if (subscriber != null) {
                if (requested.getAndUpdate(r -> r > 0 ? r != Long.MAX_VALUE ? r - 1 : Long.MAX_VALUE : 0) < 1) {
                    return;
                }
                subscriber.onNext(msg);
            }
        } finally {
            lock().unlock();
        }
    }

    @Override
    public void error(Exception e) {
        try {
            lock().lock();

            this.cancelled = true;
            super.error(e);
            if (subscriber != null) {
                subscriber.onError(e);
                subscriber = null;
            }
        } finally {
            lock().unlock();
        }
    }

    @Override
    public void complete() {
        try {
            lock().lock();

            super.complete();
            if (subscriber != null) {
                subscriber.onComplete();
                subscriber = null;
            }
        } finally {
            lock().unlock();
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public boolean hasRequests() {
        return requested.get() > 0;
    }

    @Override
    Publisher<?> getPublisher() {
        return (Publisher<Object>) s -> {
            Objects.requireNonNull(s);
            NoneEmitter.this.subscriber = s;
            s.onSubscribe(this);
        };
    }
}
