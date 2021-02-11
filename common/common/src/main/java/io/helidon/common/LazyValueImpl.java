/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.common;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

class LazyValueImpl<T> implements LazyValue<T> {

    /**
     * VarHandle's for atomic access to {@code theLock} and {@code loaded}
     * instance variables.
     */
    private static final VarHandle THE_LOCK;
    private static final VarHandle LOADED;
    static {
        try {
            THE_LOCK = MethodHandles.lookup().findVarHandle(LazyValueImpl.class, "theLock", Semaphore.class);
            LOADED = MethodHandles.lookup().findVarHandle(LazyValueImpl.class, "loaded", int.class);
        } catch (Exception e) {
            throw new Error("Unable to obtain VarHandle's", e);
        }
    }

    /**
     * Cached value returned by supplier or passed directly to constructor.
     */
    private T value;

    /**
     * Wrapped delegate or {@code null} if using direct value instead.
     */
    private Supplier<T> delegate;

    /**
     * Semaphore to prevent concurrent update of internal state. Updated
     * only via {@code THE_LOCK}.
     */
    private volatile Semaphore theLock;

    /**
     * Boolean indicating value if readily available without calling
     * a supplier.
     */
    private volatile int loaded;

    private static final int DONE = -1;
    private static final int INIT = 0;
    private static final int WORKING = INIT + 1;

    LazyValueImpl(T value) {
        this.value = value;
        this.loaded = DONE;
    }

    LazyValueImpl(Supplier<T> supplier) {
        this.delegate = supplier;
    }

    @Override
    public boolean isLoaded() {
        return loaded == DONE;
    }

    /**
     * Ensure only a single thread calls the delegate if the value is not yet loaded.
     * Note that {@code loadedCopy} and {@code theLockCopy} represent thread copies
     * of the corresponding volatile variables, while {@code LOADED} and {@code THE_LOCK}
     * are var references to those volatile variables.
     *
     * @return the value
     */
    @Override
    public T get() {
        int loadedCopy = loaded;
        if (loadedCopy == DONE) {
            return value;
        }

        Semaphore theLockCopy = theLock;

        // Race winner that sets 'loaded' to WORKING skips this loop, losers enter it
        while (loadedCopy != DONE && !LOADED.compareAndSet(this, INIT, WORKING)) {
            // One of the losers initializes 'theLock'
            if (theLockCopy == null) {
                THE_LOCK.compareAndSet(this, null, new Semaphore(0));
                theLockCopy = theLock;
            }

            loadedCopy = loaded;
            if (loadedCopy == WORKING) {
                theLockCopy.acquireUninterruptibly();
                loadedCopy = loaded;
            }
        }

        try {
            if (loadedCopy == DONE) {
                return value;
            }
            loadedCopy = INIT;
            value = delegate.get();
            delegate = null;
            loadedCopy = DONE;
            loaded = DONE;
        } finally {
            // If condition holds, delegate threw exception
            if (loadedCopy == INIT) {
                loaded = INIT;
            }
            // Assert: if theLock is null, the successful compare-and-set of THE_LOCK is
            // in the future; but after such compare-and-set there will be a check of
            // loaded as not WORKING, resulting in no attempt to acquire the semaphore
            theLockCopy = theLock;
            if (theLockCopy != null) {
                theLockCopy.release();
            }
        }

        return value;
    }
}
