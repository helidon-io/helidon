/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
import java.util.function.Supplier;

import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;

import static io.helidon.common.concurrency.limits.LimitHandlers.LimiterHandler;
import static io.helidon.metrics.api.Meter.Scope.VENDOR;

/**
 * Base class for semaphore-based limits like FixedLimit and ThroughputLimit.
 */
@SuppressWarnings("removal")
abstract class SemaphoreLimitBase extends LimitAlgorithmDeprecatedBase implements Limit, SemaphoreLimit {

    static final int DEFAULT_QUEUE_LENGTH = 0;

    private LimiterHandler handler;
    private int initialPermits;
    private Semaphore semaphore;
    private final AtomicInteger concurrentRequests;
    private final AtomicInteger rejectedRequests;
    private final Supplier<Long> clock;
    private final boolean enableMetrics;
    private final String name;
    private int queueLength;

    private Timer rttTimer;
    private Timer queueWaitTimer;

    private String originName;

    /**
     * Constructor initializing common fields.
     */
    protected SemaphoreLimitBase(Optional<Supplier<Long>> configuredClock, boolean enableMetrics, String name) {
        this.concurrentRequests = new AtomicInteger();
        this.rejectedRequests = new AtomicInteger();
        this.clock = configuredClock.orElseGet(() -> System::nanoTime);
        this.enableMetrics = enableMetrics;
        this.name = name;
    }

    @Override
    public abstract String type();

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
    public void init(String socketName) {
        originName = socketName;
        if (enableMetrics) {
            MetricsFactory metricsFactory = MetricsFactory.getInstance();
            MeterRegistry meterRegistry = Metrics.globalRegistry();

            Tag socketNameTag = null;
            if (!socketName.equals("@default")) {
                socketNameTag = Tag.create("socketName", socketName);
            }

            if (semaphore != null) {
                Gauge.Builder<Integer> queueLengthBuilder = metricsFactory.gaugeBuilder(
                        name + "_queue_length", semaphore::getQueueLength).scope(VENDOR);
                if (socketNameTag != null) {
                    queueLengthBuilder.tags(List.of(socketNameTag));
                }
                meterRegistry.getOrCreate(queueLengthBuilder);
            }

            Gauge.Builder<Integer> concurrentRequestsBuilder = metricsFactory.gaugeBuilder(
                    name + "_concurrent_requests", concurrentRequests::get).scope(VENDOR);
            if (socketNameTag != null) {
                concurrentRequestsBuilder.tags(List.of(socketNameTag));
            }
            meterRegistry.getOrCreate(concurrentRequestsBuilder);

            Gauge.Builder<Integer> rejectedRequestsBuilder = metricsFactory.gaugeBuilder(
                    name + "_rejected_requests", rejectedRequests::get).scope(VENDOR);
            if (socketNameTag != null) {
                rejectedRequestsBuilder.tags(List.of(socketNameTag));
            }
            meterRegistry.getOrCreate(rejectedRequestsBuilder);

            Timer.Builder rttTimerBuilder = metricsFactory.timerBuilder(name + "_rtt")
                    .scope(VENDOR)
                    .baseUnit(Timer.BaseUnits.MILLISECONDS);
            if (socketNameTag != null) {
                rttTimerBuilder.tags(List.of(socketNameTag));
            }
            rttTimer = meterRegistry.getOrCreate(rttTimerBuilder);

            Timer.Builder waitTimerBuilder = metricsFactory.timerBuilder(name + "_queue_wait_time")
                    .scope(VENDOR)
                    .baseUnit(Timer.BaseUnits.MILLISECONDS);
            if (socketNameTag != null) {
                waitTimerBuilder.tags(List.of(socketNameTag));
            }
            queueWaitTimer = meterRegistry.getOrCreate(waitTimerBuilder);
        }
    }

    /**
     * Updates the round-trip time (RTT) metric with the elapsed time between the specified start and end times.
     * <p>
     * The RTT is calculated as the difference between the end time and the start time. If the timer is not null,
     * the RTT is recorded using the {@link Timer#record(long, TimeUnit)} method with the {@link TimeUnit#NANOSECONDS} unit.
     *
     * @param startTime the start time of the operation in nanoseconds
     * @param endTime the end time of the operation in nanoseconds
     */
    protected void updateMetrics(long startTime, long endTime) {
        long rtt = endTime - startTime;
        if (rttTimer != null) {
            rttTimer.record(rtt, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Sets the {@link LimiterHandler} instance to be used by this semaphore-based limit.
     * <p>
     * The {@code LimiterHandler} is responsible for managing the underlying semaphore and providing
     * a way to acquire tokens.
     *
     * @param handler the {@link LimiterHandler} instance to be used
     */
    protected void setHandler(LimiterHandler handler) {
        this.handler = handler;
    }

    /**
     * Returns the initial number of permits set for this semaphore-based limit.
     * <p>
     * The initial number of permits is used to initialize the underlying semaphore.
     *
     * @return the initial number of permits
     */
    protected int getInitialPermits() {
        return initialPermits;
    }

    /**
     * Sets the initial number of permits for this semaphore-based limit.
     * <p>
     * The initial number of permits is used to initialize the underlying semaphore.
     *
     * @param initialPermits the initial number of permits to be set
     */
    protected void setInitialPermits(int initialPermits) {
        this.initialPermits = initialPermits;
    }

    /**
     * Returns the underlying semaphore instance associated with this semaphore-based limit.
     * <p>
     * Note that direct access to the semaphore may bypass the limit calculation and is not recommended.
     * This method is provided for backward compatibility only and is deprecated for removal.
     *
     * @return the underlying semaphore instance
     */
    protected Semaphore getSemaphore() {
        return semaphore;
    }

    /**
     * Sets the underlying semaphore instance associated with this semaphore-based limit.
     * <p>
     * The semaphore is used to manage the concurrency limit. It is recommended to use the
     * {@link LimiterHandler} instance to acquire tokens instead of directly accessing the semaphore.
     *
     * @param semaphore the semaphore instance to be set
     */
    protected void setSemaphore(Semaphore semaphore) {
        this.semaphore = semaphore;
    }

    /**
     * Returns the {@link AtomicInteger} instance tracking the current number of concurrent requests.
     * <p>
     * The returned {@code AtomicInteger} is used to maintain a count of the concurrent requests being processed.
     *
     * @return the {@link AtomicInteger} instance tracking concurrent requests
     */
    protected AtomicInteger getConcurrentRequests() {
        return concurrentRequests;
    }

    /**
     * Returns the clock supplier used by this semaphore-based limit.
     * <p>
     * The clock supplier provides a way to obtain the current time in nanoseconds.
     *
     * @return the clock supplier
     */
    protected Supplier<Long> getClock() {
        return clock;
    }

    /**
     * Returns the name associated with this semaphore-based limit.
     * <p>
     * The name is used to identify the limit and is typically used for metrics and monitoring purposes.
     *
     * @return the name of this semaphore-based limit
     */
    protected String getName() {
        return name;
    }

    /**
     * Returns the current queue length associated with this semaphore-based limit.
     * <p>
     * The queue length represents the maximum number of requests that can be queued
     * while waiting for a permit to be available.
     *
     * @return the current queue length
     */
    protected int getQueueLength() {
        return queueLength;
    }

    /**
     * Sets the maximum number of requests that can be queued while waiting for a permit to be available.
     * <p>
     * The queue length determines the number of requests that can be buffered when the concurrency limit is reached.
     * If the queue is full, subsequent requests will be rejected.
     *
     * @param queueLength the maximum number of requests to be queued
     */
    protected void setQueueLength(int queueLength) {
        this.queueLength = queueLength;
    }

    // Deprecated methods
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
            return Outcome.immediateAcceptance(originName, type(), token.get());

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
                        type(),
                        token.get(),
                        startWait,
                        endWait);
            }
            return Outcome.deferredRejection(originName, type(), startWait, endWait);
        }
        rejectedRequests.getAndIncrement();
        return Outcome.immediateRejection(originName, type());
    }

    private <T> Result<T> doInvoke(Callable<T> callable) throws Exception {

        Outcome outcome = doTryAcquire(true);
        if (!(outcome instanceof Outcome.Accepted accepted)) {
            throw new LimitException("No more permits available for the semaphore");
        }
        LimitAlgorithm.Token token = accepted.token();
        try {
            T response = callable.call();
            token.success();
            return Result.create(response, outcome);
        } catch (IgnoreTaskException e) {
            token.ignore();
            return Result.create(e.handle(), outcome);
        } catch (Throwable ex) {
            token.dropped();
            throw ex;
        }
    }
}
