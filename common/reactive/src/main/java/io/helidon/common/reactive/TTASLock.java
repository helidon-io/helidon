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

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;

/**
 * TTAS Lock with exponential backoff.
 */
class TTASLock implements Lock {


    private final SplittableRandom random = new SplittableRandom();
    private final AtomicBoolean locked = new AtomicBoolean(false);
    private final long min;
    private final long max;

    /**
     * Create new TTAS Lock with exponential backoff.
     *
     * @param min minimal spinlock park time in nanos
     * @param max maximum spinlock park time in nanos
     *            to reach by exponentially rising backoff time
     */
    TTASLock(final long min, final long max) {
        if (min < 2) {
            throw new IllegalArgumentException("Minimal park time should be higher than 1");
        }
        this.min = min;
        this.max = max;
    }

    /**
     * Create new TTAS Lock with exponential backoff.
     */
    TTASLock() {
        this(20L, 2000L);
    }

    public void lock() {
        long backOff = -1;
        for (;;) {
            if (!locked.get()) {
                if (locked.compareAndSet(false, true)) {
                    return;
                }
            }
            if (backOff == -1) {
                backOff = random.nextLong(min, min * 2);
            }
            backOff = backOff < max ? backOff * 2 : backOff;
            LockSupport.parkNanos(this, backOff);
        }
    }

    public void unlock() {
        locked.set(false);
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryLock() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryLock(final long time, final TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }
}
