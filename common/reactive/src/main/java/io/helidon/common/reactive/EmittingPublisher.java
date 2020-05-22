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
 *
 */

package io.helidon.common.reactive;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Emitting publisher for manual publishing on the same thread.
 * {@link EmittingPublisher} doesn't have any buffering capability and propagates backpressure
 * directly by returning {@code false} from {@link EmittingPublisher#emit(Object)} in case there
 * is no demand, or {@code cancel} signal has been received.
 * <p>
 *     For publishing with buffering in case of backpressure use {@link BufferedEmittingPublisher}.
 * </p>
 *
 * <p>
 * <strong>This publisher allows only a single subscriber</strong>.
 * </p>
 *
 * @param <T> type of emitted item
 */
public class EmittingPublisher<T> implements Flow.Publisher<T> {
    private volatile Flow.Subscriber<? super T> subscriber;
    private final AtomicReference<State> state = new AtomicReference<>(State.NOT_REQUESTED_YET);
    private final AtomicLong requested = new AtomicLong();
    private final AtomicBoolean emitting = new AtomicBoolean(false);
    private final AtomicBoolean subscribed = new AtomicBoolean(false);
    private final CompletableFuture<Void> deferredComplete = new CompletableFuture<>();
    private BiConsumer<Long, Long> requestCallback = (n, r) -> {};
    private Runnable onSubscribeCallback = () -> {};
    private Runnable cancelCallback = () -> {};

    EmittingPublisher() {
    }

    /**
     * Create new {@code EmittingPublisher}.
     *
     * @param <T> type of emitted item
     * @return brand new {@code EmittingPublisher}
     */
    public static <T> EmittingPublisher<T> create() {
        return new EmittingPublisher<>();
    }

    @Override
    public void subscribe(final Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");

        if (!subscribed.compareAndSet(false, true)) {
            subscriber.onSubscribe(SubscriptionHelper.CANCELED);
            subscriber.onError(new IllegalStateException("Only single subscriber is allowed!"));
            return;
        }

        this.subscriber = subscriber;

        onSubscribeCallback.run();
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(final long n) {
                if (state.get().isTerminated()) {
                    return;
                }
                if (n < 1) {
                    fail(new IllegalArgumentException("Rule §3.9 violated: non-positive request amount is forbidden"));
                    return;
                }
                requested.updateAndGet(r -> Long.MAX_VALUE - r > n ? n + r : Long.MAX_VALUE);
                state.compareAndSet(State.NOT_REQUESTED_YET, State.READY_TO_EMIT);
                requestCallback.accept(n, requested.get());
            }

            @Override
            public void cancel() {
                if (state.compareAndSet(State.NOT_REQUESTED_YET, State.CANCELLED)
                        || state.compareAndSet(State.READY_TO_EMIT, State.CANCELLED)) {
                    cancelCallback.run();
                    EmittingPublisher.this.subscriber = null;
                }
            }

        });
        deferredComplete.complete(null);
    }

    /**
     * Properly fail the stream, set publisher to cancelled state and send {@code onError} signal downstream.
     * Signal {@code onError} is sent only once, any other call to this method is no-op.
     *
     * @param throwable Sent as {@code onError} signal
     */
    public void fail(Throwable throwable) {
        if (deferredComplete.isDone()) {
            signalOnError(throwable);
        } else {
            deferredComplete.thenRun(() -> signalOnError(throwable));
        }
    }

    /**
     * Properly complete the stream, set publisher to completed state and send {@code onComplete} signal downstream.
     * Signal {@code onComplete} is sent only once, any other call to this method is no-op.
     */
    public void complete() {
        deferredComplete.thenRun(this::signalOnComplete);
    }

    private void signalOnError(Throwable throwable) {
        if (state.compareAndSet(State.NOT_REQUESTED_YET, State.FAILED)
                || state.compareAndSet(State.READY_TO_EMIT, State.FAILED)) {
            for (;;) {
                try {
                    if (emitting.getAndSet(true)) {
                        continue;
                    }
                    Flow.Subscriber<? super T> subscriber = this.subscriber;
                    if (subscriber == null) {
                        // cancel released the reference already
                        return;
                    }
                    EmittingPublisher.this.subscriber = null;
                    subscriber.onError(throwable);
                    return;
                } catch (Throwable t) {
                    throw new IllegalStateException("On error threw an exception!", t);
                } finally {
                    emitting.set(false);
                }
            }
        }
    }

    private void signalOnComplete() {
        if (state.compareAndSet(State.NOT_REQUESTED_YET, State.COMPLETED)
                || state.compareAndSet(State.READY_TO_EMIT, State.COMPLETED)) {
            for (;;) {
                try {
                    if (emitting.getAndSet(true)) {
                        continue;
                    }
                    Flow.Subscriber<? super T> subscriber = this.subscriber;
                    if (subscriber == null) {
                        // cancel released the reference already
                        return;
                    }
                    EmittingPublisher.this.subscriber = null;
                    subscriber.onComplete();
                    return;
                } finally {
                    emitting.set(false);
                }
            }
        }
    }

    /**
     * Emit one item to the stream, if there is enough requested and publisher is not cancelled,
     * item is signaled to downstream as {@code onNext} and method returns true.
     * If there is requested less than 1, nothing is sent and method returns false.
     *
     * @param item to be sent downstream
     * @return true if item successfully sent, false if canceled on no demand
     * @throws IllegalStateException if publisher is completed
     */
    public boolean emit(T item) {
        return this.state.get().emit(this, item);
    }

    /**
     * Check if publisher has been completed.
     *
     * @return true if so
     */
    public boolean isCompleted() {
        return this.state.get() == State.COMPLETED;
    }

    /**
     * Check if publisher has been cancelled.
     *
     * @return true if so
     */
    public boolean isCancelled() {
        return this.state.get() == State.CANCELLED;
    }

    /**
     * Check if publisher has been failed.
     *
     * @return true if so
     */
    public boolean isFailed() {
        return this.state.get() == State.FAILED;
    }

    /**
     * Check if demand is higher than 0. Returned value should be used
     * as informative and can change asynchronously.
     *
     * @return true if so
     */
    public boolean hasRequests() {
        return this.requested.get() > 0;
    }

    /**
     * Check if downstream requested unbounded number of items, eg. there is no backpressure.
     *
     * @return true if so
     */
    public boolean isUnbounded() {
        return this.requested.get() == Long.MAX_VALUE;
    }

    /**
     * Executed when request signal from downstream arrive.
     *
     * @param onSubscribeCallback to be executed
     */
    void onSubscribe(Runnable onSubscribeCallback) {
        this.onSubscribeCallback = RunnableChain.combine(this.onSubscribeCallback, onSubscribeCallback);
    }

    /**
     * Executed when cancel signal from downstream arrive.
     *
     * @param cancelCallback to be executed
     */
    public void onCancel(Runnable cancelCallback) {
        this.cancelCallback = RunnableChain.combine(this.cancelCallback, cancelCallback);
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

    private boolean boundedEmit(T item){
        for (;;) {
            try {
                if (emitting.getAndSet(true)) {
                    // race against parallel emits
                    // only those can decrement counter
                    continue;
                }
                Flow.Subscriber<? super T> subscriber = this.subscriber;
                if (subscriber == null) {
                    // cancel released the reference
                    return false;
                }
                if (requested.getAndUpdate(r -> r > 0 ? r != Long.MAX_VALUE ? r - 1 : Long.MAX_VALUE : 0) < 1) {
                    // there is a chance racing request will increment counter between this check and onNext
                    // lets delegate that to emit caller
                    return false;
                }
                subscriber.onNext(item);
                return true;
            } catch (NullPointerException npe) {
                throw npe;
            } catch (Throwable t) {
                fail(new IllegalStateException(t));
                return false;
            } finally {
                emitting.set(false);
            }
        }
    }

    private boolean unboundedEmit(T item) {
        try {
            subscriber.onNext(item);
            return true;
        } catch (NullPointerException npe) {
            throw npe;
        } catch (Throwable t) {
            fail(new IllegalStateException(t));
            return false;
        }
    }

    private enum State {
        NOT_REQUESTED_YET {
            @Override
            <T> boolean emit(EmittingPublisher<T> publisher, T item) {
                return false;
            }

            @Override
            boolean isTerminated() {
                return false;
            }
        },
        READY_TO_EMIT {
            @Override
            <T> boolean emit(EmittingPublisher<T> publisher, T item) {
                if (publisher.isUnbounded()) {
                    return publisher.unboundedEmit(item);
                } else {
                    return publisher.boundedEmit(item);
                }
            }

            @Override
            boolean isTerminated() {
                return false;
            }
        },
        CANCELLED {
            @Override
            <T> boolean emit(EmittingPublisher<T> publisher, T item) {
                return false;
            }

            @Override
            boolean isTerminated() {
                return true;
            }
        },
        FAILED {
            @Override
            <T> boolean emit(EmittingPublisher<T> publisher, T item) {
                return false;
            }

            @Override
            boolean isTerminated() {
                return true;
            }
        },
        COMPLETED {
            @Override
            <T> boolean emit(EmittingPublisher<T> publisher, T item) {
                throw new IllegalStateException("Emitter is completed!");
            }

            @Override
            boolean isTerminated() {
                return true;
            }
        };

        abstract <T> boolean emit(EmittingPublisher<T> publisher, T item);
        abstract boolean isTerminated();

    }
}
