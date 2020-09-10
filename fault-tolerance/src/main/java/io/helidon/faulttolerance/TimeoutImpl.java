/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

class TimeoutImpl implements Timeout {
    private static final long MONITOR_THREAD_TIMEOUT = 100L;

    private final long timeoutMillis;
    private final LazyValue<? extends ScheduledExecutorService> executor;
    private final boolean currentThread;

    TimeoutImpl(Timeout.Builder builder) {
        this.timeoutMillis = builder.timeout().toMillis();
        this.executor = builder.executor();
        this.currentThread = builder.currentThread();
    }

    @Override
    public <T> Multi<T> invokeMulti(Supplier<? extends Flow.Publisher<T>> supplier) {
        if (currentThread) {
            throw new UnsupportedOperationException("Unsupported currentThread flag with Multi");
        }
        return Multi.create(supplier.get())
                .timeout(timeoutMillis, TimeUnit.MILLISECONDS, executor.get());
    }

    @Override
    public <T> Single<T> invoke(Supplier<? extends CompletionStage<T>> supplier) {
        if (!currentThread) {
            return Single.create(supplier.get(), true)
                    .timeout(timeoutMillis, TimeUnit.MILLISECONDS, executor.get());
        } else {
            Thread thisThread = Thread.currentThread();
            CompletableFuture<Void> monitorStarted = new CompletableFuture<>();
            AtomicBoolean callReturned = new AtomicBoolean(false);

            // Startup monitor thread that can interrupt current thread after timeout
            CompletableFuture<T> future = new CompletableFuture<>();
            Timeout.builder()
                    .executor(executor.get())       // propagate executor
                    .currentThread(false)
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .build()
                    .invoke(() -> {
                        monitorStarted.complete(null);
                        return Single.never();
                    })
                    .exceptionally(it -> {
                        if (callReturned.compareAndSet(false, true)) {
                            future.completeExceptionally(new TimeoutException("Method interrupted by timeout"));
                            thisThread.interrupt();
                        }
                        return null;
                    });

            // Ensure monitor thread has started
            try {
                monitorStarted.get(MONITOR_THREAD_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                return Single.error(new IllegalStateException("Timeout monitor thread failed to start"));
            }

            // Run invocation in current thread
            Single<T> single = Single.create(supplier.get(), true);
            callReturned.set(true);
            single.whenComplete((o, t) -> {
                if (t != null) {
                    future.completeExceptionally(t);
                } else {
                    future.complete(o);
                }
            });

            // Clear interrupted flag here -- required for uninterruptible busy loops
            Thread.interrupted();

            return Single.create(future, true);
        }
    }
}
