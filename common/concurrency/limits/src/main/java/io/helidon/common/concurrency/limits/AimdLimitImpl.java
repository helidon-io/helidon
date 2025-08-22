/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome;
import io.helidon.common.concurrency.limits.LimitAlgorithm.Result;
import io.helidon.common.config.ConfigException;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;

import static io.helidon.metrics.api.Meter.Scope.VENDOR;

class AimdLimitImpl {
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
    private final AimdLimitConfig config;

    private Timer rttTimer;
    private Timer queueWaitTimer;
    private String originName;

    AimdLimitImpl(AimdLimitConfig config) {
        this.config = config;
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
    }

    Semaphore semaphore() {
        return semaphore;
    }

    int currentLimit() {
        return limit.get();
    }

    @Deprecated(since = "4.3.0", forRemoval = true)
    Optional<LimitAlgorithm.Token> tryAcquire(boolean wait) {
        return (doTryAcquire(wait) instanceof Outcome.Accepted accepted)
                ? Optional.of((LimitAlgorithm.Token) accepted)
                : Optional.empty();
    }

    Outcome tryAcquireOutcome(boolean wait) {
        return doTryAcquire(wait);
    }

    Outcome run(Runnable runnable)
            throws Exception {
        return call(() -> {
            runnable.run();
            return null;
        }).outcome();
    }

    @Deprecated(since = "4.3.0", forRemoval = true)
    void invoke(Runnable runnable) throws Exception {
        run(runnable);
    }

    <T> Result<T> call(Callable<T> callable)
            throws Exception {
        return doCall(callable);
    }

    @Deprecated(since = "4.3.0", forRemoval = true)
    <T> T invoke(Callable<T> callable) throws Exception {
        return doCall(callable).result();
    }

    private <T> Result<T> doCall(Callable<T> callable)
            throws Exception {

        Outcome outcome = tryAcquireOutcome(true);
        if (outcome instanceof Outcome.Accepted accepted) {
            LimitAlgorithm.Token token = accepted.token();
            try {
                T response = callable.call();
                token.success();
                return Result.create(response, outcome);
            } catch (IgnoreTaskException e) {
                token.ignore();
                return Result.create(e.handle(), outcome);
            } catch (Throwable e) {
                token.dropped();
                throw e;
            }
        } else {
            throw new LimitException("No more permits available for the semaphore");
        }
    }

    void updateWithSample(long startTime, long endTime, int currentRequests, boolean success) {
        long rtt = endTime - startTime;

        if (rttTimer != null) {
            rttTimer.record(rtt, TimeUnit.NANOSECONDS);
        }

        int currentLimit = limit.get();
        if (rtt > timeoutInNanos || !success) {
            currentLimit = (int) (currentLimit * backoffRatio);
        } else if (currentRequests * 2 >= currentLimit) {
            currentLimit = currentLimit + 1;
        }
        setLimit(Math.min(maxLimit, Math.max(minLimit, currentLimit)));
    }

    private Outcome doTryAcquire(boolean wait) {
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
                if (queueWaitTimer != null) {
                    queueWaitTimer.record(endWait - startWait, TimeUnit.NANOSECONDS);
                }
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
     * @param config this limit's config
     */
    void initMetrics(String socketName, AimdLimitConfig config) {
        originName = socketName;
        if (config.enableMetrics()) {
            MetricsFactory metricsFactory = MetricsFactory.getInstance();
            MeterRegistry meterRegistry = Metrics.globalRegistry();

            // define tag if socket is not the default
            Tag socketNameTag = null;
            if (!socketName.equals("@default")) {
                socketNameTag = Tag.create("socketName", socketName);
            }

            // actual value of limit at this time
            Gauge.Builder<Integer> limitBuilder = metricsFactory.gaugeBuilder(
                    config.name() + "_limit", limit::get).scope(VENDOR);
            if (socketNameTag != null) {
                limitBuilder.tags(List.of(socketNameTag));
            }
            meterRegistry.getOrCreate(limitBuilder);

            // count of current requests running
            Gauge.Builder<Integer> concurrentRequestsBuilder = metricsFactory.gaugeBuilder(
                    config.name() + "_concurrent_requests", concurrentRequests::get).scope(VENDOR);
            if (socketNameTag != null) {
                concurrentRequestsBuilder.tags(List.of(socketNameTag));
            }
            meterRegistry.getOrCreate(concurrentRequestsBuilder);

            // count of number of requests rejected
            Gauge.Builder<Integer> rejectedRequestsBuilder = metricsFactory.gaugeBuilder(
                    config.name() + "_rejected_requests", rejectedRequests::get).scope(VENDOR);
            if (socketNameTag != null) {
                rejectedRequestsBuilder.tags(List.of(socketNameTag));
            }
            meterRegistry.getOrCreate(rejectedRequestsBuilder);

            // actual number of requests queued
            Gauge.Builder<Integer> queueLengthBuilder = metricsFactory.gaugeBuilder(
                    config.name() + "_queue_length", semaphore::getQueueLength).scope(VENDOR);
            if (socketNameTag != null) {
                queueLengthBuilder.tags(List.of(socketNameTag));
            }
            meterRegistry.getOrCreate(queueLengthBuilder);

            // histogram of round-trip times, excluding any time queued
            Timer.Builder rttTimerBuilder = metricsFactory.timerBuilder(config.name() + "_rtt")
                    .scope(VENDOR)
                    .baseUnit(Timer.BaseUnits.MILLISECONDS);
            if (socketNameTag != null) {
                rttTimerBuilder.tags(List.of(socketNameTag));
            }
            rttTimer = meterRegistry.getOrCreate(rttTimerBuilder);

            // histogram of wait times for a permit in queue
            Timer.Builder waitTimerBuilder = metricsFactory.timerBuilder(config.name() + "_queue_wait_time")
                    .scope(VENDOR)
                    .baseUnit(Timer.BaseUnits.MILLISECONDS);
            if (socketNameTag != null) {
                waitTimerBuilder.tags(List.of(socketNameTag));
            }
            queueWaitTimer = meterRegistry.getOrCreate(waitTimerBuilder);
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
