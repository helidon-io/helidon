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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.config.Config;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;

import static io.helidon.metrics.api.Meter.Scope.VENDOR;

/**
 * Semaphore based limit, that supports queuing for a permit, and timeout on the queue.
 * The default behavior is non-queuing.
 *
 * @see io.helidon.common.concurrency.limits.FixedLimitConfig
 */
@SuppressWarnings("removal")
@RuntimeType.PrototypedBy(FixedLimitConfig.class)
public class FixedLimit extends LimitAlgorithmDeprecatedBase implements Limit, SemaphoreLimit, RuntimeType.Api<FixedLimitConfig> {

    /**
     * Default limit, meaning unlimited execution.
     */
    public static final int DEFAULT_LIMIT = 0;

    /**
     * Default length of the queue.
     */
    public static final int DEFAULT_QUEUE_LENGTH = 0;

    /**
     * Timeout of a request that is enqueued.
     */
    public static final String DEFAULT_QUEUE_TIMEOUT_DURATION = "PT1S";

    static final String TYPE = "fixed";

    private final FixedLimitConfig config;
    private final LimitHandlers.LimiterHandler handler;
    private final int initialPermits;
    private final Semaphore semaphore;
    private final Supplier<Long> clock;
    private final AtomicInteger concurrentRequests;
    private final AtomicInteger rejectedRequests;
    private final int queueLength;

    private Timer rttTimer;
    private Timer queueWaitTimer;

    private String originName;

    private FixedLimit(FixedLimitConfig config) {
        this.config = config;
        this.concurrentRequests = new AtomicInteger();
        this.rejectedRequests = new AtomicInteger();
        this.clock = config.clock().orElseGet(() -> System::nanoTime);

        if (config.permits() == 0 && config.semaphore().isEmpty()) {
            this.semaphore = null;
            this.initialPermits = 0;
            this.queueLength = 0;
            this.handler = new LimitHandlers.NoOpSemaphoreHandler();
        } else {
            this.semaphore = config.semaphore().orElseGet(() -> new Semaphore(config.permits(), config.fair()));
            this.initialPermits = semaphore.availablePermits();
            this.queueLength = Math.max(0, config.queueLength());
            this.handler = new LimitHandlers.QueuedSemaphoreHandler(semaphore,
                                                                    queueLength,
                                                                    config.queueTimeout(),
                                                                    () -> new FixedLimit.FixedToken(clock,
                                                                                                    concurrentRequests));
        }
    }

    /**
     * Create a new fluent API builder to construct {@link FixedLimit}
     * instance.
     *
     * @return fluent API builder
     */
    public static FixedLimitConfig.Builder builder() {
        return FixedLimitConfig.builder();
    }

    /**
     * Create a new instance with all defaults (no limit).
     *
     * @return a new limit instance
     */
    public static FixedLimit create() {
        return builder().build();
    }

    /**
     * Create an instance from the provided semaphore.
     *
     * @param semaphore semaphore to use
     * @return a new fixed limit backed by the provided semaphore
     */
    public static FixedLimit create(Semaphore semaphore) {
        return builder()
                .semaphore(semaphore)
                .build();
    }

    /**
     * Create a new instance from configuration.
     *
     * @param config configuration of the fixed limit
     * @return a new limit instance configured from {@code config}
     */
    public static FixedLimit create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    /**
     * Create a new instance from configuration.
     *
     * @param config configuration of the fixed limit
     * @return a new limit instance configured from {@code config}
     */
    public static FixedLimit create(FixedLimitConfig config) {
        return new FixedLimit(config);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param consumer consumer of configuration builder
     * @return a new limit instance configured from the builder
     */
    public static FixedLimit create(Consumer<FixedLimitConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    @Override
    public Outcome tryAcquireOutcome(boolean wait) {
        return doTryAcquire(wait);
    }

    @Override
    public <T> Result<T> call(Callable<T> callable) throws Exception {
        return doInvoke(callable);
    }

    @Override
    public Outcome run(Runnable runnable) throws Exception {
        return doInvoke(() -> {
            runnable.run();
            return null;
        }).outcome();
    }

    @SuppressWarnings("removal")
    @Override
    public Semaphore semaphore() {
        return handler.semaphore();
    }

    @Override
    public FixedLimitConfig prototype() {
        return config;
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return FixedLimit.TYPE;
    }

    @Override
    public Limit copy() {
        if (config.semaphore().isPresent()) {
            Semaphore semaphore = config.semaphore().get();

            return FixedLimitConfig.builder()
                    .from(config)
                    .semaphore(new Semaphore(initialPermits, semaphore.isFair()))
                    .build();
        }
        return config.build();
    }

    /**
     * Initialize metrics for this limit.
     *
     * @param socketName name of socket for which this limit was created
     */
    @Override
    public void init(String socketName) {
        originName = socketName;
        if (config.enableMetrics()) {
            MetricsFactory metricsFactory = MetricsFactory.getInstance();
            MeterRegistry meterRegistry = Metrics.globalRegistry();

            // define tag if socket is not the default
            Tag socketNameTag = null;
            if (!socketName.equals("@default")) {
                socketNameTag = Tag.create("socketName", socketName);
            }

            if (semaphore != null) {
                // actual number of requests queued
                Gauge.Builder<Integer> queueLengthBuilder = metricsFactory.gaugeBuilder(
                        config.name() + "_queue_length", semaphore::getQueueLength).scope(VENDOR);
                if (socketNameTag != null) {
                    queueLengthBuilder.tags(List.of(socketNameTag));
                }
                meterRegistry.getOrCreate(queueLengthBuilder);
            }

            // count of current requests running
            Gauge.Builder<Integer> concurrentRequestsBuilder = metricsFactory.gaugeBuilder(
                    config.name() + "_concurrent_requests", concurrentRequests::get).scope(VENDOR);
            if (socketNameTag != null) {
                concurrentRequestsBuilder.tags(List.of(socketNameTag));
            }
            meterRegistry.getOrCreate(concurrentRequestsBuilder);

            // actual number of requests queued
            Gauge.Builder<Integer> rejectedRequestsBuilder = metricsFactory.gaugeBuilder(
                    config.name() + "_rejected_requests", rejectedRequests::get).scope(VENDOR);
            if (socketNameTag != null) {
                rejectedRequestsBuilder.tags(List.of(socketNameTag));
            }
            meterRegistry.getOrCreate(rejectedRequestsBuilder);

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

    void updateMetrics(long startTime, long endTime) {
        long rtt = endTime - startTime;
        if (rttTimer != null) {
            rttTimer.record(rtt, TimeUnit.NANOSECONDS);
        }
    }

    // Remove when we retire the obsolete methods on LimitAlgorithm in 5.0.
    @Deprecated(since = "4.3.0", forRemoval = true)
    @Override
    Outcome doTryAcquireObs(boolean wait) {
        return doTryAcquire(wait);
    }

    @Deprecated(since = "4.3.0", forRemoval = true)
    @Override
    <T> Result<T> doInvokeObs(Callable<T> callable) throws Exception {
        return doInvoke(callable);
    }

    private Outcome doTryAcquire(boolean wait) {

        Optional<LimitAlgorithm.Token> token = handler.tryAcquireToken(false);

        if (token.isPresent()) {
            return Outcome.immediateAcceptance(originName, TYPE, token.get());

        }
        if (wait && queueLength > 0) {
            long startWait = clock.get();
            token = handler.tryAcquireToken(true);
            long endWait = clock.get();
            if (token.isPresent()) {
                if (queueWaitTimer != null) {
                    queueWaitTimer.record(endWait - startWait, TimeUnit.NANOSECONDS);
                }
                return Outcome.deferredAcceptance(originName,
                                                  TYPE,
                                                  token.get(),
                                                  startWait,
                                                  endWait);
            }
            return Outcome.deferredRejection(originName, TYPE, startWait, endWait);
        }
        rejectedRequests.getAndIncrement();
        return Outcome.immediateRejection(originName, TYPE);
    }

    private <T> Result<T> doInvoke(Callable<T> callable)
            throws Exception {

        Outcome outcome = doTryAcquire(true);
        if (outcome instanceof Outcome.Accepted accepted) {
            LimitAlgorithm.Token token = accepted.token();
            try {
                concurrentRequests.getAndIncrement();
                long startTime = clock.get();
                T response = callable.call();
                if (rttTimer != null) {
                    rttTimer.record(clock.get() - startTime, TimeUnit.NANOSECONDS);
                }
                token.success();
                return Result.create(response, outcome);
            } catch (IgnoreTaskException e) {
                token.ignore();
                return Result.create(e.handle(), outcome);
            } catch (Throwable e) {
                token.dropped();
                throw e;
            } finally {
                concurrentRequests.getAndDecrement();
            }
        } else {
            throw new LimitException("No more permits available for the semaphore");
        }
    }

    private class FixedToken implements Token {
        private final long startTime;

        private FixedToken(Supplier<Long> clock, AtomicInteger concurrentRequests) {
            startTime = clock.get();
            concurrentRequests.incrementAndGet();
        }

        @Override
        public void dropped() {
            try {
                updateMetrics(startTime, clock.get());
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
                updateMetrics(startTime, clock.get());
                concurrentRequests.decrementAndGet();
            } finally {
                semaphore.release();
            }
        }
    }
}
