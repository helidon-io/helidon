/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.microprofile.testing;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Test synchronizer.
 * A concurrent utility to help enforce sequentiality.
 */
public class HelidonTestSynchronizer {

    private static final int CLASS_MASK = 1 << 31;
    private static final int CONTAINER_MASK = 1 << 30;
    private static final int METHOD_MASK = 1;

    private final Sync sync = new Sync();

    private final Semaphore classSemaphore = new Semaphore(1);
    private final Semaphore containerSemaphore = new Semaphore(1);
    private final Lock methodsLock = new ReentrantReadWriteLock().readLock();
    private final Map<Object, CompletableFuture<Void>> methodsFutures = new ConcurrentHashMap<>();

    /**
     * Acquire the "class" permit.
     */
    public void acquireClass() {
        try {
            classSemaphore.acquire();
            //            sync.acquireSharedInterruptibly(CLASS_MASK);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Release the "class" permit.
     */
    public void releaseClass() {
        classSemaphore.release();
        //        sync.releaseShared(CLASS_MASK);
    }

    /**
     * Acquire the "container" permit.
     */
    public void acquireContainer() {
        try {
            containerSemaphore.acquire();
            //            sync.acquireSharedInterruptibly(CONTAINER_MASK);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Release the "container" permit.
     */
    public void releaseContainer() {
        containerSemaphore.release();
        //        sync.releaseShared(CONTAINER_MASK);
    }

    /**
     * Wait the "methods" is zero.
     */
    public void awaitMethods() {
        try {
            CompletableFuture.allOf(methodsFutures.values().toArray(new CompletableFuture[0])).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            methodsFutures.clear();
        }
    }

    /**
     * Increment the "methods" counter.
     */
    public void startMethod(Object key) {
        methodsFutures.put(key, new CompletableFuture<>());
        //        sync.release(1);
    }

    /**
     * Decrement the "methods" counter.
     */
    public void completeMethod(Object key) {
        CompletableFuture<Void> future = methodsFutures.get(key);
        if (future != null) {
            future.complete(null);
        }
        //        if (methodsCount.decrementAndGet() == 0) {
        //            methodsLock.unlock();
        //        }
        //        sync.release(-1);
    }

    private static final class Sync extends AbstractQueuedSynchronizer {

        @Override
        protected int tryAcquireShared(int acquires) {
            return (getState() & acquires) == 0 ? 0 : -1;
        }

        @Override
        protected boolean tryReleaseShared(int releases) {
            for (; ; ) {
                int c = getState();
                switch (releases) {
                    case CLASS_MASK -> {
                        if ((c & CLASS_MASK) == CLASS_MASK) {
                            return compareAndSetState(c, c | (c ^ CLASS_MASK));
                        }
                        return false;
                    }
                    case CONTAINER_MASK -> {
                        if ((c & CONTAINER_MASK) == CONTAINER_MASK) {
                            return compareAndSetState(c, c | (c ^ CONTAINER_MASK));
                        }
                        return false;
                    }
                    default -> {
                        if (c == 0) {
                            return false;
                        }
                        int nextc = c + 1;
                        if (compareAndSetState(c, nextc)) {
                            return nextc == 0;
                        }
                    }
                }
            }
        }
    }
}
