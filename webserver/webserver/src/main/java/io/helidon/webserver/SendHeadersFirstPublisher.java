/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.util.Objects;

import io.helidon.common.reactive.Flow;

import io.opentracing.Span;

/**
 * A {@link Flow.Publisher} proxy which sends headers before first message. It accepts only single subscriber.
 *
 * @param <T> a type of the publisher
 */
class SendHeadersFirstPublisher<T> implements Flow.Publisher<T> {

    private final Object lock = new Object();
    private final HashResponseHeaders headers;
    private final Flow.Publisher<T> delegate;
    private final Span span;

    // Sent switch just once from false to true near the beginning. It use combination with volatile to faster check.
    private boolean sent;
    private volatile boolean sentVolatile;

    private volatile Flow.Subscriber<? super T> singleSubscriber;

    /**
     * Creates new instance.
     *
     * @param headers  headers to send
     * @param span     a span assigned with this publisher
     * @param delegate a publisher delegate
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    SendHeadersFirstPublisher(HashResponseHeaders headers, Span span, Flow.Publisher<T> delegate) {
        Objects.requireNonNull(delegate, "Parameter 'delegate' is null!");
        this.headers = headers;
        this.span = span;
        this.delegate = delegate;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        // Register ONLY single subscriber
        boolean hasSubscriber = singleSubscriber != null;
        if (!hasSubscriber) {
            synchronized (lock) {
                // Potentially can be registered
                hasSubscriber = singleSubscriber != null;
                if (!hasSubscriber) {
                    this.singleSubscriber = subscriber;
                }
            }
        }
        if (hasSubscriber) {
            subscriber.onError(new IllegalStateException("Only single subscriber is allowed!"));
            return;
        }

        DelegatingSubscriber ds = new DelegatingSubscriber(subscriber);
        delegate.subscribe(ds);
    }

    private class DelegatingSubscriber implements Flow.Subscriber<T> {

        private final Flow.Subscriber<? super T> delegate;

        /**
         * Creates new instance.
         *
         * @param delegate a delegate
         */
        DelegatingSubscriber(Flow.Subscriber<? super T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(T item) {
            sendHeadersIfNeeded();
            delegate.onNext(item);
        }

        private void sendHeadersIfNeeded() {
            if (headers != null && !sent && !sentVolatile) {
                synchronized (this) {
                    if (!sent && !sentVolatile) {
                        sent = true;
                        sentVolatile = true;
                        headers.send();
                    }
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            try {
                delegate.onError(throwable);
            } finally {
                if (span != null) {
                    span.finish();
                }
            }
        }

        @Override
        public void onComplete() {
            try {
                sendHeadersIfNeeded();
                delegate.onComplete();
            } finally {
                if (span != null) {
                    span.finish();
                }
            }
        }
    }
}
