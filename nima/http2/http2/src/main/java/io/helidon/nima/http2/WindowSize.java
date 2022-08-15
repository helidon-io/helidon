/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.http2;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Window size container, used with {@link io.helidon.nima.http2.FlowControl}.
 */
public class WindowSize {
    /**
     * Default window size.
     */
    public static final int DEFAULT_WIN_SIZE = 65_535;
    /**
     * Maximal window size.
     */
    public static final int MAX_WIN_SIZE = Integer.MAX_VALUE;

    private final AtomicInteger remainingWindowSize;

    private final AtomicReference<CompletableFuture<Void>> updated = new AtomicReference<>(new CompletableFuture<>());

    WindowSize(int initialWindowSize) {
        remainingWindowSize = new AtomicInteger(initialWindowSize);
    }

    /**
     * Window size with default initial size.
     */
    public WindowSize() {
        remainingWindowSize = new AtomicInteger(DEFAULT_WIN_SIZE);
    }

    /**
     * Reset window size.
     *
     * @param n window size
     */
    public void resetWindowSize(long n) {
        // When the value of SETTINGS_INITIAL_WINDOW_SIZE changes,
        // a receiver MUST adjust the size of all stream flow-control windows that
        // it maintains by the difference between the new value and the old value
        remainingWindowSize.updateAndGet(o -> (int) n - o);
    }

    /**
     * Increment window size.
     *
     * @param n increment
     * @return whether the increment succeeded
     */
    public boolean incrementWindowSize(int n) {
        int old = remainingWindowSize.getAndUpdate(r -> MAX_WIN_SIZE - r > n ? n + r : MAX_WIN_SIZE);
        triggerUpdate();
        return MAX_WIN_SIZE - old <= n;
    }

    /**
     * Decrement window size.
     *
     * @param decrement decrement
     */
    public void decrementWindowSize(int decrement) {
        remainingWindowSize.updateAndGet(operand -> operand - decrement);
    }

    /**
     * Remaining window size.
     *
     * @return remaining sze
     */
    public int getRemainingWindowSize() {
        return remainingWindowSize.get();
    }

    /**
     * Block until window size update.
     *
     * @return whether update happened before timeout
     */
    public boolean blockTillUpdate() {
        try {
            //TODO configurable timeout
            updated.get().get(10, TimeUnit.SECONDS);
            return false;
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return true;
        }
    }

    /**
     * Trigger update of window size.
     */
    public void triggerUpdate() {
        updated.getAndSet(new CompletableFuture<>()).complete(null);
    }

    @Override
    public String toString() {
        return String.valueOf(remainingWindowSize.get());
    }
}
