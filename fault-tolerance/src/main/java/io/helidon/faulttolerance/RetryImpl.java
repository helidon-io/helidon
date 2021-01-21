/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

class RetryImpl implements Retry {
    private final LazyValue<? extends ScheduledExecutorService> scheduledExecutor;
    private final ErrorChecker errorChecker;
    private final long maxTimeNanos;
    private final Retry.RetryPolicy retryPolicy;
    private final AtomicLong retryCounter = new AtomicLong(0L);
    private final String name;

    RetryImpl(Retry.Builder builder) {
        this.scheduledExecutor = builder.scheduledExecutor();
        this.errorChecker = ErrorChecker.create(builder.skipOn(), builder.applyOn());
        this.maxTimeNanos = builder.overallTimeout().toNanos();
        this.retryPolicy = builder.retryPolicy();
        this.name = builder.name();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public <T> Multi<T> invokeMulti(Supplier<? extends Flow.Publisher<T>> supplier) {
        return retryMulti(new RetryContext<>(supplier));
    }

    @Override
    public <T> Single<T> invoke(Supplier<? extends CompletionStage<T>> supplier) {
        return retrySingle(new RetryContext<>(supplier));
    }

    private <T> Single<T> retrySingle(RetryContext<? extends CompletionStage<T>> context) {
        long delay = 0;
        int currentCallIndex = context.count.getAndIncrement();
        if (currentCallIndex != 0) {
            Optional<Long> maybeDelay = retryPolicy.nextDelayMillis(context.startedMillis,
                                                                    context.lastDelay.get(),
                                                                    currentCallIndex);
            if (maybeDelay.isEmpty()) {
                return Single.error(context.throwable());
            }
            delay = maybeDelay.get();
        }

        long nanos = System.nanoTime() - context.startedNanos;
        if (nanos > maxTimeNanos) {
            return Single.error(new RetryTimeoutException(context.throwable(),
                    "Execution took too long. Already executing: "
                    + TimeUnit.NANOSECONDS.toMillis(nanos) + " ms, must timeout after: "
                    + TimeUnit.NANOSECONDS.toMillis(maxTimeNanos) + " ms."));
        }

        if (currentCallIndex > 0) {
            retryCounter.getAndIncrement();
        }

        DelayedTask<Single<T>> task = DelayedTask.createSingle(context.supplier);
        if (delay == 0) {
            task.execute();
        } else {
            scheduledExecutor.get().schedule(task::execute, delay, TimeUnit.MILLISECONDS);
        }

        return task.result()
                .onErrorResumeWithSingle(throwable -> {
                    Throwable cause = FaultTolerance.cause(throwable);
                    context.thrown.add(cause);
                    if (errorChecker.shouldSkip(cause)) {
                        return Single.error(context.throwable());
                    }
                    return retrySingle(context);
                });
    }

    private <T> Multi<T> retryMulti(RetryContext<? extends Flow.Publisher<T>> context) {
        long delay = 0;
        int currentCallIndex = context.count.getAndIncrement();
        if (currentCallIndex != 0) {
            Optional<Long> maybeDelay = retryPolicy.nextDelayMillis(context.startedMillis,
                                                                    context.lastDelay.get(),
                                                                    currentCallIndex);
            if (maybeDelay.isEmpty()) {
                return Multi.error(context.throwable());
            }
            delay = maybeDelay.get();
        }

        long nanos = System.nanoTime() - context.startedNanos;
        if (nanos > maxTimeNanos) {
            return Multi.error(new RetryTimeoutException(context.throwable(),
                    "Execution took too long. Already executing: "
                    + TimeUnit.NANOSECONDS.toMillis(nanos) + " ms, must timeout after: "
                    + TimeUnit.NANOSECONDS.toMillis(maxTimeNanos) + " ms."));
        }

        if (currentCallIndex > 0) {
            retryCounter.getAndIncrement();
        }

        DelayedTask<Multi<T>> task = DelayedTask.createMulti(context.supplier);
        if (delay == 0) {
            task.execute();
        } else {
            scheduledExecutor.get().schedule(task::execute, delay, TimeUnit.MILLISECONDS);
        }

        return task.result()
                .onErrorResumeWith(throwable -> {
                    Throwable cause = FaultTolerance.cause(throwable);
                    context.thrown.add(cause);
                    if (task.hadData() || errorChecker.shouldSkip(cause)) {
                        return Multi.error(context.throwable());
                    }
                    return retryMulti(context);
                });
    }

    @Override
    public long retryCounter() {
        return retryCounter.get();
    }

    private static class RetryContext<U> {
        // retry runtime
        private final long startedMillis = System.currentTimeMillis();
        private final long startedNanos = System.nanoTime();
        private final AtomicInteger count = new AtomicInteger();
        private final List<Throwable> thrown = new LinkedList<>();
        private final AtomicLong lastDelay = new AtomicLong();

        private final Supplier<U> supplier;

        RetryContext(Supplier<U> supplier) {
            this.supplier = supplier;
        }

        Throwable throwable() {
            if (thrown.isEmpty()) {
                return new IllegalStateException("Exception list is empty");
            }
            Throwable last = thrown.get(thrown.size() - 1);
            for (int i = 0; i < thrown.size() - 1; i++) {
                Throwable throwable = thrown.get(i);
                if (throwable != last) {
                    last.addSuppressed(throwable);
                }
            }
            return last;
        }
    }
}
