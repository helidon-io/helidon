/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.faulttolerance;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;

class TimeoutImpl implements Timeout {
    private static final System.Logger LOGGER = System.getLogger(TimeoutImpl.class.getName());

    private final long timeoutMillis;
    private final LazyValue<? extends ScheduledExecutorService> executor;
    private final boolean currentThread;
    private final String name;
    private final Duration timeout;

    TimeoutImpl(Builder builder) {
        this.timeout = builder.timeout();
        this.timeoutMillis = builder.timeout().toMillis();
        this.executor = builder.executor();
        this.currentThread = builder.currentThread();
        this.name = builder.name();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public <T> T invoke(Supplier<? extends T> supplier) {
        if (!currentThread) {
            try {
                return CompletableFuture.supplyAsync(supplier, executor.get())
                        .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                        .get();
            } catch (InterruptedException e) {
                throw new TimeoutException("Call interrupted", e);
            } catch (ExecutionException e) {
                throw new TimeoutException("Asynchronous execution error", e.getCause());
            }
        } else {
            Thread thisThread = Thread.currentThread();
            ReentrantLock interruptLock = new ReentrantLock();
            AtomicBoolean callReturned = new AtomicBoolean(false);

            ScheduledFuture<?> timeoutFuture = executor.get().schedule(() -> {
                interruptLock.lock();
                try {
                    if (callReturned.compareAndSet(false, true)) {
                        thisThread.interrupt();
                    }
                } finally {
                    interruptLock.unlock();
                }

            }, timeoutMillis, TimeUnit.MILLISECONDS);

            try {
                return supplier.get();
            } finally {
                interruptLock.lock();
                try {
                    callReturned.set(true);
                    timeoutFuture.cancel(false);
                    // Run invocation in current thread
                    // Clear interrupted flag here -- required for uninterruptible busy loops
                    if (Thread.interrupted()) {
                        LOGGER.log(System.Logger.Level.DEBUG, "Current thread interrupted, clearing status");
                    }
                } finally {
                    interruptLock.unlock();
                }
            }
        }
    }
}
