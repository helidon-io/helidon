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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.reactive.Single;

public class Retry implements Handler {
    private final int calls;
    private final long delayMillis;
    private final long maxTimeNanos;
    private final int jitterMillis;
    private final LazyValue<? extends ScheduledExecutorService> scheduledExecutor;
    private final Random random = new Random();

    protected Retry(Builder builder) {
        this.calls = builder.calls;
        this.delayMillis = builder.delay.toMillis();
        this.maxTimeNanos = builder.maxTime.toNanos();
        long jitter = builder.jitter.toMillis() * 2;
        this.jitterMillis = (jitter > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) jitter;
        this.scheduledExecutor = builder.scheduledExecutor;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <T> Single<T> invoke(Supplier<? extends CompletionStage<T>> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();

        new Retrier<>(future, supplier, this)
                .retry();

        return Single.create(future);
    }

    protected boolean abort(Throwable throwable) {
        return false;
    }

    private long nextDelay() {
        long delay = delayMillis;
        int jitterRandom = random.nextInt(jitterMillis) - jitterMillis;
        delay = delay + jitterRandom;
        delay = Math.max(0, delay);

        return delay;
    }

    private class Retrier<T> {
        private final AtomicInteger count = new AtomicInteger();
        private final AtomicReference<Throwable> lastThrowable = new AtomicReference<>();
        private final Supplier<? extends CompletionStage<T>> supplier;
        private final CompletableFuture<T> future;
        private final Retry retry;
        private final long started = System.nanoTime();

        private Retrier(CompletableFuture<T> future,
                        Supplier<? extends CompletionStage<T>> supplier,
                        Retry retry) {
            this.future = future;
            this.supplier = supplier;
            this.retry = retry;
        }

        private void retry() {
            int currentCount = count.incrementAndGet();

            CompletionStage<T> stage = null;
            try {
                stage = supplier.get();
            } catch (Throwable e) {
                stage = CompletableFuture.failedStage(e);
            }

            stage.handle((it, throwable) -> {
                        if (throwable == null) {
                            future.complete(it);
                        } else {
                            Throwable current = FaultTolerance.getCause(throwable);
                            Throwable before = lastThrowable.get();
                            if (before != null) {
                                current.addSuppressed(before);
                            }

                            long now = System.nanoTime();
                            if (currentCount >= retry.calls || retry.abort(current)) {
                                // this is final execution
                                future.completeExceptionally(current);
                            } else if (now - started > maxTimeNanos) {
                                TimeoutException timedOut = new TimeoutException("Retries timed out");
                                timedOut.addSuppressed(current);
                                future.completeExceptionally(timedOut);
                            } else {
                                lastThrowable.set(current);
                                // schedule next retry
                                scheduledExecutor.get().schedule(this::retry, nextDelay(), TimeUnit.MILLISECONDS);
                            }
                        }
                        return null;
                    });
        }
    }

    private static class RetryWithAbortOn extends Retry {
        private final Set<Class<? extends Throwable>> abortOn;

        private RetryWithAbortOn(Builder builder) {
            super(builder);
            this.abortOn = Set.copyOf(builder.abortOn);
        }

        @Override
        protected boolean abort(Throwable throwable) {
            return abortOn.contains(throwable.getClass());
        }
    }

    private static class RetryWithRetryOn extends Retry {
        private final Set<Class<? extends Throwable>> retryOn;

        private RetryWithRetryOn(Builder builder) {
            super(builder);
            this.retryOn = Set.copyOf(builder.retryOn);
        }

        @Override
        protected boolean abort(Throwable throwable) {
            return !retryOn.contains(throwable.getClass());
        }
    }

    public static class Builder implements io.helidon.common.Builder<Retry> {
        private final Set<Class<? extends Throwable>> retryOn = new HashSet<>();
        private final Set<Class<? extends Throwable>> abortOn = new HashSet<>();

        private int calls = 3;
        private Duration delay = Duration.ofMillis(200);
        private Duration maxTime = Duration.ofSeconds(1);
        private Duration jitter = Duration.ofMillis(50);
        private LazyValue<? extends ScheduledExecutorService> scheduledExecutor = FaultTolerance.scheduledExecutor();

        private Builder() {
        }

        @Override
        public Retry build() {
            calls = Math.max(1, calls);
            if (retryOn.isEmpty()) {
                if (abortOn.isEmpty()) {
                    return new Retry(this);
                } else {
                    return new RetryWithAbortOn(this);
                }
            } else {
                if (abortOn.isEmpty()) {
                    return new RetryWithRetryOn(this);
                } else {
                    throw new IllegalArgumentException("You have defined both retryOn and abortOn exceptions. "
                                                            + "This cannot be correctly handled; abortOn: " + abortOn
                                                            + " retryOn: " + retryOn);
                }
            }
        }

        /**
         * Total number of calls (first + retries).
         * @param calls how many times to call the method
         * @return updated builder instance
         */
        public Builder calls(int calls) {
            this.calls = calls;
            return this;
        }

        public Builder delay(Duration delay) {
            this.delay = delay;
            return this;
        }

        public Builder maxTime(Duration maxTime) {
            this.maxTime = maxTime;
            return this;
        }

        public Builder jitter(Duration jitter) {
            this.jitter = jitter;
            return this;
        }

        public Builder retryOn(Class<? extends Throwable>... classes) {
            retryOn.clear();
            Arrays.stream(classes)
                    .forEach(this::addRetryOn);

            return this;
        }

        public Builder addRetryOn(Class<? extends Throwable> clazz) {
            this.retryOn.add(clazz);
            return this;
        }

        public Builder abortOn(Class<? extends Throwable>... classes) {
            abortOn.clear();
            Arrays.stream(classes)
                    .forEach(this::addAbortOn);

            return this;
        }

        public Builder addAbortOn(Class<? extends Throwable> clazz) {
            this.abortOn.add(clazz);
            return this;
        }

        public Builder scheduledExecutor(ScheduledExecutorService scheduledExecutor) {
            this.scheduledExecutor = LazyValue.create(scheduledExecutor);
            return this;
        }
    }
}
