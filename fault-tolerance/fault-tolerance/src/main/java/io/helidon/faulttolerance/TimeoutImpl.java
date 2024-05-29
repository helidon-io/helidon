/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

class TimeoutImpl implements Timeout {
    private static final System.Logger LOGGER = System.getLogger(TimeoutImpl.class.getName());

    private final long timeoutMillis;
    private final ExecutorService executor;
    private final boolean currentThread;
    private final String name;
    private final TimeoutConfig config;

    TimeoutImpl(TimeoutConfig config) {
        this.timeoutMillis = config.timeout().toMillis();
        this.executor = config.executor().orElseGet(FaultTolerance.executor());
        this.currentThread = config.currentThread();
        this.name = config.name().orElseGet(() -> "timeout-" + System.identityHashCode(config));
        this.config = config;
    }

    @Override
    public TimeoutConfig prototype() {
        return config;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public <T> T invoke(Supplier<? extends T> supplier) {
        if (!currentThread) {
            try {
                return CompletableFuture.supplyAsync(supplier, executor)
                        .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                        .get();
            } catch (Throwable t) {
                throw mapThrowable(t, null);
            }
        } else {
            Thread thisThread = Thread.currentThread();
            ReentrantLock interruptLock = new ReentrantLock();
            AtomicBoolean callReturned = new AtomicBoolean(false);
            AtomicBoolean interrupted = new AtomicBoolean(false);

            executor.submit(FaultTolerance.toDelayedRunnable(() -> {
                interruptLock.lock();
                try {
                    if (callReturned.compareAndSet(false, true)) {
                        thisThread.interrupt();
                        interrupted.set(true);      // needed if InterruptedException caught in supplier
                    }
                } finally {
                    interruptLock.unlock();
                }
            }, timeoutMillis));

            try {
                T result = supplier.get();
                if (interrupted.get()) {
                    throw new TimeoutException("Supplier execution interrupted", null);
                }
                return result;
            } catch (Throwable t) {
                throw mapThrowable(t, interrupted);
            } finally {
                interruptLock.lock();
                try {
                    callReturned.set(true);
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

    private static RuntimeException mapThrowable(Throwable t, AtomicBoolean interrupted) {
        Throwable throwable = SupplierHelper.unwrapThrowable(t);
        if (throwable instanceof InterruptedException) {
            return new TimeoutException("Call interrupted", throwable);

        } else if (throwable instanceof java.util.concurrent.TimeoutException) {
            return new TimeoutException("Timeout reached", throwable.getCause());
        } else if (interrupted != null && interrupted.get()) {
            return new TimeoutException("Supplier execution interrupted", t);
        }
        return SupplierHelper.toRuntimeException(throwable);
    }
}
