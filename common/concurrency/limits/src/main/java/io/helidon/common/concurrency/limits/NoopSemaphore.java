/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.common.concurrency.limits;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A semaphore that does nothing.
 * Use {@link #INSTANCE} to get an instance of this semaphore.
 *
 * @deprecated this is only provided for backward compatibility and will be removed, use
 *         {@link BasicLimit#create()} to get unlimited limit
 */
@Deprecated(forRemoval = true, since = "4.2.0")
public class NoopSemaphore extends Semaphore {
    /**
     * Singleton instance to be used whenever needed.
     */
    public static final Semaphore INSTANCE = new NoopSemaphore();

    private NoopSemaphore() {
        super(0);
    }

    @Override
    public void acquire() throws InterruptedException {
        // do nothing
    }

    @Override
    public void acquireUninterruptibly() {
        // do nothing
    }

    @Override
    public boolean tryAcquire() {
        return true;
    }

    @Override
    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        return true;
    }

    @Override
    public void release() {
        // do nothing
    }

    @Override
    public void acquire(int permits) throws InterruptedException {
        // do nothing
    }

    @Override
    public void acquireUninterruptibly(int permits) {
        // do nothing
    }

    @Override
    public boolean tryAcquire(int permits) {
        return true;
    }

    @Override
    public boolean tryAcquire(int permits, long timeout, TimeUnit unit) throws InterruptedException {
        return true;
    }

    @Override
    public void release(int permits) {
        // do nothing
    }

    @Override
    public int availablePermits() {
        return 1000;
    }

    @Override
    public int drainPermits() {
        return 0;
    }

    @Override
    protected void reducePermits(int reduction) {
        // do nothing
    }

    @Override
    public boolean isFair() {
        return true;
    }

    @Override
    protected Collection<Thread> getQueuedThreads() {
        return Set.of();
    }

    @Override
    public String toString() {
        return "No-op semaphore";
    }
}
