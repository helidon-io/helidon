/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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

    private final ConcurrentLinkedQueue<T> buffer = new ConcurrentLinkedQueue<>();
    private volatile Throwable error;
    private BiConsumer<Long, Long> requestCallback = null;
    private Consumer<? super T> onEmitCallback = null;
    private Consumer<? super T> onCleanup = null;
    private Consumer<? super Throwable> onAbort = null;
    private volatile Flow.Subscriber<? super T> subscriber;
    // state: two bits, b1 b0, tell:
    // b0: 0/1 is not started/started (a subscriber arrived)
    // b1: 0/1 is not stopped/stopped (a publisher completed)
    // You can start and stop asynchronously and in any order
    private final AtomicInteger state = new AtomicInteger();
    // assert: contenders is initially non-zero, so nothing can be done until onSubscribe has
    //         been signalled; observe drain() after onSubscribe
    private final AtomicInteger contenders = new AtomicInteger(1);
    private final AtomicLong requested = new AtomicLong();
    // assert: ignorePending is set to enter terminal state as soon as possible: behave like
    //         the buffer is empty
    private volatile boolean ignorePending;

    // assert: emitted is accessed single-threadedly
    private long emitted;
    // assert: observing cancelled, but not ignorePending, is possible only if cancel() races
    //         against a completion (isCancelled() and isComplete() are both true)
    private boolean cancelled;

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
    public void subscribe(final Flow.Subscriber<? super T> sub) {
        if (stateChange(1)) {
            MultiError.<T>create(new IllegalStateException("Only single subscriber is allowed!"))
                    .subscribe(sub);
            return;
        }

        sub.onSubscribe(new Flow.Subscription() {
            public void request(long n) {
                if (n < 1) {
                    abort(new IllegalArgumentException("Expected request() with a positive increment"));
                    return;
                }
                long curr;
                do {
                    curr = requested.get();
                } while (curr != Long.MAX_VALUE
                        && !requested.compareAndSet(curr, Long.MAX_VALUE - curr > n ? curr + n : Long.MAX_VALUE));
                if (requestCallback != null) {
                    requestCallback.accept(n, curr);
                }
                maybeDrain();
            }

            public void cancel() {
                cancelled = true;
                ignorePending = true;
                maybeDrain();
                abort(null);
            }
        });
        subscriber = sub;
        drain(); // assert: contenders lock is already acquired
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
     * Callback executed right after {@code onNext} is actually sent.
     * <ul>
     * <li><b>param</b> {@code i} sent item</li>
     * </ul>
     *
     * @param onEmitCallback to be executed
     */
    public void onEmit(Consumer<T> onEmitCallback) {
        this.onEmitCallback = ConsumerChain.combine(this.onEmitCallback, onEmitCallback);
    }

    /**
     * Callback executed to clean up the buffer, when the Publisher terminates without passing
     * ownership of buffered items to anyone (fail, completeNow, or the Subscription is cancelled).
     * <p>
     * Use case: items buffered require handling their lifecycle, like releasing resources, or
     * returning to a pool.
     * <p>
     * Calling onCleanup multiple times will ensure that each of the provided Consumers gets a
     * chance to look at the items in the buffer. Usually you do not want to release the same
     * resource to a pool more than once, so you should usually want to ensure you pass one and
     * only one callback to onCleanup. For this reason, do not use together with clearBuffer,
     * unless you know how to have idempotent resource lifecycle management.
     *
     * @param onCleanup callback executed to clean up the buffer
     */
    public void onCleanup(Consumer<? super T> onCleanup) {
        this.onCleanup = ConsumerChain.combine(this.onCleanup, onCleanup);
    }

    /**
     * Callback executed when this Publisher fails or is cancelled in a way that the entity performing
     * emit() may be unaware of.
     * <p>
     * Use case: emit() is issued only if onRequest is received; these will cease upon a failed request
     * or when downstream requests cancellation. onAbort is going to let the entity issuing emit()
     * know that no more onRequest are forthcoming (albeit they may still happen, the items emitted
     * after onAbort will likely be discarded, and not emitted items will not be missed).
     * <p>
     * In essence the pair of onRequest and onAbort make up the interface like that of a Processor's
     * Subscription's request and cancel. The difference is only the API and the promise: we allow
     * emit() to not heed backpressure (for example, when upstream is really unable to heed
     * backpressure without introducing a buffer of its own, like is the case with many transformations
     * of the form Publisher&lt;T&gt;-&gt;Publisher&lt;Publisher&lt;T&gt;&gt;).
     * <p>
     * In the same vein there really is no restriction as to when onAbort callback can be called - there
     * is no requirement for this Publisher to establish exactly whether the entity performing emit()
     * is aware of the abort (say, a fail), or not. It is only required to ensure that the failures it
     * generates (and not merely forwards to downstream) and cancellations it received, get propagated
     * to the callback.
     *
     * @param onAbort callback executed when this Publisher fails or is cancelled
     */
    public void onAbort(Consumer<? super Throwable> onAbort) {
        this.onAbort = ConsumerChain.combine(this.onAbort, onAbort);
    }

    private void abort(Throwable th) {
        if (th != null) {
            fail(th);
        }
        if (onAbort != null) {
            onAbort.accept(th);
        }
    }

    /**
     * Emit item to the stream, if there is no immediate demand from downstream,
     * buffer item for sending when demand is signaled.
     * No-op after downstream enters terminal state. (Cancelled subscription or received onError/onComplete)
     *
     * @param item to be emitted
     */
    public void emit(final T item) {
        boolean locked = false;
        int s = state.get();
        if (s == 1) {
            // assert: attempt fast path only if started, and not stopped
            locked = contenders.get() == 0 && contenders.compareAndSet(0, 1);
        }

        // assert: this condition is the same as the loop on slow path in drain(), except the buffer
        //         isEmpty - the condition when we can skip adding, and immediately removing the item
        //         from the buffer, without loss of FIFO order.
        if (locked && !ignorePending && requested.get() > emitted && buffer.isEmpty()) {
            try {
                subscriber.onNext(item);
                if (onEmitCallback != null) {
                    onEmitCallback.accept(item);
                }
                emitted++;
            } catch (RuntimeException re) {
                // assert: fail is re-entrant (will succeed even while the contenders lock has been acquired)
                abort(re);
            } finally {
                drain();
            }
            return;
        }

        // assert: if ignorePending, buffer cleanup will happen in the future
        buffer.add(item);
        if (locked) {
            drain();
        } else {
            maybeDrain();
        }
    }

    /**
     * Send {@code onError} signal downstream, regardless of the buffer content.
     * Nothing else can be sent downstream after calling fail.
     * No-op after downstream enters terminal state. (Cancelled subscription or received onError/onComplete)
     * <p>
     * If several fail are invoked in quick succession or concurrently, no guarantee
     * which of them ends up sent to downstream.
     *
     * @param throwable Throwable to be sent downstream as onError signal.
     */
    public void fail(Throwable throwable) {
        // assert: delivering a completion signal discarding the whole buffer takes precedence over normal
        //         completion - that is, if complete() has been called, but onComplete has not been delivered
        //         yet, onError will be signalled instead, discarding the entire buffer.
        //         Otherwise the downstream may not be able to establish orderly processing: fail() can be
        //         forced as part of a borken request(), failed onNext, onRequest or onEmit callbacks. These
        //         indicate the conditions where downstream may not reach a successful request() or cancel,
        //         thus blocking the progress of the Publisher.
        error = throwable;
        completeNow();
    }

    /**
     * Send onComplete to downstream after it consumes the entire buffer. Intervening fail invocations
     * can end up sending onError instead of onComplete.
     * No-op after downstream enters terminal state. (Cancelled subscription or received onError/onComplete)
     */
    public void complete() {
        // assert: transition the state to stopped, and see if it is started; if not started, maybeDrain is futile
        // assert: if cancelled can be observed, let's not race against it to change the state - let the state
        //    remain cancelled; this does not preclude the possibility of isCancelled switching to false, just makes
        //    it a little more predictable in single-threaded cases
        // assert: even if cancelled, enter maybeDrain to ensure the cleanup occurs (complete is entrant from
        //    completeNow and fail)
        if (cancelled || stateChange(2)) {
            maybeDrain();
        }
    }

    private boolean stateChange(int s) {
        int curr;
        do {
            curr = state.get();
        } while ((curr & s) != s && !state.compareAndSet(curr, curr + s));
        return (curr & 1) > 0;
    }

    /**
     * Send {@code onComplete} signal downstream immediately, regardless of the buffer content.
     * Nothing else can be sent downstream after calling {@link BufferedEmittingPublisher#completeNow()}.
     * No-op after downstream enters terminal state. (Cancelled subscription or received onError/onComplete)
     */
    public void completeNow() {
        ignorePending = true;
        complete();
    }

    /**
     * Clear whole buffer, invoke consumer for each item before discarding it.
     * Use case: items in the buffer require discarding properly, freeing up some resources, or returning them
     * to a pool.
     * <p>
     * It is the caller's responsibility to ensure there are no concurrent invocations of clearBuffer, and
     * that there will be no emit calls in the future, as the items processed by those invocations may not be
     * consumed properly.
     * <p>
     * It is recommended that onCleanup is set up instead of using clearBuffer. Do not use together with onCleanup.
     *
     * @param consumer to be invoked for each item
     */
    public void clearBuffer(Consumer<T> consumer) {
        // I recommend deprecating this method altogether

        // Accessing buffer concurrently with drain() is inherently broken: everyone assumes that if buffer
        // is not empty, then buffer.poll() returns non-null value (this promise is broken), and everyone
        // assumes that polling buffer returns items in FIFO order (this promise is broken).
        //while (!buffer.isEmpty()) {
        //    consumer.accept(buffer.poll());
        //}
        onCleanup(consumer);
        completeNow(); // this is the current behaviour
    }

    /**
     * Check if downstream requested unbounded number of items eg. {@code Long.MAX_VALUE}.
     *
     * @return true if so
     */
    public boolean isUnbounded() {
        return requested.get() == Long.MAX_VALUE;
    }

    /**
     * Check if demand is higher than 0.
     * Returned value should be used as informative and can change asynchronously.
     *
     * @return true if demand is higher than 0
     */
    public boolean hasRequests() {
        return requested.get() > emitted;
    }

    /**
     * Check if publisher sent {@code onComplete} signal downstream.
     * Returns {@code true} right after calling {@link BufferedEmittingPublisher#completeNow()}
     * (with a caveat)
     * but after calling {@link BufferedEmittingPublisher#complete()} returns
     * {@code false} until whole buffer has been drained.
     * <p>
     * The caveat is that completeNow() does not guarantee that the onComplete signal is sent
     * before returning from completeNow() - it is only guaranteed to be sent as soon as it can be done.
     *
     * @return true if so
     */
    public boolean isCompleted() {
        // The caveat above means only that the current implementation guarantees onComplete is sent
        // before completeNow returns in single-threaded cases. When concurrent emit() or request()
        // race against completeNow, completeNow may return without entering drain() - but the concurrent
        // calls guarantee onComplete will be called as soon as they observe the buffer is empty.
        //
        // We don't want to say this in the public documentation as this is implementation detail.
        //
        // A subtle logical statement: if onError or onComplete has been signalled to downstream,
        //   isCompleted is true. But it is also true if cancel() precluded sending the signal
        //   to downstream.
        //
        //   The current implementation is: isCompleted is true if and only if no more onNext signals
        //   will be sent to downstream and no cancellation was requested.
        //
        // assert: once isCompleted becomes true, it stays true
        // question: what should it be, if complete() was called, but not onSubscribe()?
        return buffer.isEmpty() && state.get() > 1;
    }

    /**
     * Check if publisher is in terminal state CANCELLED.
     * <p>
     * It is for information only. It is not guaranteed to tell what happened to downstream, if there
     * were a concurrent cancellation and a completion.
     *
     * @return true if so
     */
    public boolean isCancelled() {
        // a stricter logic can be implemented, but is the complication warranted?

        // assert: once isCancelled becomes true, isCancelled || isCompleted stays true
        return ignorePending && cancelled && !isCompleted();
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

    /**
     * Override, if you prefer to do cleanup in a uniform way, instead of requiring everyone
     * to register a onCleanup.
     * <p>
     * Use case: a subclass that offers an implementation of BufferedEmittingPublisher&lt;T&gt; for
     * a certain type of resource T.
     */
    protected void cleanup() {
        if (onCleanup == null) {
            buffer.clear();
        } else {
            while (!buffer.isEmpty()) {
                onCleanup.accept(buffer.poll());
            }
        }
    }

    private void maybeDrain() {
        // assert: if not started, will not post too many emit() and complete() to overflow the
        //         counter
        if (contenders.getAndIncrement() == 0) {
            drain();
        }
    }

    // Key design principles:
    // - all operations on downstream are executed whilst "holding the lock".
    //   The lock acquisition is the ability to transition the value of contenders from zero to 1.
    // - any changes to state are followed by maybeDrain, so the thread inside drain() can notice
    //   that some state change has occurred:
    //   - ignorePending
    //     - error
    //     - cancelled
    //   - requested
    //   - buffer contents
    private void drain() {
        IllegalStateException ise = null;
        for (int cont = 1; cont > 0; cont = contenders.addAndGet(-cont)) {
            boolean terminateNow = ignorePending;
            try {
                while (!terminateNow && requested.get() > emitted && !buffer.isEmpty()) {
                    T item = buffer.poll();
                    subscriber.onNext(item);
                    if (onEmitCallback != null) {
                        onEmitCallback.accept(item);
                    }
                    emitted++;
                    terminateNow = ignorePending;
                }
            } catch (RuntimeException re) {
                abort(re);
            }

            if (terminateNow) {
                cleanup();
            }

            if (terminateNow || isCompleted()) {
                try {
                    // assert: cleanup in finally
                    if (!cancelled) {
                        cancelled = true;
                        if (error != null) {
                            subscriber.onError(error);
                        } else {
                            subscriber.onComplete();
                        }
                    }
                } catch (Throwable th) {
                    // assert: catch all throwables, to ensure the lock is released properly
                    //         and buffer cleanup remains reachable
                    // assert: this line is reachable only once: all subsequent iterations
                    //         will observe cancelled == true
                    ise = new IllegalStateException(th);
                } finally {
                    error = null;
                    subscriber = null;
                    requestCallback = null;
                    onEmitCallback = null;
                }
            }
        }

        if (ise != null) {
            // assert: this violates the reactive spec, but this is what the tests expect.
            //         Observe that there is no guarantee where the exception will be thrown -
            //         it may happen during request(), which is expected to finish without
            //         throwing
            throw ise;
        }
    }
}
