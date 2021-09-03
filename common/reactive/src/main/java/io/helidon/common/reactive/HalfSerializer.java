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

package io.helidon.common.reactive;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Serialize calls to {@link Flow.Subscriber} methods to allow {@code onError} or
 * {@code onComplete} to be called concurrently with an otherwise sequential
 * {@code onNext} call.
 */
final class HalfSerializer {

    /** Utility class. */
    private HalfSerializer() {
        throw new IllegalStateException("No instances!");
    }

    /**
     * Signal onNext if not already terminated concurrently.
     * <p>
     *     If there is a call to the other {@link #onError} or
     *     {@link #onComplete} methods while the emission is going
     *     on, the relevant terminal event is also emitted by this
     *     method.
     * </p>
     * <p>
     *     This method must be called only by one thread at a time.
     * </p>
     * @param downstream the receiver of the item
     * @param wip the work-in-progress indicator
     * @param error holds the exception signaled concurrently
     * @param item the item to signal to the downstream
     * @param <T> the element type of the sequence
     */
    public static <T> void onNext(Flow.Subscriber<? super T> downstream,
                                     AtomicInteger wip,
                                     AtomicReference<Throwable> error,
                                     T item) {
        if (wip.compareAndSet(0, 1)) {
            downstream.onNext(item);
            if (wip.decrementAndGet() != 0) {
                Throwable ex = error.getAndSet(TERMINATED_EXCEPTION);
                if (ex != null && ex != TERMINATED_EXCEPTION) {
                    downstream.onError(ex);
                } else {
                    downstream.onComplete();
                }
            }
        }
    }

    /**
     * Signal an error and deliver it to the downstream if possible.
     * <p>
     *     This method can be called concurrently from multiple
     *     threads with the other methods, but only the first
     *     Throwable is allowed to pass through.
     * </p>
     * @param downstream the receiver of the error
     * @param wip the work-in-progress indicator
     * @param error holds the exception signaled concurrently
     * @param throwable the error to signal to the downstream
     */
    public static void onError(Flow.Subscriber<?> downstream,
                               AtomicInteger wip,
                               AtomicReference<Throwable> error,
                               Throwable throwable) {
        if (error.compareAndSet(null, throwable)) {
            if (wip.getAndIncrement() == 0) {
                error.lazySet(TERMINATED_EXCEPTION);
                downstream.onError(throwable);
            }
        }
    }

    /**
     * Signals completion if possible.
     * <p>
     *     This method can be called concurrently from multiple
     *     threads with the other methods, but a concurrent
     *     {@link #onError} may override the terminal signal
     *     and emit an error instead.
     * </p>
     * @param downstream the receiver of the error
     * @param wip the work-in-progress indicator
     * @param error holds the exception signaled concurrently
     */
    public static void onComplete(Flow.Subscriber<?> downstream,
                                  AtomicInteger wip,
                                  AtomicReference<Throwable> error) {
        if (wip.getAndIncrement() == 0) {
            Throwable ex = error.getAndSet(TERMINATED_EXCEPTION);
            if (ex != null && ex != TERMINATED_EXCEPTION) {
                downstream.onError(ex);
            } else {
                downstream.onComplete();
            }
        }
    }

    /**
     * The singleton instanceo of a stackless exception indicating no
     * further exceptions should be stored.
     * <p>
     *     Do not leak this instance!
     * </p>
     */
    private static final TerminatedThrowable TERMINATED_EXCEPTION = new TerminatedThrowable();

    /**
     * Indicates no further exceptions should be stored.
     */
    static final class TerminatedThrowable extends Throwable {

        TerminatedThrowable() {
            super("Terminated");
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
