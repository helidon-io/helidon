/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome;
import io.helidon.common.concurrency.limits.LimitAlgorithm.Result;
import io.helidon.config.ConfigException;

class AimdLimitImpl extends LimitBase {
    private final double backoffRatio;
    private final long timeoutInNanos;
    private final int minLimit;
    private final int maxLimit;

    private final Supplier<Long> clock;
    private final AtomicInteger concurrentRequests;
    private final AtomicInteger rejectedRequests;
    private final AdjustableSemaphore semaphore;
    private final LimitHandlers.LimiterHandler handler;
    private final AtomicInteger limit;
    private final Lock limitLock = new ReentrantLock();
    private final int queueLength;
    private final AimdMetrics metrics;

    private volatile String originName;

    AimdLimitImpl(AimdLimitConfig config) {
        super("No more permits available for the semaphore");

        int initialLimit = config.initialLimit();
        this.backoffRatio = config.backoffRatio();
        this.timeoutInNanos = config.timeout().toNanos();
        this.minLimit = config.minLimit();
        this.maxLimit = config.maxLimit();
        this.clock = config.clock().orElseGet(() -> System::nanoTime);

        this.concurrentRequests = new AtomicInteger();
        this.rejectedRequests = new AtomicInteger();
        this.limit = new AtomicInteger(initialLimit);

        this.queueLength = config.queueLength();
        this.semaphore = new AdjustableSemaphore(initialLimit, config.fair());
        this.handler = new LimitHandlers.QueuedSemaphoreHandler(semaphore,
                                                                queueLength,
                                                                config.queueTimeout(),
                                                                () -> new AimdToken(clock, concurrentRequests));
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
        this.metrics = new AimdMetrics(config.enableMetrics(),
                                       semaphore,
                                       config.name(),
                                       rejectedRequests,
                                       concurrentRequests,
                                       limit);
    }

    int concurrentRequests() {
        return concurrentRequests.get();
    }

    int currentLimit() {
        return limit.get();
    }

    Outcome tryAcquireOutcome(boolean wait) {
        return doTryAcquireOutcome(wait);
    }

    Outcome run(Runnable runnable) throws Exception {
        return call(() -> {
            runnable.run();
            return null;
        }).outcome();
    }

    <T> Result<T> call(Callable<T> callable) throws Exception {
        return doCall(callable);
    }

    void updateWithSample(long startTime, long endTime, int currentRequests, boolean success) {
        long rtt = endTime - startTime;

        metrics.updateRtt(rtt);

        int currentLimit = limit.get();
        if (rtt > timeoutInNanos || !success) {
            currentLimit = (int) (currentLimit * backoffRatio);
        } else if (currentRequests * 2 >= currentLimit) {
            currentLimit = currentLimit + 1;
        }
        setLimit(Math.min(maxLimit, Math.max(minLimit, currentLimit)));
    }

    @Override
    protected Outcome doTryAcquireOutcome(boolean wait) {
        Optional<LimitAlgorithm.Token> token = handler.tryAcquireToken(false);

        if (token.isPresent()) {
            return Outcome.immediateAcceptance(originName,
                                                        AimdLimit.TYPE,
                                                        token.get());
        }
        Outcome outcome;
        if (wait && queueLength > 0) {
            long startWait = clock.get();
            token = handler.tryAcquireToken(true);
            long endWait = clock.get();
            if (token.isPresent()) {
                outcome = Outcome.deferredAcceptance(originName,
                                                           AimdLimit.TYPE,
                                                           token.get(),
                                                           startWait,
                                                           endWait);
                metrics.updateWaitTime(startWait, endWait);
                return outcome;
            }
            outcome = Outcome.deferredRejection(originName,
                                                      AimdLimit.TYPE,
                                                      startWait,
                                                      endWait);
        } else {
            outcome = Outcome.immediateRejection(originName,
                                                       AimdLimit.TYPE);
        }
        rejectedRequests.getAndIncrement();
        return outcome;
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

    /**
     * Initialize metrics for this limit.
     *
     * @param socketName name of socket for which this limit was created
     */
    void init(String socketName) {
        originName = socketName;
        metrics.init(socketName);
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

    private class AimdToken implements LimitAlgorithm.Token {
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
                // Dropped work is no longer running, so keep the concurrency gauge accurate.
                concurrentRequests.decrementAndGet();
                semaphore.release();
            }
        }

        @Override
        public void ignore() {
            concurrentRequests.decrementAndGet();
            semaphore.release();
        }

        @Override
        public void success() {
            try {
                updateWithSample(startTime, clock.get(), currentRequests, true);
                concurrentRequests.decrementAndGet();

            } finally {
                semaphore.release();
            }
        }
    }
}
