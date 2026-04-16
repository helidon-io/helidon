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
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.RuntimeType;
import io.helidon.config.Config;

/**
 * Throughput based limit, that is backed by a semaphore with timeout on the queue.
 * The default behavior is non-queuing.
 *
 * @see io.helidon.common.concurrency.limits.ThroughputLimitConfig
 */
public class ThroughputLimit extends SemaphoreLimitBase implements RuntimeType.Api<ThroughputLimitConfig> {

    /**
     * Default amount, meaning unlimited execution.
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
     * Create an instance from the provided semaphore.
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
        return switch (config.rateLimitingAlgorithm()) {
            case FIXED_RATE -> new FixedRatePermitStrategy(config, clock);
            case TOKEN_BUCKET -> new TokenBucketPermitStrategy(config, clock);
        };
    }

    private static Context context(ThroughputLimitConfig config) {
        LimitHandlers.LimiterHandler limiterHandler;
        Supplier<Long> clock = LimitUtil.clock(config);

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
            return nanosPerToken / 1000000L;
        }

        @Override
        public void refillPermits() {
            long lastRefillTime = lastRefillTimeNanos.get();
            int newTokens = (int) ((clock.get() - lastRefillTime) / nanosPerToken);
            if (newTokens > 0) {
                int permitsToRefill = Math.min(newTokens, amount - semaphore.availablePermits());
                if (permitsToRefill > 0 && lastRefillTimeNanos.compareAndSet(
                        lastRefillTime, lastRefillTime + (permitsToRefill * nanosPerToken))) {
                    // Last refill time has been set to time when most recent token was generated
                    semaphore.release(permitsToRefill);
                }
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
            return nanosPerRequest / 1000000L;
        }

        @Override
        public void refillPermits() {
            long now = clock.get();
            long lastRequestTime = lastRequestTimeNanos.get();
            if ((now - lastRequestTime) > nanosPerRequest
                    && semaphore.availablePermits() <= 0
                    && lastRequestTimeNanos.compareAndSet(lastRequestTime, now)) {
                semaphore.release();
            }
        }

        @Override
        public Optional<Semaphore> semaphore() {
            return Optional.ofNullable(semaphore);
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
