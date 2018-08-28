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

package io.helidon.common.reactive.valve;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

/**
 * Transforms reactive {@link Valve} into a blocking {@link Iterator}.
 * <p>
 * If the original {@code Valve} ends with {@code Throwable} then this iterator simply ends and original cause can be get
 * using {@link #getThrowable()} method.
 *
 * @param <T> Type of elements
 */
public class ValveIterator<T> implements Iterator<T> {

    private static final Logger LOGGER = Logger.getLogger(ValveIterator.class.getName());

    private final Object lock = new Object();
    private final Valve<T> valve;
    private boolean closed = false;
    private T nextItem;
    private volatile Throwable throwable;

    ValveIterator(Valve<T> valve) {
        this.valve = valve;
        valve.handle((t, p) -> {
            synchronized (lock) {
                nextItem = t;
                p.pause();
                lock.notifyAll();
            }
        }, throwable -> {
            this.throwable = throwable;
            close();
        }, this::close);
    }

    private void close() {
        synchronized (lock) {
            closed = true;
            lock.notifyAll();
        }
    }

    @Override
    public boolean hasNext() {
        synchronized (lock) {
            if (nextItem != null) {
                return true;
            }
            if (closed) {
                return false;
            }
            while (true) {
                valve.resume();
                if (nextItem != null) {
                    return true;
                }
                if (closed) {
                    return false;
                }
                try {
                    lock.wait();
                    if (nextItem != null) {
                        return true;
                    }
                    if (closed) {
                        return false;
                    }
                } catch (InterruptedException e) {
                    this.closed = true;
                    this.throwable = e;
                    return false;
                }
            }
        }
    }

    @Override
    public T next() {
        synchronized (lock) {
            if (hasNext()) {
                T result = nextItem;
                nextItem = null;
                return result;
            } else {
                throw new NoSuchElementException("No more elements. Original Valve is closed!");
            }
        }
    }

    /**
     * If original {@link Valve} ends with error then this method returns cause of such error.
     *
     * @return the cause of {@code Valve} error or {@code null}
     */
    public Throwable getThrowable() {
        return throwable;
    }
}
