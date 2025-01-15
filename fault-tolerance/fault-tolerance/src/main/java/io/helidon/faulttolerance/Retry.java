/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.RuntimeType;

/**
 * Retry supports retry policies to be applied on an execution of asynchronous tasks.
 */
@RuntimeType.PrototypedBy(RetryConfig.class)
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
        return new RetryImpl(retryConfig);
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
     * @return number ot times a method is retried.
     */
    long retryCounter();

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
         * @param call            call index (0 for the first failed call)
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
     */
    class DelayingRetryPolicy implements RetryPolicy {
        private final int calls;
        private final long delayMillis;
        private final double delayFactor;

        private DelayingRetryPolicy(Builder builder) {
            this.calls = builder.calls;
            this.delayMillis = builder.delay.toMillis();
            this.delayFactor = builder.delayFactor;
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

            if (call == 0 || lastDelay == 0) {
                return Optional.of(delayMillis);
            }

            return Optional.of((long) (lastDelay * delayFactor));
        }

        /**
         * Fluent API builder for {@link Retry.DelayingRetryPolicy}.
         */
        public static class Builder implements io.helidon.common.Builder<Builder, DelayingRetryPolicy> {
            private int calls = 3;
            private double delayFactor = 2;
            private Duration delay = Duration.ofMillis(200);

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
     *     <li>First retry: 50 - 150 millis (delay +- Random.nextInt(jitter)</li>
     *     <li>Second retry: 50 - 150 millis</li>
     *     <li>Third retry: 50 - 150 millis</li>
     * </ul>
     */
    class JitterRetryPolicy implements RetryPolicy {
        private final int calls;
        private final long delayMillis;
        private final Supplier<Integer> randomJitter;

        private JitterRetryPolicy(Builder builder) {
            this.calls = builder.calls;
            this.delayMillis = builder.delay.toMillis();
            long jitter = builder.jitter.toMillis();
            int jitterMillis = (jitter > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) jitter;
            if (jitterMillis == 0) {
                randomJitter = () -> 0;
            } else {
                Random random = new Random();
                // need a number [-jitterMillis,+jitterMillis]
                randomJitter = () -> random.nextInt(jitterMillis * 2) - jitterMillis;
            }
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
        public Optional<Long> nextDelayMillis(long firstCallNanos, long lastDelay, int call) {
            if (call >= calls) {
                return Optional.empty();
            }

            long delay = delayMillis;
            int jitterRandom = randomJitter.get();
            delay = delay + jitterRandom;
            delay = Math.max(0, delay);

            return Optional.of(delay);
        }

        /**
         * Fluent API builder for {@link Retry.JitterRetryPolicy}.
         */
        public static class Builder implements io.helidon.common.Builder<Builder, JitterRetryPolicy> {
            private int calls = 3;
            private Duration delay = Duration.ofMillis(200);
            private Duration jitter = Duration.ofMillis(50);

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
                this.jitter = jitter;
                return this;
            }
        }
    }
}
