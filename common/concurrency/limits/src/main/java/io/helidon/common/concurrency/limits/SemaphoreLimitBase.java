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

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static io.helidon.common.concurrency.limits.LimitHandlers.LimiterHandler;

/**
 * Base class for semaphore-based limits like FixedLimit and ThroughputLimit.
 */
abstract class SemaphoreLimitBase extends LimitBase implements Limit {

    static final int DEFAULT_QUEUE_LENGTH = 0;

    private final int initialPermits;
    private final LimiterHandler handler;
    private final AtomicInteger concurrentRequests;
    private final AtomicInteger rejectedRequests;
    private final Supplier<Long> clock;
    private final SemaphoreMetrics metrics;
    private final int queueLength;

    // set from a thread that may not be the same that uses them, must be volatile
    private volatile String originName;

    /**
     * Constructor initializing common fields.
     */
    protected SemaphoreLimitBase(Context context) {
        super("No more permits available for the semaphore");

        this.initialPermits = context.initialPermits;
        this.handler = context.handler;
        this.queueLength = context.queueLength;
        this.concurrentRequests = context.concurrentRequests;
        this.rejectedRequests = context.rejectedRequests;
        this.clock = context.clock;
        this.metrics = context.semaphoreMetrics;
    }

    @Override
    public abstract String type();

    @Override
    public Outcome tryAcquireOutcome(boolean wait) {
        return doTryAcquireOutcome(wait);
    }

    @Override
    public <T> Result<T> call(Callable<T> callable) throws Exception {
        return doCall(callable);
    }

    @Override
    public Outcome run(Runnable runnable) throws Exception {
        return doCall(() -> {
            runnable.run();
            return null;
        }).outcome();
    }

    @Override
    public void init(String socketName) {
        originName = socketName;
        metrics.init(socketName);
    }

    /**
     * Returns the initial number of permits set for this semaphore-based limit.
     * <p>
     * The initial number of permits is used to initialize the underlying semaphore.
     *
     * @return the initial number of permits
     */
    protected int initialPermits() {
        return initialPermits;
    }

    /**
     * Returns the {@link AtomicInteger} instance tracking the current number of concurrent requests.
     * <p>
     * The returned {@code AtomicInteger} is used to maintain a count of the concurrent requests being processed.
     *
     * @return the {@link AtomicInteger} instance tracking concurrent requests
     */
    protected AtomicInteger concurrentRequests() {
        return concurrentRequests;
    }

    /**
     * Returns the {@link AtomicInteger} instance tracking the number of rejected requests.
     *
     * @return rejected requests counter
     */
    protected AtomicInteger rejectedRequests() {
        return rejectedRequests;
    }

    @Override
    protected Outcome doTryAcquireOutcome(boolean wait) {

        Optional<LimitAlgorithm.Token> token = handler.tryAcquireToken(false);

        if (token.isPresent()) {
            return Outcome.immediateAcceptance(originName, type(), token.get());

        }
        if (wait && queueLength > 0) {
            long startWait = clock.get();
            token = handler.tryAcquireToken(true);
            long endWait = clock.get();
            if (token.isPresent()) {
                metrics.updateWaitTime(startWait, endWait);
                return Outcome.deferredAcceptance(originName,
                                                  type(),
                                                  token.get(),
                                                  startWait,
                                                  endWait);
            }
            rejectedRequests.getAndIncrement();
            return Outcome.deferredRejection(originName, type(), startWait, endWait);
        }
        rejectedRequests.getAndIncrement();
        return Outcome.immediateRejection(originName, type());
    }

    record Context(
            int initialPermits,
            int queueLength,
            LimiterHandler handler,
            Supplier<Long> clock,
            AtomicInteger concurrentRequests,
            AtomicInteger rejectedRequests,
            SemaphoreMetrics semaphoreMetrics) {
    }
}
