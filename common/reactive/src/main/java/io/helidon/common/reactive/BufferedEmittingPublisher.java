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
import java.util.function.Consumer;

/**
 * Emitting publisher for manual publishing with built-in buffer for handling backpressure.
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
    private final AtomicBoolean emitting = new AtomicBoolean(false);
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private BiConsumer<Long, Long> requestCallback = (n, r) -> {};
    private Consumer<T> emitCallback = (i) -> {};
    private Runnable cancelCallback = () -> {};

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
        emitter.onCancel(() -> {
            cancelCallback.run();
            state.compareAndSet(State.READY_TO_EMIT, State.CANCELLED);
        });
        emitter.subscribe(subscriber);
    }

    /**
     * Callback executed when request signal from downstream arrive.
     * <ul>
     * <li><b>param</b> {@code n} the requested count.</li>
     * <li><b>param</b> {@code result} the current total cumulative requested count, ranges between [0, {@link Long#MAX_VALUE}]
     * where the max indicates that this publisher is unbounded.</li>
     * </ul>
     *
     * @param requestCallback to be executed
     */
    public void onRequest(BiConsumer<Long, Long> requestCallback) {
        this.requestCallback = BiConsumerChain.combine(this.requestCallback, requestCallback);
    }

    /**
     * Executed when cancel signal from downstream arrive.
     * If the callback is already registered, old one is removed.
     *
     * @param cancelCallback to be executed
     */
    public void onCancel(Runnable cancelCallback) {
        this.cancelCallback = RunnableChain.combine(this.cancelCallback, cancelCallback);
    }

    /**
     * Callback executed when each item is successfully emitted.
     *
     * @param emitCallback to be executed
     */
    public void onEmit(Consumer<T> emitCallback) {
        this.emitCallback = ConsumerChain.combine(this.emitCallback, emitCallback);
    }

    /**
     * Emit item to the stream, if there is no immediate demand from downstream,
     * buffer item for sending when demand is signaled.
     *
     * @param item to be emitted
     * @return actual size of the buffer, value should be used as informative and can change asynchronously
     * @throws IllegalStateException if cancelled, completed of failed
     */
    public int emit(final T item) {
        return state.get().emit(this, item);
    }

    /**
     * Send {@code onError} signal downstream, regardless of the buffer content.
     * Nothing else can be sent downstream after calling fail.
     * {@link BufferedEmittingPublisher#emit(Object)} throws {@link IllegalStateException} after calling fail.
     *
     * @param throwable Throwable to be sent downstream as onError signal.
     */
    public void fail(Throwable throwable) {
        error.set(throwable);
        if (state.compareAndSet(State.READY_TO_EMIT, State.FAILED)) {
            emitter.fail(throwable);
        }
    }

    /**
     * Drain the buffer, in case of not sufficient demands wait for more requests,
     * then send {@code onComplete} signal to downstream.
     * {@link BufferedEmittingPublisher#emit(Object)} throws {@link IllegalStateException} after calling complete.
     */
    public void complete() {
        if (state.compareAndSet(State.READY_TO_EMIT, State.COMPLETING)) {
            //drain buffer then complete
            State.READY_TO_EMIT.drain(this);
        }
    }

    /**
     * Send {@code onComplete} signal downstream immediately, regardless of the buffer content.
     * Nothing else can be sent downstream after calling {@link BufferedEmittingPublisher#completeNow()}.
     * {@link BufferedEmittingPublisher#emit(Object)} throws {@link IllegalStateException} after calling completeNow.
     */
    public void completeNow() {
        if (state.compareAndSet(State.READY_TO_EMIT, State.COMPLETED)) {
            emitter.complete();
        }
    }

    /**
     * Clear whole buffer, invoke consumer for each item before discarding it.
     *
     * @param consumer to be invoked for each item
     */
    public void clearBuffer(Consumer<T> consumer) {
        while (!buffer.isEmpty()) {
            consumer.accept(buffer.poll());
        }
    }

    /**
     * Check if downstream requested unbounded number of items eg. {@code Long.MAX_VALUE}.
     *
     * @return true if so
     */
    public boolean isUnbounded() {
        return this.emitter.isUnbounded();
    }

    /**
     * Check if demand is higher than 0.
     * Returned value should be used as informative and can change asynchronously.
     *
     * @return true if demand is higher than 0
     */
    public boolean hasRequests() {
        return this.emitter.hasRequests();
    }

    /**
     * Check if publisher sent {@code onComplete} signal downstream.
     * Returns {@code true} right after calling {@link BufferedEmittingPublisher#completeNow()}
     * but after calling {@link BufferedEmittingPublisher#complete()} returns
     * {@code false} until whole buffer has been drained.
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
     * Returned value should be used as informative and can change asynchronously.
     *
     * @return estimated size of the buffer
     */
    public int bufferSize() {
        return buffer.size();
    }

    private void drainBuffer() {
        if (!draining.getAndSet(true)) {
            while (!buffer.isEmpty()) {
                T item = buffer.peek();
                if (emitter.emit(item)) {
                    emitCallback.accept(item);
                    buffer.poll();
                } else {
                    break;
                }
            }
            if (buffer.isEmpty()
                    && state.compareAndSet(State.COMPLETING, State.COMPLETED)) {
                //Buffer drained, time for complete
                emitter.complete();
            }
            draining.set(false);
        }
    }

    private int emitOrBuffer(T item) {
        for (;;) {
            try {
                if (emitting.getAndSet(true)) {
                    // race against parallel emits
                    // only those can add to buffer
                    continue;
                }
                if (buffer.isEmpty() && emitter.emit(item)) {
                    // Buffer drained, emit successful
                    // saved time by skipping buffer
                    return 0;
                } else {
                    //safe slower path thru buffer
                    buffer.add(item);
                    state.get().drain(this);
                    return buffer.size();
                }
            } finally {
                emitting.set(false);
            }
        }
    }

    private enum State {
        READY_TO_EMIT {
            @Override
            <T> int emit(BufferedEmittingPublisher<T> publisher, T item) {
                return publisher.emitOrBuffer(item);
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
                throw new IllegalStateException("Emitter is in failed state!");
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
