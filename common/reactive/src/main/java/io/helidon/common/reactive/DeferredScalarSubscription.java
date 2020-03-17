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
package io.helidon.common.reactive;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Buffers a single item and emits it to the downstream when it requests.
 * @param <T> the item type buffered
 */
class DeferredScalarSubscription<T> extends AtomicInteger implements Flow.Subscription {

    private final Flow.Subscriber<? super T> downstream;

    private T value;

    static final int NO_VALUE_NO_REQUEST = 0;
    static final int NO_VALUE_HAS_REQUEST = 1;
    static final int HAS_VALUE_NO_REQUEST = 2;
    static final int HAS_VALUE_HAS_REQUEST = 3;
    static final int COMPLETE = 4;
    static final int CANCELED = 5;

    DeferredScalarSubscription(Flow.Subscriber<? super T> downstream) {
        this.downstream = downstream;
    }

    @Override
    public void cancel() {
        if (getAndSet(CANCELED) != CANCELED) {
            value = null;
        }
    }

    @Override
    public final void request(long n) {
        if (n <= 0L) {
            if (getAndSet(CANCELED) != CANCELED) {
                downstream.onError(
                        new IllegalArgumentException("Rule §3.9 violated: non-positive requests are forbidden"));
            }
        } else {
            for (;;) {
                int state = get();
                if (state == HAS_VALUE_NO_REQUEST) {
                    T v = value;
                    value = null;
                    if (compareAndSet(HAS_VALUE_NO_REQUEST, HAS_VALUE_HAS_REQUEST)) {
                        downstream.onNext(v);
                        if (compareAndSet(HAS_VALUE_HAS_REQUEST, COMPLETE)) {
                            downstream.onComplete();
                        }
                        break;
                    }
                } else if (state == NO_VALUE_NO_REQUEST) {
                    if (compareAndSet(NO_VALUE_NO_REQUEST, NO_VALUE_HAS_REQUEST)) {
                        break;
                    }
                } else {
                    // state == COMPLETE
                    // state == HAS_VALUE_HAS_REQUEST
                    // state == NO_VALUE_HAS_REQUEST
                    // state == CANCELED
                    break;
                }
            }
        }
    }

    /**
     * Signal the only item if possible or save it for later when there
     * is a request for it.
     * <p>
     *     This method should be called at most once and from only one thread.
     * </p>
     * @param item the item to signal and then complete the downstream
     */
    public final void complete(T item) {
        for (;;) {
            int state = get();
            if (state == NO_VALUE_HAS_REQUEST) {
                if (compareAndSet(NO_VALUE_HAS_REQUEST, HAS_VALUE_HAS_REQUEST)) {
                    downstream.onNext(item);
                    if (compareAndSet(HAS_VALUE_HAS_REQUEST, COMPLETE)) {
                        downstream.onComplete();
                    }
                    break;
                }
            } else if (state == NO_VALUE_NO_REQUEST) {
                value = item;
                if (compareAndSet(NO_VALUE_NO_REQUEST, HAS_VALUE_NO_REQUEST)) {
                    break;
                }
                value = null;
            } else {
                // state == COMPLETE
                // state == HAS_VALUE_NO_REQUEST
                // state == HAS_VALUE_HAS_REQUEST
                // state == CANCELED
                break;
            }
        }
    }

    /**
     * Calls onSubscribe of the downstream with {@code this}.
     */
    protected final void subscribeSelf() {
        downstream.onSubscribe(this);
    }

    /**
     * Returns the downstream reference.
     * @return the downstream reference
     */
    protected final Flow.Subscriber<? super T> downstream() {
        return downstream;
    }

    /**
     * Complete the downstream without emitting any items.
     */
    public final void complete() {
        for (;;) {
            int state = get();
            if (state == NO_VALUE_NO_REQUEST || state == NO_VALUE_HAS_REQUEST) {
                if (compareAndSet(state, COMPLETE)) {
                    downstream.onComplete();
                    return;
                }
            } else {
                break;
            }
        }
    }

    /**
     * Signal error to the downstream without emitting any items.
     * @param throwable the error to signal
     */
    public final void error(Throwable throwable) {
        for (;;) {
            int state = get();
            if (state == NO_VALUE_NO_REQUEST || state == NO_VALUE_HAS_REQUEST) {
                if (compareAndSet(state, COMPLETE)) {
                    downstream.onError(throwable);
                    return;
                }
            } else {
                break;
            }
        }
    }

    // Workaround for SpotBugs, Flow classes should never get serialized
    private void writeObject(ObjectOutputStream stream)
            throws IOException {
        stream.defaultWriteObject();
    }

    // Workaround for SpotBugs, Flow classes should never get serialized
    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
    }

}
