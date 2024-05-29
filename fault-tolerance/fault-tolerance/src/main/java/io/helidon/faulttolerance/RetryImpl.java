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

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

class RetryImpl implements Retry {
    private final ErrorChecker errorChecker;
    private final long maxTimeNanos;
    private final RetryPolicy retryPolicy;
    private final RetryConfig retryConfig;
    private final AtomicLong retryCounter = new AtomicLong(0L);
    private final String name;

    RetryImpl(RetryConfig retryConfig) {
        this.name = retryConfig.name().orElseGet(() -> "retry-" + System.identityHashCode(retryConfig));
        this.errorChecker = ErrorChecker.create(retryConfig.skipOn(), retryConfig.applyOn());
        this.maxTimeNanos = retryConfig.overallTimeout().toNanos();
        this.retryPolicy = retryConfig.retryPolicy().orElseThrow();
        this.retryConfig = retryConfig;
    }

    @Override
    public RetryConfig prototype() {
        return retryConfig;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public <T> T invoke(Supplier<? extends T> supplier) {
        RetryContext<? extends T> context = new RetryContext<>();
        while (true) {
            try {
                return supplier.get();
            } catch (Throwable t) {
                Throwable throwable = SupplierHelper.unwrapThrowable(t);
                context.thrown.add(throwable);
                if (errorChecker.shouldSkip(throwable)
                        || throwable instanceof InterruptedException) {     // no retry on interrupt
                    return context.throwIt();
                }
            }
            // now retry
            int currentCallIndex = context.count.incrementAndGet();
            Optional<Long> maybeDelay = computeDelay(context, currentCallIndex);
            if (maybeDelay.isEmpty()) {
                return context.throwIt();
            }

            long delayMillis = maybeDelay.get();

            // check timeout before sleep (if execution took too long)
            long now = System.nanoTime();
            checkTimeout(context, now);
            // check timeout after sleep
            checkTimeout(context, now + TimeUnit.MILLISECONDS.toNanos(delayMillis));

            // now we are retrying for sure
            retryCounter.getAndIncrement();
            // just block current thread, we are expected to run in Virtual threads with Loom
            try {
                Thread.sleep(Duration.ofMillis(delayMillis));
            } catch (InterruptedException e) {
                e.addSuppressed(context.throwable());
                throw new RuntimeException("Retries interrupted", e);
            }
            context.lastDelay.set(delayMillis);
        }
    }

    public void checkTimeout(RetryContext<?> context, long nanoTime) {
        if ((nanoTime - context.startedNanos) > maxTimeNanos) {
            RetryTimeoutException te = new RetryTimeoutException("Execution took too long. Already executing: "
                                                                         + TimeUnit.NANOSECONDS.toMillis(nanoTime)
                                                                         + " ms, must timeout after: "
                                                                         + TimeUnit.NANOSECONDS.toMillis(maxTimeNanos)
                                                                         + " ms.",
                                                                 context.throwable());
            throw te;
        }
    }

    @Override
    public long retryCounter() {
        return retryCounter.get();
    }

    private Optional<Long> computeDelay(RetryContext<?> context, int currentCallIndex) {
        return retryPolicy.nextDelayMillis(context.startedMillis,
                                           context.lastDelay.get(),
                                           currentCallIndex);
    }

    private static class RetryContext<U> {
        // retry runtime
        private final long startedMillis = System.currentTimeMillis();
        private final long startedNanos = System.nanoTime();
        private final AtomicInteger count = new AtomicInteger();
        private final List<Throwable> thrown = new LinkedList<>();
        private final AtomicLong lastDelay = new AtomicLong();

        RetryContext() {
        }

        public U throwIt() {
            Throwable t = throwable();
            if (t instanceof RuntimeException r) {
                throw r;
            }
            if (t instanceof Error e) {
                throw e;
            }
            throw new SupplierException(t);
        }

        boolean hasThrowable() {
            return !thrown.isEmpty();
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

