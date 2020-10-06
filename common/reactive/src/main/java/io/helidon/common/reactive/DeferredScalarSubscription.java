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
 *
 * @param <T> the item type buffered
 */
class DeferredScalarSubscription<T> extends AtomicInteger implements Flow.Subscription {

    private final Flow.Subscriber<? super T> downstream;

    private T value;

    static final int REQUEST_ARRIVED = 1;
    static final int VALUE_ARRIVED = 2;
    static final int DONE = REQUEST_ARRIVED | VALUE_ARRIVED;


    DeferredScalarSubscription(Flow.Subscriber<? super T> downstream) {
        this.downstream = downstream;
    }

    @Override
    public void cancel() {
        if (getAndSet(DONE) != DONE) {
            value = null;
        }
    }

    @Override
    public final void request(long n) {
        if (n <= 0L) {
            if (getAndSet(DONE) != DONE) {
                value = null;
                downstream.onError(
                        new IllegalArgumentException("Rule §3.9 violated: non-positive requests are forbidden"));
            }
            return;
        }

        int state;
        T v;
        do {
            state = get();
            v = value;
        } while (!compareAndSet(state, state | REQUEST_ARRIVED));

        if (state == VALUE_ARRIVED) {
            value = null;
            downstream.onNext(v);
            downstream.onComplete();
        }
    }

    /**
     * Signal the only item if possible or save it for later when there
     * is a request for it.
     * <p>
     * This method should be called at most once and from only one thread.
     * </p>
     *
     * @param item the item to signal and then complete the downstream
     */
    public final void complete(T item) {
        value = item; // assert: even if the race occurs, we will deliver one of the items with which complete()
        //         has been invoked - we support only the case with a single invocation of complete()
        int state = getAndUpdate(n -> n | VALUE_ARRIVED);
        if (state == REQUEST_ARRIVED) {
            value = null;
            downstream.onNext(item);
            downstream.onComplete();
        } else if (state == DONE) {
            value = null;
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
     *
     * @return the downstream reference
     */
    protected final Flow.Subscriber<? super T> downstream() {
        return downstream;
    }

    /**
     * Complete the downstream without emitting any items.
     */
    public final void complete() {
        int state = get();
        if (((state & VALUE_ARRIVED) != VALUE_ARRIVED) && compareAndSet(state, DONE)){
            downstream.onComplete();
        }
    }

    /**
     * Signal error to the downstream without emitting any items.
     *
     * @param throwable the error to signal
     */
    public final void error(Throwable throwable) {
        if (getAndSet(DONE) != DONE) {
            value = null;
            downstream.onError(throwable);
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
