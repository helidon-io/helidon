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

package io.helidon.microprofile.connectors.kafka;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Emitting reactive streams publisher to be used by {@code ReactiveStreams.fromPublisher},
 * should be deprecated in favor of {@code org.eclipse.microprofile.reactive.messaging.Emitter}
 * in the future version of messaging.
 *
 * @param <T> type of emitted item
 */
class EmittingPublisher<T> implements Publisher<T> {

    private static final Logger LOGGER = Logger.getLogger(EmittingPublisher.class.getName());
    private Subscriber<? super T> subscriber;
    private final AtomicReference<State> state = new AtomicReference<>(State.NOT_REQUESTED_YET);
    private final AtomicLong requested = new AtomicLong();
    private final AtomicBoolean terminated = new AtomicBoolean();
    private final Callback<Long> requestsCallback;

    protected EmittingPublisher(Callback<Long> requestsCallback) {
        this.requestsCallback = requestsCallback;
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        this.subscriber = subscriber;
        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(final long n) {
                if (n < 1) {
                    fail(new IllegalArgumentException("Rule §3.9 violated: non-positive request amount is forbidden"));
                }
                LOGGER.fine(String.format("Request %s events", n));
                requested.updateAndGet(r -> Long.MAX_VALUE - r > n ? n + r : Long.MAX_VALUE);
                state.compareAndSet(State.NOT_REQUESTED_YET, State.READY_TO_EMIT);
                requestsCallback.nofity(n);
            }

            @Override
            public void cancel() {
                LOGGER.fine("Subscription cancelled");
                state.compareAndSet(State.NOT_REQUESTED_YET, State.CANCELLED);
                state.compareAndSet(State.READY_TO_EMIT, State.CANCELLED);
                EmittingPublisher.this.subscriber = null;
            }

        });
    }

    /**
     * Properly fail the stream, set publisher to cancelled state and send {@code onError} signal downstream.
     * Signal {@code onError} is sent only once, any other call to this method is no-op.
     *
     * @param throwable Sent as {@code onError} signal
     */
    void fail(Throwable throwable) {
        if (!terminated.getAndSet(true) && subscriber != null) {
            state.compareAndSet(State.NOT_REQUESTED_YET, State.CANCELLED);
            state.compareAndSet(State.READY_TO_EMIT, State.CANCELLED);
            this.subscriber.onError(throwable);
        }
    }

    /**
     * Properly complete the stream, set publisher to completed state and send {@code onComplete} signal downstream.
     * Signal {@code onComplete} is sent only once, any other call to this method is no-op.
     */
    void complete() {
        if (!terminated.getAndSet(true) && subscriber != null) {
            state.compareAndSet(State.NOT_REQUESTED_YET, State.COMPLETED);
            state.compareAndSet(State.READY_TO_EMIT, State.COMPLETED);
            this.subscriber.onComplete();
        }
    }

    /**
     * Emit one item to the stream, if there is enough requested, item is signaled to downstream as {@code onNext}
     * and method returns true. If there is requested less than 1, nothing is sent and method returns false.
     *
     * @param item to be sent downstream
     * @return true if item successfully sent
     * @throws java.lang.IllegalStateException if publisher is cancelled
     */
    boolean emit(T item) {
        return this.state.get().emit(this, item);
    }

    boolean isTerminated() {
        return terminated.get();
    }

    /**
     * Check if publisher is in terminal state CANCELLED.
     *
     * @return true if so
     */
    boolean isCancelled() {
        return this.state.get() == State.CANCELLED;
    }

    /**
     * Check if publisher is in terminal state COMPLETED.
     *
     * @return true if so
     */
    boolean isCompleted() {
        return this.state.get() == State.COMPLETED;
    }

    private enum State {
        NOT_REQUESTED_YET {
            @Override
            <T> boolean emit(EmittingPublisher<T> publisher, T item) {
                return false;
            }
        },
        READY_TO_EMIT {
            @Override
            <T> boolean emit(EmittingPublisher<T> publisher, T item) {
                if (publisher.requested.getAndDecrement() < 1) {
                    return false;
                }
                try {
                    publisher.subscriber.onNext(item);
                    return true;
                } catch (Throwable t) {
                    // We need to catch the error here because emit is invoked in other context
                    publisher.fail(t);
                    return false;
                }
            }
        },
        CANCELLED {
            @Override
            <T> boolean emit(EmittingPublisher<T> publisher, T item) {
                // No-op
                return false;
            }
        },
        COMPLETED {
            @Override
            <T> boolean emit(EmittingPublisher<T> publisher, T item) {
                // No-op
                return false;
            }
        };

        abstract <T> boolean emit(EmittingPublisher<T> publisher, T item);

    }
}
