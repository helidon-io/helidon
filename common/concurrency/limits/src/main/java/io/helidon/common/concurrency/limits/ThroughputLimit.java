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

import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;

/**
 * Throughput based limit, that is backed by a semaphore with timeout on the queue.
 * The default behavior is non-queuing.
 *
 * @see io.helidon.common.concurrency.limits.ThroughputLimitConfig
 */
@Api.Preview
public class ThroughputLimit extends SemaphoreLimitBase implements RuntimeType.Api<ThroughputLimitConfig> {

    /**
     * Default amount. Execution is unlimited unless a supplied semaphore limits concurrency.
     */
    public static final int DEFAULT_AMOUNT = 0;

    /**
     * Default duration over which to count operations.
     */
    public static final String DEFAULT_DURATION = "PT1S";

    /**
     * Default length of the queue.
     */
    public static final int DEFAULT_QUEUE_LENGTH = SemaphoreLimitBase.DEFAULT_QUEUE_LENGTH;

    /**
     * Timeout of a request that is enqueued.
     */
    public static final String DEFAULT_QUEUE_TIMEOUT_DURATION = "PT1S";

    static final String TYPE = "throughput";

    private final ThroughputLimitConfig config;

    private ThroughputLimit(ThroughputLimitConfig config) {
        super(context(config));

        this.config = config;
    }

    /**
     * Create a new fluent API builder to construct {@link ThroughputLimit}
     * instance.
     *
     * @return fluent API builder
     */
    public static ThroughputLimitConfig.Builder builder() {
        return ThroughputLimitConfig.builder();
    }

    /**
     * Create a new instance with all defaults (no limit).
     *
     * @return a new limit instance
     */
    public static ThroughputLimit create() {
        return builder().build();
    }

    /**
     * Create an instance from the provided semaphore. Permits are returned when work completes,
     * and no time-based replenishment is performed.
     *
     * @param semaphore semaphore to use
     * @return a new throughput limit backed by the provided semaphore
     */
    public static ThroughputLimit create(Semaphore semaphore) {
        return builder()
                .semaphore(semaphore)
                .build();
    }

    /**
     * Create a new instance from configuration.
     *
     * @param config configuration of the throughput limit
     * @return a new limit instance configured from {@code config}
     */
    public static ThroughputLimit create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    /**
     * Create a new instance from configuration.
     *
     * @param config configuration of the throughput limit
     * @return a new limit instance configured from {@code config}
     */
    public static ThroughputLimit create(ThroughputLimitConfig config) {
        return new ThroughputLimit(config);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param consumer consumer of configuration builder
     * @return a new limit instance configured from the builder
     */
    public static ThroughputLimit create(Consumer<ThroughputLimitConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    @Override
    public ThroughputLimitConfig prototype() {
        return config;
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Limit copy() {
        if (config.semaphore().isPresent()) {
            Semaphore semaphore = config.semaphore().get();

            return ThroughputLimitConfig.builder()
                    .from(config)
                    .semaphore(new Semaphore(initialPermits(), semaphore.isFair()))
                    .build();
        }
        return config.build();
    }

    private static PermitStrategy permitStrategy(ThroughputLimitConfig config, Supplier<Long> clock) {
        int amount = config.amount();
        if (amount < 0) {
            throw new ConfigException("Throughput amount must be nonnegative");
        }
        if (amount > 0) {
            if (!config.duration().isPositive()) {
                throw new ConfigException("Throughput duration must be positive when amount is positive");
            }
            long durationNanos = config.duration().toNanos();
            if (durationNanos % amount != 0) {
                return new FractionalPermitStrategy(config, clock, durationNanos);
            }
        }
        return switch (config.rateLimitingAlgorithm()) {
            case FIXED_RATE -> new FixedRatePermitStrategy(config, clock);
            case TOKEN_BUCKET -> new TokenBucketPermitStrategy(config, clock);
        };
    }

    private static Context context(ThroughputLimitConfig config) {
        LimitHandlers.LimiterHandler limiterHandler;
        Supplier<Long> clock = LimitUtil.clock(config);

        if (config.amount() == 0 && config.semaphore().isPresent()) {
            return FixedLimit.context(FixedLimitConfig.builder()
                                              .semaphore(config.semaphore().get())
                                              .queueLength(config.queueLength())
                                              .queueTimeout(config.queueTimeout())
                                              .name(config.name())
                                              .clock(clock)
                                              .enableMetrics(config.enableMetrics())
                                              .buildPrototype());
        }

        AtomicInteger concurrentRequests = new AtomicInteger();
        AtomicInteger rejectedRequests = new AtomicInteger();
        PermitStrategy permitStrategy = permitStrategy(config, clock);
        Semaphore semaphore = permitStrategy.semaphore().orElse(null);

        SemaphoreMetrics metrics = new SemaphoreMetrics(config.enableMetrics(),
                                                        semaphore,
                                                        config.name(),
                                                        concurrentRequests,
                                                        rejectedRequests);

        if (semaphore == null) {
            return new Context(0,
                               0,
                               new LimitHandlers.NoOpSemaphoreHandler(),
                               clock,
                               new AtomicInteger(),
                               rejectedRequests,
                               metrics);
        }
        int initialPermits = semaphore.availablePermits();
        int queueLength = Math.max(0, config.queueLength());

        Supplier<Token> tokenSupplier = () -> new ThroughputToken(concurrentRequests, clock, metrics);
        limiterHandler = new LimitHandlers.QueuedSemaphoreHandler(semaphore,
                                                                  queueLength,
                                                                  config.queueTimeout(),
                                                                  tokenSupplier,
                                                                  permitStrategy.maxWaitMillis(),
                                                                  permitStrategy::refillPermits);

        return new Context(initialPermits,
                           queueLength,
                           limiterHandler,
                           clock,
                           concurrentRequests,
                           rejectedRequests,
                           metrics);
    }

    private interface PermitStrategy {
        long maxWaitMillis();

        void refillPermits();

        Optional<Semaphore> semaphore();
    }

    private static class TokenBucketPermitStrategy implements PermitStrategy {
        private final long nanosPerToken;
        private final AtomicBoolean refillInProgress = new AtomicBoolean();
        private final AtomicLong lastRefillTimeNanos = new AtomicLong();
        private final int amount;
        private final Supplier<Long> clock;
        private final Semaphore semaphore;

        TokenBucketPermitStrategy(ThroughputLimitConfig config, Supplier<Long> clock) {
            this.nanosPerToken = config.amount() > 0 ? config.duration().toNanos() / config.amount() : 0;
            this.amount = config.amount();
            this.clock = clock;

            if (amount == 0 && config.semaphore().isEmpty()) {
                this.semaphore = null;
            } else {
                lastRefillTimeNanos.set(clock.get());
                this.semaphore = config.semaphore().orElseGet(() -> new Semaphore(config.amount(), config.fair()));
            }
        }

        @Override
        public long maxWaitMillis() {
            return Math.ceilDiv(nanosPerToken, 1_000_000L);
        }

        @Override
        public void refillPermits() {
            long lastRefillTime = lastRefillTimeNanos.get();
            if ((clock.get() - lastRefillTime) < nanosPerToken) {
                return;
            }

            if (!refillInProgress.compareAndSet(false, true)) {
                return;
            }
            try {
                lastRefillTime = lastRefillTimeNanos.get();
                long now = clock.get();
                long elapsedNanos = now - lastRefillTime;
                long newTokens = elapsedNanos / nanosPerToken;
                if (newTokens > 0) {
                    long headroom = Math.max(0L, (long) amount - semaphore.availablePermits());
                    int permitsToRefill = (int) Math.min(newTokens, headroom);
                    if (permitsToRefill > 0) {
                        semaphore.release(permitsToRefill);
                    }
                    // Publish the refill time after the permits so fast-path readers cannot miss a pending release.
                    long refilledAt = clock.get();
                    long elapsedAtRefill = refilledAt - lastRefillTime;
                    lastRefillTimeNanos.set(refilledAt - elapsedAtRefill % nanosPerToken);
                }
            } finally {
                refillInProgress.set(false);
            }
        }

        @Override
        public Optional<Semaphore> semaphore() {
            return Optional.ofNullable(semaphore);
        }
    }

    private static class FixedRatePermitStrategy implements PermitStrategy {
        private final long nanosPerRequest;
        private final Supplier<Long> clock;
        private final AtomicLong lastRequestTimeNanos = new AtomicLong();
        private final Semaphore semaphore;

        FixedRatePermitStrategy(ThroughputLimitConfig config, Supplier<Long> clock) {
            this.nanosPerRequest = config.amount() > 0 ? config.duration().toNanos() / config.amount() : 0;
            this.clock = clock;
            if (config.amount() == 0 && config.semaphore().isEmpty()) {
                this.semaphore = null;
            } else {
                lastRequestTimeNanos.set(clock.get());
                this.semaphore = config.semaphore().orElseGet(() -> new Semaphore(1, config.fair()));
            }
        }

        @Override
        public long maxWaitMillis() {
            return Math.ceilDiv(nanosPerRequest, 1_000_000L);
        }

        @Override
        public void refillPermits() {
            long lastRequestTime = lastRequestTimeNanos.get();
            long now = clock.get();
            if (semaphore.availablePermits() > 0) {
                // An available permit does not earn credit towards the next request while idle.
                lastRequestTimeNanos.compareAndSet(lastRequestTime, now);
            } else if (now - lastRequestTime >= nanosPerRequest && lastRequestTimeNanos.compareAndSet(lastRequestTime, now)) {
                semaphore.release();
            }
        }

        @Override
        public Optional<Semaphore> semaphore() {
            return Optional.ofNullable(semaphore);
        }
    }

    private static class FractionalPermitStrategy implements PermitStrategy {
        private final long durationNanos;
        private final int amount;
        private final long wholeNanosPerPermit;
        private final long fractionPerPermit;
        private final boolean tokenBucket;
        private final int capacity;
        private final Supplier<Long> clock;
        private final Semaphore semaphore;
        private final AtomicBoolean refillInProgress = new AtomicBoolean();
        private final AtomicLong lastFixedRatePermitObservedNanos;

        // The last accounted permit boundary is lastRefillTimeNanos + lastRefillFraction / amount.
        private long lastRefillTimeNanos;
        private int lastRefillFraction;
        private long lastFixedRateRefillNanos;

        FractionalPermitStrategy(ThroughputLimitConfig config, Supplier<Long> clock, long durationNanos) {
            this.durationNanos = durationNanos;
            this.amount = config.amount();
            this.wholeNanosPerPermit = durationNanos / amount;
            this.fractionPerPermit = durationNanos % amount;
            this.tokenBucket = config.rateLimitingAlgorithm() == RateLimitingAlgorithmType.TOKEN_BUCKET;
            this.capacity = tokenBucket ? amount : 1;
            this.clock = clock;
            this.semaphore = config.semaphore().orElseGet(() -> new Semaphore(capacity, config.fair()));
            long now = clock.get();
            this.lastRefillTimeNanos = now;
            this.lastFixedRateRefillNanos = now;
            this.lastFixedRatePermitObservedNanos = new AtomicLong(now);
        }

        @Override
        public long maxWaitMillis() {
            return Math.ceilDiv(wholeNanosPerPermit + 1, 1_000_000L);
        }

        @Override
        public void refillPermits() {
            boolean ordinaryFixedRate = !tokenBucket && wholeNanosPerPermit > 0;
            // Timestamp the observation before reading availability. A caller can claim a permit while refill is busy.
            long observedAt = ordinaryFixedRate ? clock.get() : 0;
            if (ordinaryFixedRate && semaphore.availablePermits() > 0) {
                observeFixedRatePermit(observedAt);
                return;
            }
            if (!refillInProgress.compareAndSet(false, true)) {
                return;
            }
            try {
                long now = ordinaryFixedRate ? observedAt : clock.get();
                if (tokenBucket) {
                    refillBucket(now);
                } else {
                    refillFixedRate(now);
                }
            } finally {
                refillInProgress.set(false);
            }
        }

        @Override
        public Optional<Semaphore> semaphore() {
            return Optional.of(semaphore);
        }

        private void refillBucket(long now) {
            long elapsed = now - lastRefillTimeNanos;
            if (elapsed <= 0) {
                return;
            }
            // Leave 1..durationNanos for the last window to include a fractional boundary at its end.
            long windows = (elapsed - 1) / durationNanos;
            long tail = elapsed - windows * durationNanos;
            int due = permitsInWindow(tail);
            if (windows == 0 && due == 0) {
                return;
            }
            long earned = Math.min(windows, Integer.MAX_VALUE) * amount + due;
            long headroom = (long) capacity - semaphore.availablePermits();
            int refill = (int) Math.min(Integer.MAX_VALUE, Math.min(earned, headroom));
            if (refill > 0) {
                semaphore.release(refill);
                // Discard whole permits earned while a release was in progress.
                long published = clock.get();
                if (published != now) {
                    elapsed = published - lastRefillTimeNanos;
                    windows = (elapsed - 1) / durationNanos;
                    due = permitsInWindow(elapsed - windows * durationNanos);
                }
            }
            lastRefillTimeNanos += windows * durationNanos;
            advanceBoundary(due);
        }

        private void refillFixedRate(long now) {
            if (semaphore.availablePermits() > 0) {
                if (wholeNanosPerPermit > 0) {
                    observeFixedRatePermit(now);
                }
                return;
            }
            if (wholeNanosPerPermit > 0) {
                long observedAt = lastFixedRatePermitObservedNanos.get();
                // An available permit starts a new interval only when observed after its refill timestamp.
                if (observedAt - lastFixedRateRefillNanos > 0) {
                    lastRefillTimeNanos = observedAt;
                    lastRefillFraction = 0;
                }
            } else if (now - lastFixedRateRefillNanos > 1) {
                // Only this nanosecond's allowance survives an idle period at sub-nanosecond rates.
                lastRefillTimeNanos = now - 1;
                lastRefillFraction = 0;
            }
            long elapsed = now - lastRefillTimeNanos;
            long nextPermit = wholeNanosPerPermit + Math.ceilDiv(lastRefillFraction + fractionPerPermit, amount);
            if (elapsed < nextPermit) {
                return;
            }
            semaphore.release();
            if (wholeNanosPerPermit > 0 && elapsed > nextPermit) {
                lastRefillTimeNanos = now;
                lastRefillFraction = 0;
            } else {
                advanceBoundary(1);
            }
            lastFixedRateRefillNanos = now;
        }

        private void observeFixedRatePermit(long now) {
            lastFixedRatePermitObservedNanos.accumulateAndGet(now, (previous, observed) -> observed - previous > 0
                    ? observed : previous);
        }

        private int permitsInWindow(long nanos) {
            if (nanos <= Long.MAX_VALUE / amount) {
                return (int) ((nanos * amount - lastRefillFraction) / durationNanos);
            }
            return BigInteger.valueOf(nanos)
                    .multiply(BigInteger.valueOf(amount))
                    .subtract(BigInteger.valueOf(lastRefillFraction))
                    .divide(BigInteger.valueOf(durationNanos))
                    .intValueExact();
        }

        private void advanceBoundary(int permits) {
            // Both permits and fractionPerPermit are bounded by amount, an int.
            long fraction = lastRefillFraction + permits * fractionPerPermit;
            lastRefillTimeNanos += permits * wholeNanosPerPermit + fraction / amount;
            lastRefillFraction = (int) (fraction % amount);
        }
    }

    private static class ThroughputToken implements LimitAlgorithm.Token {
        private final AtomicInteger concurrentRequests;
        private final long startTime;
        private final Supplier<Long> clock;
        private final SemaphoreMetrics metrics;

        ThroughputToken(AtomicInteger concurrentRequests, Supplier<Long> clock, SemaphoreMetrics metrics) {
            this.concurrentRequests = concurrentRequests;
            this.startTime = clock.get();
            this.clock = clock;
            this.metrics = metrics;

            concurrentRequests.incrementAndGet();
        }

        @Override
        public void dropped() {
            try {
                metrics.updateRtt(startTime, clock.get());
            } finally {
                // Dropped work is no longer running, so keep the concurrency gauge accurate.
                concurrentRequests.decrementAndGet();
            }
        }

        @Override
        public void ignore() {
            concurrentRequests.decrementAndGet();
        }

        @Override
        public void success() {
            metrics.updateRtt(startTime, clock.get());
            concurrentRequests.decrementAndGet();
        }
    }
}
