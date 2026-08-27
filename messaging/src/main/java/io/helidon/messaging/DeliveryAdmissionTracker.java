/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.messaging;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tracks admission attempts that cross independent channel dispatcher locks.
 */
final class DeliveryAdmissionTracker {
    private final ReentrantLock stabilityLock = new ReentrantLock();
    private final Condition stabilityChanged = stabilityLock.newCondition();
    private final AtomicBoolean finalized = new AtomicBoolean();
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicInteger stabilityWaiters = new AtomicInteger();
    private final AtomicLong generation = new AtomicLong();
    private boolean forced;

    boolean enter() {
        if (finalized.get()) {
            return false;
        }
        attempts.incrementAndGet();
        // Pair the increment with a second gate check so drain cannot finalize between the first check and entry.
        if (!finalized.get()) {
            return true;
        }
        exit();
        return false;
    }

    void exit() {
        if (attempts.decrementAndGet() != 0 || stabilityWaiters.get() == 0) {
            return;
        }
        stabilityLock.lock();
        try {
            if (stabilityWaiters.get() != 0) {
                stabilityChanged.signalAll();
            }
        } finally {
            stabilityLock.unlock();
        }
    }

    void advance() {
        generation.incrementAndGet();
    }

    long generation() {
        return generation.get();
    }

    Stability awaitStable(long observedGeneration, long deadline) {
        try {
            stabilityLock.lockInterruptibly();
            try {
                stabilityWaiters.incrementAndGet();
                try {
                    while (attempts.get() != 0) {
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0) {
                            return Stability.TIMED_OUT;
                        }
                        stabilityChanged.awaitNanos(remaining);
                    }
                    if (observedGeneration != generation.get()) {
                        return Stability.CHANGED;
                    }
                    finalized.set(true);
                    // An attempt may have passed its first gate check immediately before finalization. Recheck both
                    // counters with the gate closed; such an attempt will either be visible or reject itself.
                    if (attempts.get() == 0 && observedGeneration == generation.get()) {
                        return Stability.STABLE;
                    }
                    if (!forced) {
                        finalized.set(false);
                    }
                    return Stability.CHANGED;
                } finally {
                    stabilityWaiters.decrementAndGet();
                }
            } finally {
                stabilityLock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Stability.TIMED_OUT;
        }
    }

    void forceFinalize() {
        stabilityLock.lock();
        try {
            forced = true;
            finalized.set(true);
        } finally {
            stabilityLock.unlock();
        }
    }

    enum Stability {
        STABLE,
        CHANGED,
        TIMED_OUT
    }
}
