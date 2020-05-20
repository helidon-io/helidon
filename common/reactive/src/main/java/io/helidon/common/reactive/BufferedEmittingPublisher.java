/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Emitting publisher for manual publishing with built-in buffer.
 *
 * <p>
 * <strong>This publisher allows only a single subscriber</strong>.
 * </p>
 *
 * @param <T> type of emitted item
 */
public class BufferedEmittingPublisher<T> implements Flow.Publisher<T> {

    private final AtomicReference<State> state = new AtomicReference<>(State.READY_TO_EMIT);
    private final ConcurrentLinkedQueue<T> buffer = new ConcurrentLinkedQueue<>();
    private final EmittingPublisher<T> emitter = new EmittingPublisher<>();
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private BiConsumer<Long, Long> requestCallback = (n, r) -> {};

    protected BufferedEmittingPublisher() {
    }

    /**
     * Create new {@link BufferedEmittingPublisher}.
     *
     * @param <T> type of emitted item
     * @return new instance of BufferedEmittingPublisher
     */
    public static <T> BufferedEmittingPublisher<T> create() {
        return new BufferedEmittingPublisher<T>();
    }

    @Override
    public void subscribe(final Flow.Subscriber<? super T> subscriber) {
        emitter.onSubscribe(() -> state.get().drain(this));
        emitter.onRequest((n, cnt) -> {
            requestCallback.accept(n, cnt);
            state.get().drain(this);
        });
        emitter.onCancel(() -> state.compareAndSet(State.READY_TO_EMIT, State.CANCELLED));
        emitter.subscribe(subscriber);
    }

    /**
     * Hook invoked after calls to {@link java.util.concurrent.Flow.Subscription#request(long)}.
     * Callback executed when request signal from downstream arrive.
     * <ul>
     * <li>param n the requested count.</li>
     * <li>param result the current total cumulative requested count; ranges between [0, {@link Long#MAX_VALUE}] where the max
     * indicates that this publisher is unbounded.</li>
     * </ul>
     *
     * @param requestCallback to be executed
     */
    public void onRequest(BiConsumer<Long, Long> requestCallback) {
        this.requestCallback = BiConsumerChain.combine(this.requestCallback, requestCallback);
    }

    /**
     * Emit item to the stream, if there is no immediate demand from downstream,
     * buffer item for sending when demand is signaled.
     *
     * @param item to be emitted
     * @return estimated size of the buffer
     * @throws IllegalStateException if cancelled, completed of failed
     */
    public int emit(final T item) {
        return state.get().emit(this, item);
    }

    /**
     * Send onError signal downstream, regardless of the buffer content.
     * Nothing else can be sent downstream after calling fail.
     * {@link BufferedEmittingPublisher#emit(T)} throws {@link IllegalStateException} after calling fail.
     *
     * @param throwable Throwable to be sent downstream as onError signal.
     */
    public void fail(Throwable throwable) {
        error.set(throwable);
        if (state.compareAndSet(State.READY_TO_EMIT, State.FAILED)) {
            emitter.fail(error.get());
        }
    }

    /**
     * Drain the buffer up to actual demand and then complete.
     * {@link BufferedEmittingPublisher#emit(T)} throws {@link IllegalStateException} after calling complete.
     */
    public void complete() {
        if (state.compareAndSet(State.READY_TO_EMIT, State.COMPLETING)) {
            //drain buffer then complete
            State.READY_TO_EMIT.drain(this);
        }
    }

    /**
     * Send onComplete signal downstream immediately, regardless of the buffer content.
     * Nothing else can be sent downstream after calling completeNow.
     * {@link BufferedEmittingPublisher#emit(T)} throws {@link IllegalStateException} after calling completeNow.
     */
    public void completeNow() {
        if (state.compareAndSet(State.READY_TO_EMIT, State.COMPLETED)) {
            emitter.complete();
        }
    }

    /**
     * Check if downstream requested unbounded.
     *
     * @return true if so
     */
    public boolean isUnbounded() {
        return this.emitter.isUnbounded();
    }

    /**
     * Check if demand is higher than 0.
     * Returned value should be used
     * as informative and can change rapidly.
     *
     * @return true if so
     */
    public boolean hasRequests() {
        return this.emitter.hasRequests();
    }

    /**
     * Check if publisher is in terminal state COMPLETED.
     *
     * @return true if so
     */
    public boolean isCompleted() {
        return this.state.get() == State.COMPLETED;
    }

    /**
     * Check if publisher is in terminal state CANCELLED.
     *
     * @return true if so
     */
    public boolean isCancelled() {
        return this.state.get() == State.CANCELLED;
    }

    /**
     * Estimated size of the buffer.
     *
     * @return estimated size of the buffer
     */
    public int bufferSize() {
        return buffer.size();
    }

    private void drainBuffer() {
        if (!draining.getAndSet(true)) {
            while (!buffer.isEmpty()) {
                if (emitter.emit(buffer.peek())) {
                    buffer.poll();
                } else {
                    break;
                }
            }
            if (buffer.isEmpty()
                    && state.compareAndSet(State.COMPLETING, State.COMPLETED)) {
                //Buffer drained, time to for lazy complete
                emitter.complete();
            }
            draining.set(false);
        }
    }

    private enum State {
        READY_TO_EMIT {
            @Override
            <T> int emit(BufferedEmittingPublisher<T> publisher, T item) {
                publisher.buffer.add(item);
                publisher.state.get().drain(publisher);
                return publisher.buffer.size();
            }

            @Override
            <T> void drain(final BufferedEmittingPublisher<T> publisher) {
                publisher.drainBuffer();
            }
        },
        CANCELLED {
            @Override
            <T> int emit(BufferedEmittingPublisher<T> publisher, T item) {
                throw new IllegalStateException("Emitter is cancelled!");
            }

            @Override
            <T> void drain(final BufferedEmittingPublisher<T> publisher) {
                //noop
            }
        },
        FAILED {
            @Override
            <T> int emit(BufferedEmittingPublisher<T> publisher, T item) {
                throw new IllegalStateException("Emitter is failed!");
            }

            @Override
            <T> void drain(final BufferedEmittingPublisher<T> publisher) {
                //Can't happen twice, internal emitter keeps the state too
                publisher.emitter.fail(publisher.error.get());
            }
        },
        COMPLETING {
            @Override
            <T> int emit(BufferedEmittingPublisher<T> publisher, T item) {
                throw new IllegalStateException("Emitter is completing!");
            }

            @Override
            <T> void drain(final BufferedEmittingPublisher<T> publisher) {
                State.READY_TO_EMIT.drain(publisher);
            }
        },
        COMPLETED {
            @Override
            <T> int emit(BufferedEmittingPublisher<T> publisher, T item) {
                throw new IllegalStateException("Emitter is completed!");
            }

            @Override
            <T> void drain(final BufferedEmittingPublisher<T> publisher) {
                //Can't happen twice, internal emitter keeps the state too
                publisher.emitter.complete();
            }
        };

        abstract <T> int emit(BufferedEmittingPublisher<T> publisher, T item);

        abstract <T> void drain(BufferedEmittingPublisher<T> publisher);

    }
}
