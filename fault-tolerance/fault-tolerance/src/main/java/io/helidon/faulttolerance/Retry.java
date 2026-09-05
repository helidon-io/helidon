/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.LazyValue;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.service.registry.Services;

/**
 * Retry supports retry policies to be applied on an execution of asynchronous tasks.
 */
public interface Retry extends FtHandler, RuntimeType.Api<RetryConfig> {

    /**
     * Counter for all the calls in a retry. Will always be greater than
     * {@link #FT_RETRY_RETRIES_TOTAL} that only counts actual retries.
     */
    String FT_RETRY_CALLS_TOTAL = "ft.retry.calls.total";

    /**
     * Counter for all retry calls, excluding the initial call.
     */
    String FT_RETRY_RETRIES_TOTAL = "ft.retry.retries.total";

    /**
     * Create a new retry from its configuration.
     *
     * @param retryConfig configuration of the retry to create
     * @return a new retry
     */
    static Retry create(RetryConfig retryConfig) {
        return new RetryImpl(retryConfig,
                             LazyValue.create(() -> Services.get(MeterRegistry.class)));
    }

    /**
     * Create a new retry with customized configuration.
     *
     * @param builderConsumer consumer of builder
     * @return a new retry customized through builder
     */
    static Retry create(Consumer<RetryConfig.Builder> builderConsumer) {
        return create(RetryConfig.builder()
                              .update(builderConsumer)
                              .buildPrototype());
    }

    /**
     * Create a new retry fluent API builder.
     *
     * @return a new builder
     */
    static RetryConfig.Builder builder() {
        return RetryConfig.builder();
    }

    /**
     * Number of times a method called has been retried. This is a monotonically
     * increasing counter over the lifetime of the handler.
     *
     * @return number of times a method is retried
     */
    long retryCounter();

    /**
     * Invoke a function with context for each attempt.
     * <p>
     * Unlike {@link #invoke(Supplier)}, an invocation that does not complete successfully always throws a
     * {@link RetryException}. The exception contains the retry outcome, including a termination reason.
     *
     * @param function function invoked for each attempt
     * @param <T> type of result
     * @return result obtained from the function
     * @throws RetryException if the invocation does not complete successfully
     * @throws java.lang.UnsupportedOperationException if you use a custom retry implementation, and it does not
     *          implement this method
     */
    default <T> T invoke(Function<RetryContext, ? extends T> function) {
        throw new UnsupportedOperationException("This retry does not implement invoked(Function): " + getClass().getName());
    }

    /**
     * Invoke a function with context for each attempt, using a caller-provided strategy to wait between attempts.
     * <p>
     * The wait strategy can perform work needed to keep a resource alive while waiting, or terminate retries by
     * returning {@code false}.
     *
     * @param function function invoked for each attempt
     * @param waitStrategy strategy that waits between attempts
     * @param <T> type of result
     * @return result obtained from the function
     * @throws RetryException if the invocation does not complete successfully
     * @throws java.lang.UnsupportedOperationException if you use a custom retry implementation, and it does not
     *          implement this method
     */
    default <T> T invoke(Function<RetryContext, ? extends T> function, WaitStrategy waitStrategy) {
        throw new UnsupportedOperationException("This retry does not implement invoked(Function, WaitStrategy): "
                                                        + getClass().getName());
    }

    private static long durationToMillis(Duration duration) {
        Objects.requireNonNull(duration);
        try {
            return Math.max(0, duration.toMillis());
        } catch (ArithmeticException e) {
            return duration.isNegative() ? 0 : Long.MAX_VALUE;
        }
    }

    private static void validatePolicy(int calls,
                                       Duration delay,
                                       Duration jitter,
                                       double jitterFactor,
                                       Duration maxDelay) {
        Objects.requireNonNull(delay);
        Objects.requireNonNull(jitter);
        Objects.requireNonNull(maxDelay);
        if (calls < 1) {
            throw new IllegalArgumentException("Calls must be at least 1");
        }
        if (delay.isNegative()) {
            throw new IllegalArgumentException("Delay must not be negative");
        }
        if (jitter.isNegative()) {
            throw new IllegalArgumentException("Jitter must not be negative");
        }
        if (!Double.isFinite(jitterFactor) || jitterFactor < 0 || jitterFactor >= 1) {
            throw new IllegalArgumentException("Jitter factor must be greater than or equal to 0 and lower than 1");
        }
        if (!jitter.isZero() && jitterFactor != 0) {
            throw new IllegalArgumentException("Jitter and jitter factor cannot both be greater than zero");
        }
        if (maxDelay.isNegative()) {
            throw new IllegalArgumentException("Maximum delay must not be negative");
        }
    }

    private static long multiply(long value, double factor) {
        double result = value * factor;
        if (Double.isNaN(result) || result <= 0) {
            return 0;
        }
        if (result >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) result;
    }

    private static long withJitter(SecureRandom random,
                                   long delay,
                                   long jitter,
                                   double jitterFactor,
                                   long maxDelay) {
        long result = delay;
        if (jitterFactor > 0) {
            jitter = multiply(delay, jitterFactor);
        }
        if (jitter > 0) {
            long randomJitter;
            if (jitter <= Long.MAX_VALUE / 2) {
                randomJitter = random.nextLong(-jitter, jitter);
            } else {
                long magnitude = random.nextLong(jitter);
                randomJitter = random.nextBoolean() ? magnitude : -magnitude;
            }
            if (randomJitter > 0 && result > Long.MAX_VALUE - randomJitter) {
                result = Long.MAX_VALUE;
            } else {
                result = Math.max(0, result + randomJitter);
            }
        }
        return Math.min(result, maxDelay);
    }

    /**
     * Strategy used to wait before another invocation attempt.
     */
    @FunctionalInterface
    interface WaitStrategy {
        /**
         * Wait before the next attempt.
         * <p>
         * An implementation that is interrupted must restore the thread interrupt status before returning or throwing.
         * The retry invocation then terminates with {@link RetryOutcome.Termination#INTERRUPTED}.
         *
         * @param delay proposed delay before the next attempt
         * @return {@code true} when the requested wait completed and another attempt should be made, {@code false} to
         *         terminate retries
         */
        boolean await(Duration delay);
    }

    /**
     * Retry policy to handle delays between retries.
     * The implementation must not save state, as a single instance
     * will be used by multiple threads and executions in parallel.
     */
    interface RetryPolicy {
        /**
         * Return next delay in milliseconds, or an empty optional to finish retries.
         *
         * @param firstCallMillis milliseconds recorded before the first call using {@link System#currentTimeMillis()}
         * @param lastDelay       last delay that was used (0 for the first failed call)
         * @param call            number of completed calls (1 for the first failed invocation)
         * @return how long to wait before trying again, or empty to notify this is the end of retries
         */
        Optional<Long> nextDelayMillis(long firstCallMillis, long lastDelay, int call);
    }

    /**
     * A retry policy that prolongs the delays between retries by a defined factor.
     * <p>
     * Consider the following setup:
     * <ul>
     *     <li>{@code calls = 4}</li>
     *     <li>{@code delayMillis = 100}</li>
     *     <li>{@code factor = 2.0}</li>
     * </ul>
     * The following delays will be used for each call:
     *
     * <ul>
     *     <li>Initial call - always immediate (not handled by retry policy)</li>
     *     <li>First retry - 100 millis</li>
     *     <li>Second retry - 200 millis (previous delay * factor)</li>
     *     <li>Third retry - 400 millis (previous delay * factor)</li>
     * </ul>
     * An optional absolute or relative jitter is applied after multiplying the delay. The final non-negative delay,
     * including jitter, is capped by the configured maximum delay.
     */
    class DelayingRetryPolicy implements RetryPolicy {
        private static final SecureRandom RANDOM = new SecureRandom();

        private final int calls;
        private final long delayMillis;
        private final double delayFactor;
        private final long jitterMillis;
        private final double jitterFactor;
        private final long maxDelayMillis;

        private DelayingRetryPolicy(Builder builder) {
            validatePolicy(builder.calls, builder.delay, builder.jitter, builder.jitterFactor, builder.maxDelay);
            if (!Double.isFinite(builder.delayFactor) || builder.delayFactor < 0) {
                throw new IllegalArgumentException("Delay factor must be a finite, non-negative number");
            }
            this.calls = builder.calls;
            this.delayMillis = durationToMillis(builder.delay);
            this.delayFactor = builder.delayFactor;
            this.jitterMillis = durationToMillis(builder.jitter);
            this.jitterFactor = builder.jitterFactor;
            this.maxDelayMillis = durationToMillis(builder.maxDelay);
        }

        /**
         * A builder to customize configuration of {@link Retry.DelayingRetryPolicy}.
         *
         * @return a new builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Create a retry policy with no delays and with the specified number of calls.
         *
         * @param calls number of calls to execute (retries + initial call)
         * @return a no delay retry policy
         */
        public static DelayingRetryPolicy noDelay(int calls) {
            return builder()
                    .delay(Duration.ZERO)
                    .delayFactor(0)
                    .calls(calls)
                    .build();
        }

        @Override
        public Optional<Long> nextDelayMillis(long firstCallMillis, long lastDelay, int call) {
            if (call >= calls) {
                return Optional.empty();
            }

            long delay = multiply(delayMillis, Math.pow(delayFactor, Math.max(0, call - 1)));

            return Optional.of(withJitter(RANDOM, delay, jitterMillis, jitterFactor, maxDelayMillis));
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof DelayingRetryPolicy other)) {
                return false;
            }
            return calls == other.calls
                    && delayMillis == other.delayMillis
                    && Double.compare(delayFactor, other.delayFactor) == 0
                    && jitterMillis == other.jitterMillis
                    && Double.compare(jitterFactor, other.jitterFactor) == 0
                    && maxDelayMillis == other.maxDelayMillis;
        }

        @Override
        public int hashCode() {
            return Objects.hash(calls, delayMillis, delayFactor, jitterMillis, jitterFactor, maxDelayMillis);
        }

        /**
         * Fluent API builder for {@link Retry.DelayingRetryPolicy}.
         */
        public static class Builder implements io.helidon.common.Builder<Builder, DelayingRetryPolicy> {
            private int calls = 3;
            private double delayFactor = 2;
            private Duration delay = Duration.ofMillis(200);
            private Duration jitter = Duration.ZERO;
            private double jitterFactor;
            private Duration maxDelay = Duration.ofMillis(Long.MAX_VALUE);

            private Builder() {
            }

            @Override
            public DelayingRetryPolicy build() {
                return new DelayingRetryPolicy(this);
            }

            /**
             * Total number of calls (first + retries).
             *
             * @param calls how many times to call the method
             * @return updated builder instance
             */
            public Builder calls(int calls) {
                this.calls = calls;
                return this;
            }

            /**
             * Base delay between the invocations.
             *
             * @param delay delay between the invocations
             * @return updated builder instance
             */
            public Builder delay(Duration delay) {
                this.delay = delay;
                return this;
            }

            /**
             * A delay multiplication factor.
             *
             * @param delayFactor a delay multiplication factor
             * @return updated builder instance
             */
            public Builder delayFactor(double delayFactor) {
                this.delayFactor = delayFactor;
                return this;
            }

            /**
             * Random part of the delay.
             * A number between {@code [-jitter,+jitter]} is applied after the delay factor is calculated.
             *
             * @param jitter jitter duration
             * @return updated builder instance
             */
            public Builder jitter(Duration jitter) {
                this.jitter = Objects.requireNonNull(jitter);
                return this;
            }

            /**
             * Random jitter relative to the calculated delay, from {@code 0} (inclusive) to {@code 1} (exclusive).
             * For example, a value of {@code 0.2} applies a random jitter of up to twenty percent in either direction.
             * Relative and absolute jitter cannot both be greater than zero.
             *
             * @param jitterFactor relative jitter factor
             * @return updated builder instance
             */
            public Builder jitterFactor(double jitterFactor) {
                this.jitterFactor = jitterFactor;
                return this;
            }

            /**
             * Maximum delay between invocation attempts, including jitter. Jitter is applied first and the final
             * non-negative delay is then capped by this value.
             *
             * @param maxDelay maximum delay
             * @return updated builder instance
             */
            public Builder maxDelay(Duration maxDelay) {
                this.maxDelay = Objects.requireNonNull(maxDelay);
                return this;
            }
        }
    }

    /**
     * A retry policy that randomizes delays between execution using a "jitter" time.
     * <p>
     * Consider the following setup:
     * <ul>
     *     <li>{@code calls = 4}</li>
     *     <li>{@code delayMillis = 100}</li>
     *     <li>{@code jitter = 50}</li>
     * </ul>
     * The following delays will be used for each call:
     *
     * <ul>
     *     <li>Initial call - always immediate (not handled by retry policy)</li>
     *     <li>First retry: 50 (inclusive) - 150 (exclusive) millis</li>
     *     <li>Second retry: 50 (inclusive) - 150 (exclusive) millis</li>
     *     <li>Third retry: 50 (inclusive) - 150 (exclusive) millis</li>
     * </ul>
     * The final non-negative delay, including jitter, is capped by the configured maximum delay.
     */
    class JitterRetryPolicy implements RetryPolicy {
        private static final SecureRandom RANDOM = new SecureRandom();

        private final int calls;
        private final long delayMillis;
        private final long jitterMillis;
        private final long maxDelayMillis;

        private JitterRetryPolicy(Builder builder) {
            validatePolicy(builder.calls, builder.delay, builder.jitter, 0, builder.maxDelay);
            this.calls = builder.calls;
            this.delayMillis = durationToMillis(builder.delay);
            this.jitterMillis = durationToMillis(builder.jitter);
            this.maxDelayMillis = durationToMillis(builder.maxDelay);
        }

        /**
         * A new fluent API builder to configure instances of {@link Retry}.
         *
         * @return a new builder
         */
        public static Builder builder() {
            return new Builder();
        }

        @Override
        public Optional<Long> nextDelayMillis(long firstCallMillis, long lastDelay, int call) {
            if (call >= calls) {
                return Optional.empty();
            }

            return Optional.of(withJitter(RANDOM, delayMillis, jitterMillis, 0, maxDelayMillis));
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof JitterRetryPolicy other)) {
                return false;
            }
            return calls == other.calls
                    && delayMillis == other.delayMillis
                    && jitterMillis == other.jitterMillis
                    && maxDelayMillis == other.maxDelayMillis;
        }

        @Override
        public int hashCode() {
            return Objects.hash(calls, delayMillis, jitterMillis, maxDelayMillis);
        }

        /**
         * Fluent API builder for {@link Retry.JitterRetryPolicy}.
         */
        public static class Builder implements io.helidon.common.Builder<Builder, JitterRetryPolicy> {
            private int calls = 3;
            private Duration delay = Duration.ofMillis(200);
            private Duration jitter = Duration.ofMillis(50);
            private Duration maxDelay = Duration.ofMillis(Long.MAX_VALUE);

            private Builder() {
            }

            @Override
            public JitterRetryPolicy build() {
                return new JitterRetryPolicy(this);
            }

            /**
             * Total number of calls (first + retries).
             *
             * @param calls how many times to call the method
             * @return updated builder instance
             */
            public Builder calls(int calls) {
                this.calls = calls;
                return this;
            }

            /**
             * Base delay between the invocations.
             *
             * @param delay delay between the invocations
             * @return updated builder instance
             */
            public Builder delay(Duration delay) {
                this.delay = delay;
                return this;
            }

            /**
             * Random part of the delay.
             * A number between {@code [-jitter,+jitter]} is applied to delay each time
             * delay is calculated.
             *
             * @param jitter jitter duration
             * @return updated builder instance
             */
            public Builder jitter(Duration jitter) {
                this.jitter = Objects.requireNonNull(jitter);
                return this;
            }

            /**
             * Maximum delay between invocation attempts, including jitter. Jitter is applied first and the final
             * non-negative delay is then capped by this value.
             *
             * @param maxDelay maximum delay
             * @return updated builder instance
             */
            public Builder maxDelay(Duration maxDelay) {
                this.maxDelay = Objects.requireNonNull(maxDelay);
                return this;
            }
        }
    }

}
