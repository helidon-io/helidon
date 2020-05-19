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

public class BufferedEmittingPublisher<T> implements Flow.Publisher<T> {

    private final AtomicReference<State> state = new AtomicReference<>(State.READY_TO_EMIT);
    ConcurrentLinkedQueue<T> buffer = new ConcurrentLinkedQueue<>();
    EmittingPublisher<T> emitter = new EmittingPublisher<>();
    AtomicBoolean draining = new AtomicBoolean(false);
    AtomicReference<Throwable> error = new AtomicReference<>();

    private BufferedEmittingPublisher() {
    }

    public static <T> BufferedEmittingPublisher<T> create() {
        return new BufferedEmittingPublisher<T>();
    }

    @Override
    public void subscribe(final Flow.Subscriber<? super T> subscriber) {
        emitter.onSubscribe(() -> state.get().drain(this));
        emitter.onRequest(n -> state.get().drain(this));
        emitter.onCancel(() -> state.compareAndSet(State.READY_TO_EMIT, State.CANCELLED));
        emitter.subscribe(subscriber);
    }

    public int emit(final T item) {
        return state.get().emit(this, item);
    }

    public void fail(Throwable throwable) {
        if (state.compareAndSet(State.READY_TO_EMIT, State.FAILED)) {
            error.set(throwable);
            state.get().drain(this);
        }
    }

    /**
     * Drain the buffer up to actual demand and then complete.
     */
    public void complete() {
        if (state.compareAndSet(State.READY_TO_EMIT, State.COMPLETING)) {
            //drain buffer then complete
            State.READY_TO_EMIT.drain(this);
        }
    }

    public void completeNow() {
        if (state.compareAndSet(State.READY_TO_EMIT, State.COMPLETED)) {
            emitter.complete();
        }
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

    enum State {
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
