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

package io.helidon.common.concurrency.limits;

import java.io.Serial;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.helidon.common.config.ConfigException;

class AimdLimitImpl {
    private final double backoffRatio;
    private final long timeoutInNanos;
    private final int minLimit;
    private final int maxLimit;

    private final Supplier<Long> clock;
    private final AtomicInteger concurrentRequests;
    private final AdjustableSemaphore semaphore;
    private final LimitHandlers.LimiterHandler handler;

    private final AtomicInteger limit;
    private final Lock limitLock = new ReentrantLock();

    AimdLimitImpl(AimdLimitConfig config) {
        int initialLimit = config.initialLimit();
        this.backoffRatio = config.backoffRatio();
        this.timeoutInNanos = config.timeout().toNanos();
        this.minLimit = config.minLimit();
        this.maxLimit = config.maxLimit();
        this.clock = config.clock().orElseGet(() -> System::nanoTime);

        this.concurrentRequests = new AtomicInteger();
        this.limit = new AtomicInteger(initialLimit);

        this.semaphore = new AdjustableSemaphore(initialLimit, config.fair());
        if (config.queueLength() == 0) {
            this.handler = new LimitHandlers.RealSemaphoreHandler(semaphore,
                                                                  () -> new AimdToken(clock, concurrentRequests));
        } else {
            this.handler = new LimitHandlers.QueuedSemaphoreHandler(semaphore,
                                                                    config.queueLength(),
                                                                    config.queueTimeout(),
                                                                    () -> new AimdToken(clock, concurrentRequests));
        }

        if (!(backoffRatio < 1.0 && backoffRatio >= 0.5)) {
            throw new ConfigException("Backoff ratio must be within [0.5, 1.0)");
        }
        if (maxLimit < minLimit) {
            throw new ConfigException("Max limit must be higher than min limit, or equal to it");
        }
        if (initialLimit > maxLimit) {
            throw new ConfigException("Initial limit must be lower than max limit, or equal to it");
        }
        if (initialLimit < minLimit) {
            throw new ConfigException("Initial limit must be higher than minimum limit, or equal to it");
        }
    }

    Semaphore semaphore() {
        return semaphore;
    }

    int currentLimit() {
        return limit.get();
    }

    Optional<Limit.Token> tryAcquire() {
        return handler.tryAcquire();
    }

    void invoke(Runnable runnable) throws Exception {
        invoke(() -> {
            runnable.run();
            return null;
        });
    }

    <T> T invoke(Callable<T> callable) throws Exception {
        Optional<LimitAlgorithm.Token> optionalToken = handler.tryAcquire();
        if (optionalToken.isPresent()) {
            LimitAlgorithm.Token token = optionalToken.get();
            try {
                T response = callable.call();
                token.success();
                return response;
            } catch (IgnoreTaskException e) {
                token.dropped();
                return e.handle();
            } catch (Throwable e) {
                token.ignore();
                throw e;
            }
        } else {
            throw new LimitException("No more permits available for the semaphore");
        }
    }

    void updateWithSample(long startTime, long endTime, int currentRequests, boolean success) {
        long rtt = endTime - startTime;

        int currentLimit = limit.get();
        if (rtt > timeoutInNanos || !success) {
            currentLimit = (int) (currentLimit * backoffRatio);
        } else if (currentRequests * 2 >= currentLimit) {
            currentLimit = currentLimit + 1;
        }
        setLimit(Math.min(maxLimit, Math.max(minLimit, currentLimit)));
    }

    private void setLimit(int newLimit) {
        if (newLimit == limit.get()) {
            // already have the correct limit
            return;
        }
        // now we lock, to do this only once in parallel,
        // as otherwise we may end up in strange lands
        limitLock.lock();
        try {
            int oldLimit = limit.get();
            if (oldLimit == newLimit) {
                // parallel thread already fixed it
                return;
            }
            limit.set(newLimit);

            if (newLimit > oldLimit) {
                this.semaphore.release(newLimit - oldLimit);
            } else {
                this.semaphore.reducePermits(oldLimit - newLimit);
            }
        } finally {
            limitLock.unlock();
        }
    }

    private static final class AdjustableSemaphore extends Semaphore {
        @Serial
        private static final long serialVersionUID = 114L;

        private AdjustableSemaphore(int permits, boolean fair) {
            super(permits, fair);
        }

        @Override
        protected void reducePermits(int reduction) {
            super.reducePermits(reduction);
        }
    }

    private class AimdToken implements Limit.Token {
        private final long startTime;
        private final int currentRequests;

        private AimdToken(Supplier<Long> clock, AtomicInteger concurrentRequests) {
            startTime = clock.get();
            currentRequests = concurrentRequests.incrementAndGet();
        }

        @Override
        public void dropped() {
            try {
                updateWithSample(startTime, clock.get(), currentRequests, false);
            } finally {
                AimdLimitImpl.this.semaphore.release();
            }
        }

        @Override
        public void ignore() {
            concurrentRequests.decrementAndGet();
            AimdLimitImpl.this.semaphore.release();
        }

        @Override
        public void success() {
            try {
                updateWithSample(startTime, clock.get(), currentRequests, true);
                concurrentRequests.decrementAndGet();
            } finally {
                AimdLimitImpl.this.semaphore.release();
            }
        }
    }
}
