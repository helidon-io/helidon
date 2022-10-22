/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Retry supports retry policies to be applied on an execution of asynchronous tasks.
 * <p>
 * In case you call the {@link #invokeMulti(java.util.function.Supplier)} method, the following restriction applies:
 * <ul>
 *     <li>In case at least one record was sent (one {@code onNext} was called), the retry will not trigger.</li>
 * </ul>
 */
public interface Retry extends FtHandler {
    /**
     * A new builder to customize {@code Retry} configuration.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent API builder for {@link io.helidon.faulttolerance.Retry}.
     */
    @Configured
    class Builder implements io.helidon.common.Builder<Builder, Retry> {
        private final Set<Class<? extends Throwable>> applyOn = new HashSet<>();
        private final Set<Class<? extends Throwable>> skipOn = new HashSet<>();

        private RetryPolicy retryPolicy = JitterRetryPolicy.builder()
                .calls(4)
                .delay(Duration.ofMillis(200))
                .jitter(Duration.ofMillis(50))
                .build();

        private Duration overallTimeout = Duration.ofSeconds(1);
        private LazyValue<? extends ScheduledExecutorService> scheduledExecutor = FaultTolerance.scheduledExecutor();
        private String name = "Retry-" + System.identityHashCode(this);
        private boolean cancelSource = true;

        private Builder() {
        }

        @Override
        public Retry build() {
            return new RetryImpl(this);
        }

        /**
         * Configure a retry policy to use to calculate delays between retries.
         * Defaults to a {@link io.helidon.faulttolerance.Retry.JitterRetryPolicy}
         * with 4 calls (initial call + 3 retries), delay of 200 millis and a jitter of 50 millis.
         *
         * @param policy retry policy
         * @return updated builder instance
         */
        @ConfiguredOption(provider = true)
        public Builder retryPolicy(RetryPolicy policy) {
            this.retryPolicy = policy;
            return this;
        }

        /**
         * These throwables will be considered failures, and all other will not.
         * <p>
         * Cannot be combined with {@link #skipOn}.
         *
         * @param classes to consider failures and trigger a retry
         * @return updated builder instance
         */
        @SafeVarargs
        public final Builder applyOn(Class<? extends Throwable>... classes) {
            applyOn.clear();
            Arrays.stream(classes)
                    .forEach(this::addApplyOn);

            return this;
        }

        /**
         * Add a throwable to be considered a failure.
         *
         * @param clazz to consider failure and trigger a retry
         * @return updated builder instance
         * @see #applyOn
         */
        public Builder addApplyOn(Class<? extends Throwable> clazz) {
            this.applyOn.add(clazz);
            return this;
        }

        /**
         * These throwables will not be considered retriable, all other will.
         * <p>
         * Cannot be combined with {@link #applyOn}.
         *
         * @param classes to skip retries
         * @return updated builder instance
         */
        @SafeVarargs
        public final Builder skipOn(Class<? extends Throwable>... classes) {
            skipOn.clear();
            Arrays.stream(classes)
                    .forEach(this::addSkipOn);

            return this;
        }

        /**
         * This throwable will not be considered retriable.
         * <p>
         *
         * @param clazz to to skip retries
         * @return updated builder instance
         */
        public Builder addSkipOn(Class<? extends Throwable> clazz) {
            this.skipOn.add(clazz);
            return this;
        }

        /**
         * Executor service to schedule retries.
         * By default uses an executor configured on
         * {@link io.helidon.faulttolerance.FaultTolerance#scheduledExecutor(java.util.function.Supplier)}.
         *
         * @param scheduledExecutor executor to use
         * @return updated builder instance
         */
        public Builder scheduledExecutor(ScheduledExecutorService scheduledExecutor) {
            this.scheduledExecutor = LazyValue.create(scheduledExecutor);
            return this;
        }

        /**
         * Overall timeout.
         * When overall timeout is reached, execution terminates (even if the retry policy
         * was not exhausted).
         *
         * @param overallTimeout an overall timeout
         * @return updated builder instance
         */
        @ConfiguredOption("PT1S")
        public Builder overallTimeout(Duration overallTimeout) {
            this.overallTimeout = overallTimeout;
            return this;
        }

        /**
         * A name assigned for debugging, error reporting or configuration purposes.
         *
         * @param name the name
         * @return updated builder instance
         */
        @ConfiguredOption("Retry-")
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Policy to cancel any source stage if the value return by {@link Retry#invoke}
         * is cancelled. Default is {@code true}; mostly used by FT MP to change default.
         *
         * @param cancelSource cancel source policy
         * @return updated builder instance
         */
        @ConfiguredOption("true")
        public Builder cancelSource(boolean cancelSource) {
            this.cancelSource = cancelSource;
            return this;
        }

        /**
         * <p>
         * Load all properties for this circuit breaker from configuration.
         * </p>
         * <table class="config">
         * <caption>Configuration</caption>
         * <tr>
         *     <th>key</th>
         *     <th>default value</th>
         *     <th>description</th>
         * </tr>
         * <tr>
         *     <td>overall-timeout</td>
         *     <td>1 second</td>
         *     <td>Timeout for overall execution</td>
         * </tr>
         * <tr>
         *     <td>name</td>
         *     <td>Retry-N</td>
         *     <td>Name used for debugging</td>
         * </tr>
         * <tr>
         *     <td>cancel-source</td>
         *     <td>true</td>
         *     <td>Cancel task source if task is cancelled</td>
         * </tr>
         * <tr>
         *     <td>delaying-retry-policy</td>
         *     <td>N/A</td>
         *     <td>A delaying retry policy</td>
         * </tr>
         * <tr>
         *     <td>jitter-retry-policy</td>
         *     <td>Policy of 4 calls, delay 200 millis and jitter of 50 millis</td>
         *     <td>A jitter retry policy</td>
         * </tr>
         * </table>
         *
         * @param config the config node to use
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("overall-timeout").as(Duration.class).ifPresent(this::overallTimeout);
            config.get("name").asString().ifPresent(this::name);
            config.get("cancel-source").asBoolean().ifPresent(this::cancelSource);
            boolean hasRetryPolicy = false;
            if (config.get("delaying-retry-policy").exists()) {
                hasRetryPolicy = true;
                retryPolicy(DelayingRetryPolicy.builder()
                        .config(config.get("delaying-retry-policy"))
                        .build());
            }
            if (config.get("jitter-retry-policy").exists()) {
                if (hasRetryPolicy) {
                    throw new IllegalArgumentException("Retry must not define multiple retry policies");
                }
                retryPolicy(JitterRetryPolicy.builder()
                        .config(config.get("jitter-retry-policy"))
                        .build());
            }
            return this;
        }

        Set<Class<? extends Throwable>> applyOn() {
            return applyOn;
        }

        Set<Class<? extends Throwable>> skipOn() {
            return skipOn;
        }

        RetryPolicy retryPolicy() {
            return retryPolicy;
        }

        Duration overallTimeout() {
            return overallTimeout;
        }

        LazyValue<? extends ScheduledExecutorService> scheduledExecutor() {
            return scheduledExecutor;
        }

        String name() {
            return name;
        }

        boolean cancelSource() {
            return cancelSource;
        }
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
         * A builder to customize configuration of {@link io.helidon.faulttolerance.Retry.DelayingRetryPolicy}.
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

        int calls() {
            return calls;
        }

        Duration delay() {
            return Duration.ofMillis(delayMillis);
        }

        double delayFactor() {
            return delayFactor;
        }

        @Override
        public Optional<Long> nextDelayMillis(long firstCallMillis, long lastDelay, int call) {
            if (call >= calls) {
                return Optional.empty();
            }

            if (call == 0) {
                return Optional.of(0L);
            } else if (call == 1) {
                return Optional.of(delayMillis);
            }

            return Optional.of((long) (lastDelay * delayFactor));
        }

        /**
         * Fluent API builder for {@link io.helidon.faulttolerance.Retry.DelayingRetryPolicy}.
         */
        @Configured(provides = RetryPolicy.class, description = "A retry policy that prolongs the delays between retries by "
                + "a defined factor.")
        public static class Builder implements io.helidon.common.Builder<Builder, DelayingRetryPolicy> {
            private int calls = 3;
            private double delayFactor = 2;
            private Duration delay = Duration.ofMillis(200);

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
            @ConfiguredOption("3")
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
            @ConfiguredOption("PT0.2S")
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
            @ConfiguredOption("2")
            public Builder delayFactor(double delayFactor) {
                this.delayFactor = delayFactor;
                return this;
            }

            /**
             * <p>
             * Load all properties for this circuit breaker from configuration.
             * </p>
             * <table class="config">
             * <caption>Configuration</caption>
             * <tr>
             *     <th>key</th>
             *     <th>default value</th>
             *     <th>description</th>
             * </tr>
             * <tr>
             *     <td>calls</td>
             *     <td>3</td>
             *     <td>Number of calls</td>
             * </tr>
             * <tr>
             *     <td>delay</td>
             *     <td>200 millis</td>
             *     <td>Delay to wait between retries</td>
             * </tr>
             * <tr>
             *     <td>delay-factor</td>
             *     <td>2</td>
             *     <td>A delay multiplication factor</td>
             * </tr>
             * </table>
             *
             * @param config the config node to use
             * @return updated builder instance
             */
            public Builder config(Config config) {
                config.get("calls").asInt().ifPresent(this::calls);
                config.get("delay").as(Duration.class).ifPresent(this::delay);
                config.get("delay-factor").asDouble().ifPresent(this::delayFactor);
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
         * A new fluent API builder to configure instances of {@link io.helidon.faulttolerance.Retry}.
         *
         * @return a new builder
         */
        public static Builder builder() {
            return new Builder();
        }

        int calls() {
            return calls;
        }

        Duration delay() {
            return Duration.ofMillis(delayMillis);
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
         * Fluent API builder for {@link io.helidon.faulttolerance.Retry.JitterRetryPolicy}.
         */
        @Configured(provides = RetryPolicy.class)
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
            @ConfiguredOption("3")
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
            @ConfiguredOption("PT0.2S")
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
            @ConfiguredOption("PT0.05S")
            public Builder jitter(Duration jitter) {
                this.jitter = jitter;
                return this;
            }

            /**
             * <p>
             * Load all properties for this circuit breaker from configuration.
             * </p>
             * <table class="config">
             * <caption>Configuration</caption>
             * <tr>
             *     <th>key</th>
             *     <th>default value</th>
             *     <th>description</th>
             * </tr>
             * <tr>
             *     <td>calls</td>
             *     <td>3</td>
             *     <td>Number of calls</td>
             * </tr>
             * <tr>
             *     <td>delay</td>
             *     <td>200 millis</td>
             *     <td>Delay to wait between retries</td>
             * </tr>
             * <tr>
             *     <td>jitter</td>
             *     <td>50 milliseconds</td>
             *     <td>A number between {@code [-jitter,+jitter]} applied to delay</td>
             * </tr>
             * </table>
             *
             * @param config the config node to use
             * @return updated builder instance
             */
            public Builder config(Config config) {
                config.get("calls").asInt().ifPresent(this::calls);
                config.get("delay").as(Duration.class).ifPresent(this::delay);
                config.get("delay-factor").as(Duration.class).ifPresent(this::jitter);
                return this;
            }
        }
    }

    /**
     * A retry policy that increases the delay time following the Fibonacci sequence.
     * Allowed elements that are also annotated with {@code @Retry}.
     * Expected sequence: initial delay, 2 * initial delay + jitter, 3 * initial delay + jitter,
     * 5 * initial delay + jitter, etc. {@code maxDelayInMillis} is used to prevent endless waiting.
     */
    class FibonacciRetryPolicy implements RetryPolicy {

        private final int calls;
        private final Duration initialDelay;
        private final Duration maxDelay;
        private final Supplier<Long> randomJitter;

        private long delayA;
        private long delayB;

        private FibonacciRetryPolicy(Builder builder) {
            calls = builder.calls;
            initialDelay = builder.initialDelay;
            maxDelay = builder.maxDelay;
            long jitter = builder.randomJitter;
            int jitterMillis = (int) Math.min(jitter, Integer.MAX_VALUE);
            if (jitterMillis == 0L) {
                randomJitter = () -> 0L;
            } else {
                Random random = new Random();
                // need a number [-jitterMillis,+jitterMillis]
                randomJitter = () -> Long.valueOf(random.nextInt(jitterMillis * 2) - jitterMillis);
            }
        }

        /**
         * A new fluent API builder to configure instances of {@link io.helidon.faulttolerance.Retry}.
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

            if (delayA == 0) {
                delayA = initialDelay.toMillis();
                return Optional.of(delayA + randomJitter.get());
            }

            if (delayB == 0) {
                delayB = initialDelay.toMillis() << 1;
                return Optional.of(delayB + randomJitter.get());
            }

            long delay = delayA + delayB;
            delayA = delayB;
            delayB = delay;

            if (delay >= maxDelay.toMillis()) {
                return Optional.empty();
            }

            return Optional.of(delay + randomJitter.get());
        }

        /**
         * Fluent API builder for {@link io.helidon.faulttolerance.Retry.FibonacciRetryPolicy}.
         */
        @Configured(provides = RetryPolicy.class)
        public static class Builder implements io.helidon.common.Builder<Builder, FibonacciRetryPolicy> {

            private int calls = 10;
            private Duration initialDelay = Duration.ofMillis(2);
            private Duration maxDelay = Duration.ofMillis(180_000);
            private long randomJitter = 50;

            @Override
            public FibonacciRetryPolicy build() {
                return new FibonacciRetryPolicy(this);
            }

            /**
             * Total number of calls (first + retries).
             *
             * @param calls how many times to call the method
             * @return updated builder instance
             */
            @ConfiguredOption("10")
            public Builder calls(int calls) {
                this.calls = calls;
                return this;
            }

            /**
             * Initial Delay in Milliseconds.
             *
             * @param initialDelay Duration
             * @return updated builder instance
             */
            @ConfiguredOption("0")
            public Builder initialDelay(Duration initialDelay) {
                this.initialDelay = initialDelay;
                return this;
            }

            /**
             * Max Delay in Milliseconds.
             *
             * @param maxDelay Duration
             * @return updated builder instance
             */
            @ConfiguredOption("180000")
            public Builder maxDelay(Duration maxDelay) {
                this.maxDelay = maxDelay;
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
            @ConfiguredOption("50")
            public Builder jitter(long jitter) {
                this.randomJitter = jitter;
                return this;
            }

            /**
             * <p>
             * Load all properties for this Retry Policy from configuration.
             * </p>
             * <table class="config">
             * <caption>Configuration</caption>
             * <tr>
             *     <th>key</th>
             *     <th>default value</th>
             *     <th>description</th>
             * </tr>
             * <tr>
             *     <td>calls</td>
             *     <td>10</td>
             *     <td>Number of calls</td>
             * </tr>
             * <tr>
             *     <td>initial-delay</td>
             *     <td>2</td>
             *     <td>Initial delay</td>
             * </tr>
             * <tr>
             *     <td>max-delay</td>
             *     <td>3 minutes</td>
             *     <td>Maximum delay</td>
             * </tr>
             * <tr>
             *     <td>jitter</td>
             *     <td>50 milliseconds</td>
             *     <td>A number between {@code [-jitter,+jitter]} applied to delay</td>
             * </tr>
             * </table>
             *
             * @param config the config node to use
             * @return updated builder instance
             */
            public Builder config(Config config) {
                config.get("calls").asInt().ifPresent(this::calls);
                config.get("initial-delay").as(Duration.class).ifPresent(this::initialDelay);
                config.get("max-delay").as(Duration.class).ifPresent(this::maxDelay);
                config.get("jitter").asLong().ifPresent(this::jitter);
                return this;
            }
        }
    }


    /**
     * A retry policy that increases the delay time following an exponential sequence.
     * Allowed elements that are also annotated with {@code @Retry}.
     * Expected sequence in case factor is 2: initial delay, 2 * initial delay + jitter, 4 * initial delay + jitter,
     * 8 * initial delay + jitter, etc. {@code maxDelay} is used to prevent endless waiting.
     */
    class ExponentialRetryPolicy implements RetryPolicy {

        private final int calls;
        private final Duration initialDelay;
        private final Duration maxDelay;
        private final int factor;
        private final Supplier<Long> jitter;

        private long accumulateDelay;


        private ExponentialRetryPolicy(Builder builder) {
            calls = builder.calls;
            initialDelay = builder.initialDelay;
            maxDelay = builder.maxDelay;
            factor = builder.factor;

            long jitter = builder.jitter;
            int jitterMillis = (jitter > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) jitter;
            if (jitterMillis == 0L) {
                this.jitter = () -> 0L;
            } else {
                Random random = new Random();
                // need a number [-jitterMillis,+jitterMillis]
                this.jitter = () -> Long.valueOf(random.nextInt((jitterMillis * 2) - jitterMillis));
            }
        }

        /**
         * A new fluent API builder to configure instances of {@link io.helidon.faulttolerance.Retry}.
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

            if (accumulateDelay == 0) {
                accumulateDelay = initialDelay.toMillis();
                return Optional.of(accumulateDelay + jitter.get());
            }

            accumulateDelay = accumulateDelay * factor;

            if (accumulateDelay >= maxDelay.toMillis()) {
                return Optional.empty();
            }

            return Optional.of(accumulateDelay + jitter.get());
        }

        /**
         * Fluent API builder for {@link io.helidon.faulttolerance.Retry.ExponentialRetryPolicy}.
         */
        @Configured(provides = RetryPolicy.class)
        public static class Builder implements io.helidon.common.Builder<Builder, ExponentialRetryPolicy> {

            private int calls = 10;
            private Duration initialDelay = Duration.ofMillis(2);
            private Duration maxDelay = Duration.ofMillis(180_000);
            private int factor = 2;
            private long jitter = 50;

            @Override
            public ExponentialRetryPolicy build() {
                return new ExponentialRetryPolicy(this);
            }

            /**
             * Total number of calls (first + retries).
             *
             * @param calls how many times to call the method
             * @return updated builder instance
             */
            @ConfiguredOption("10")
            public Builder calls(int calls) {
                this.calls = calls;
                return this;
            }

            /**
             * Initial Delay.
             *
             * @param initialDelay Duration
             * @return updated builder instance
             */
            @ConfiguredOption("2")
            public Builder initialDelay(Duration initialDelay) {
                this.initialDelay = initialDelay;
                return this;
            }

            /**
             * Max Delay in Milliseconds.
             *
             * @param maxDelay long
             * @return updated builder instance
             */
            @ConfiguredOption("180000")
            public Builder maxDelay(Duration maxDelay) {
                this.maxDelay = maxDelay;
                return this;
            }

            /**
             * Multiplication factor.
             *
             * @param factor multiplication factor
             * @return updated builder instance
             */
            @ConfiguredOption("2")
            public Builder factor(int factor) {
                this.factor = factor;
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
            @ConfiguredOption("50")
            public Builder jitter(long jitter) {
                this.jitter = jitter;
                return this;
            }


            /**
             * <p>
             * Load all properties for this Retry Policy from configuration.
             * </p>
             * <table class="config">
             * <caption>Configuration</caption>
             * <tr>
             *     <th>key</th>
             *     <th>default value</th>
             *     <th>description</th>
             * </tr>
             * <tr>
             *     <td>calls</td>
             *     <td>10</td>
             *     <td>Number of calls</td>
             * </tr>
             * <tr>
             *     <td>initial-delay</td>
             *     <td>2</td>
             *     <td>Initial delay</td>
             * </tr>
             * <tr>
             *     <td>max-delay</td>
             *     <td>3 minutes</td>
             *     <td>Maximum delay</td>
             * </tr>
             * <tr>
             *     <td>factor</td>
             *     <td>2</td>
             *     <td>Multiplication factor</td>
             * </tr>
             * <tr>
             *     <td>jitter</td>
             *     <td>50 milliseconds</td>
             *     <td>A number between {@code [-jitter,+jitter]} applied to delay</td>
             * </tr>
             * </table>
             *
             * @param config the config node to use
             * @return updated builder instance
             */
            public Builder config(Config config) {
                config.get("calls").asInt().ifPresent(this::calls);
                config.get("initial-delay").as(Duration.class).ifPresent(this::initialDelay);
                config.get("max-delay").as(Duration.class).ifPresent(this::maxDelay);
                config.get("factor").asInt().ifPresent(this::factor);
                config.get("jitter").asLong().ifPresent(this::jitter);
                return this;
            }
        }
    }


    /**
     * Number of times a method called has been retried. This is a monotonically
     * increasing counter over the lifetime of the handler.
     *
     * @return number ot times a method is retried.
     */
    long retryCounter();
}
