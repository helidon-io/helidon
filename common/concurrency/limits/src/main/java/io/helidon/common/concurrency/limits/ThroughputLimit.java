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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.config.Config;

import static io.helidon.common.concurrency.limits.RateLimitingAlgorithmType.FIXED_RATE;
import static io.helidon.common.concurrency.limits.RateLimitingAlgorithmType.TOKEN_BUCKET;

/**
 * Throughput based limit, that is backed by a semaphore with timeout on the queue.
 * The default behavior is non-queuing.
 *
 * @see io.helidon.common.concurrency.limits.ThroughputLimitConfig
 */
@SuppressWarnings("removal")
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
    private final PermitStrategy permitStrategy;

    private ThroughputLimit(ThroughputLimitConfig config) {
        super(config.clock(), config.enableMetrics(), config.name());
        this.config = config;
        this.permitStrategy = initializePermitStrategy();
        setSemaphore(permitStrategy.initializePermits());
        if (getSemaphore() == null) {
            setInitialPermits(0);
            setQueueLength(0);
            setHandler(new LimitHandlers.NoOpSemaphoreHandler());
        } else {
            setInitialPermits(getSemaphore().availablePermits());
            setQueueLength(Math.max(0, config.queueLength()));
            setHandler(new LimitHandlers.QueuedSemaphoreHandler(
                getSemaphore(), getQueueLength(), config.queueTimeout(),
                ThroughputToken::new, permitStrategy.maxWaitMillis(), permitStrategy::refillPermits));
        }
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
                .semaphore(new Semaphore(getInitialPermits(), semaphore.isFair()))
                .build();
        }
        return config.build();
    }

    private PermitStrategy initializePermitStrategy() {
        return switch (Optional.ofNullable(config.rateLimitingAlgorithm())
            .orElse(TOKEN_BUCKET)) {
            case FIXED_RATE -> new FixedRatePermitStrategy();
            case TOKEN_BUCKET -> new TokenBucketPermitStrategy();
        };
    }

    private interface PermitStrategy {
        Semaphore initializePermits();

        long maxWaitMillis();

        void refillPermits();
    }

    private class TokenBucketPermitStrategy implements PermitStrategy {
        private final long nanosPerToken;
        private final AtomicLong lastRefillTimeNanos = new AtomicLong();

        TokenBucketPermitStrategy() {
            this.nanosPerToken = config.amount() > 0 ? config.duration().toNanos() / config.amount() : 0;
        }

        @Override
        public Semaphore initializePermits() {
            if (config.amount() == 0 && config.semaphore().isEmpty()) {
                return null;
            } else {
                lastRefillTimeNanos.set(getClock().get());
                return config.semaphore().orElseGet(() -> new Semaphore(config.amount(), config.fair()));
            }
        }

        @Override
        public long maxWaitMillis() {
            return nanosPerToken / 1000000L;
        }

        @Override
        public void refillPermits() {
            long lastRefillTime = lastRefillTimeNanos.get();
            int newTokens = (int) ((getClock().get() - lastRefillTime) / nanosPerToken);
            if (newTokens > 0) {
                int permitsToRefill = Math.min(newTokens, config.amount() - getSemaphore().availablePermits());
                if (permitsToRefill > 0 && lastRefillTimeNanos.compareAndSet(
                    lastRefillTime, lastRefillTime + (permitsToRefill * nanosPerToken))) {
                    // Last refill time has been set to time when most recent token was generated
                    getSemaphore().release(permitsToRefill);
                }
            }
        }
    }

    private class FixedRatePermitStrategy implements PermitStrategy {
        private final long nanosPerRequest;
        private final AtomicLong lastRequestTimeNanos = new AtomicLong();

        FixedRatePermitStrategy() {
            this.nanosPerRequest = config.amount() > 0 ? config.duration().toNanos() / config.amount() : 0;
        }

        @Override
        public Semaphore initializePermits() {
            if (config.amount() == 0 && config.semaphore().isEmpty()) {
                return null;
            } else {
                lastRequestTimeNanos.set(getClock().get());
                return config.semaphore().orElseGet(() -> new Semaphore(1, config.fair()));
            }
        }

        @Override
        public long maxWaitMillis() {
            return nanosPerRequest / 1000000L;
        }

        @Override
        public void refillPermits() {
            long now = getClock().get();
            long lastRequestTime = lastRequestTimeNanos.get();
            if ((now - lastRequestTime) > nanosPerRequest
                && getSemaphore().availablePermits() <= 0
                && lastRequestTimeNanos.compareAndSet(lastRequestTime, now)) {
                getSemaphore().release();
            }
        }
    }

    private class ThroughputToken implements LimitAlgorithm.Token {
        private final long startTime = getClock().get();

        ThroughputToken() {
            getConcurrentRequests().incrementAndGet();
        }

        @Override
        public void dropped() {
            updateMetrics(startTime, getClock().get());
        }

        @Override
        public void ignore() {
            getConcurrentRequests().decrementAndGet();
        }

        @Override
        public void success() {
            updateMetrics(startTime, getClock().get());
            getConcurrentRequests().decrementAndGet();
        }
    }
}
