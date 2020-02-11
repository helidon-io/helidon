/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.config.retries;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigThreadFactory;
import io.helidon.config.spi.RetryPolicy;

import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A default retry policy implementation with {@link java.util.concurrent.ScheduledExecutorService}.
 * Following attributes can be configured:
 * <ul>
 * <li>number of retries (excluding the first invocation)</li>
 * <li>delay between the invocations</li>
 * <li>a delay multiplication factor</li>
 * <li>a timeout for the individual invocation</li>
 * <li>an overall timeout</li>
 * <li>an executor service</li>
 * </ul>
 */
public final class RetryingPolicy implements RetryPolicy {
    private static final Logger LOGGER = Logger.getLogger(RetryingPolicy.class.getName());

    private final int retries;
    private final Duration delay;
    private final double delayFactor;
    private final Duration callTimeout;
    private final Duration overallTimeout;
    private final ScheduledExecutorService executorService;

    private RetryingPolicy(Builder builder) {
        this.retries = builder.retries;
        this.delay = builder.delay;
        this.delayFactor = builder.delayFactor;
        this.callTimeout = builder.callTimeout;
        this.overallTimeout = builder.overallTimeout;
        this.executorService = builder.executorService.get();
    }

    public static RetryingPolicy create(int retries) {
        return builder().retries(retries).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <T> T execute(Supplier<T> call) throws ConfigException {
        Duration currentDelay = Duration.ZERO;
        long overallTimeoutsLeft = overallTimeout.toMillis();
        Throwable last = null;
        for (int i = 0; i <= retries; i++) {
            try {
                LOGGER.finest("next delay: " + currentDelay);
                overallTimeoutsLeft -= currentDelay.toMillis();
                if (overallTimeoutsLeft < 0) {
                    LOGGER.finest("overall timeout left [ms]: " + overallTimeoutsLeft);
                    throw new ConfigException(
                            "Cannot schedule the next call, the current delay would exceed the overall timeout.");
                }
                ScheduledFuture<T> localFuture = executorService.schedule(call::get, currentDelay.toMillis(), MILLISECONDS);
                return localFuture.get(min(currentDelay.plus(callTimeout).toMillis(), overallTimeoutsLeft), MILLISECONDS);
            } catch (ConfigException e) {
                throw e;
            } catch (CancellationException e) {
                throw new ConfigException("An invocation has been canceled.", e);
            } catch (InterruptedException e) {
                throw new ConfigException("An invocation has been interrupted.", e);
            } catch (TimeoutException e) {
                throw new ConfigException("A timeout has been reached.", e);
            } catch (Throwable t) {
                last = t;
            }
            currentDelay = nextDelay(i, currentDelay);
        }
        throw new ConfigException("All repeated calls failed.", last);
    }

    Duration nextDelay(int invocation, Duration currentDelay) {
        if (invocation == 0) {
            return delay;
        } else {
            return Duration.ofMillis((long) (currentDelay.toMillis() * delayFactor));
        }
    }

    /**
     * Number of retries.
     * @return retries
     */
    public int retries() {
        return retries;
    }

    /**
     * Delay between retries.
     *
     * @return delay
     */
    public Duration delay() {
        return delay;
    }

    /**
     * Delay multiplication factor.
     * @return delay factor
     */
    public double delayFactor() {
        return delayFactor;
    }

    /**
     * Timeout of the call.
     * @return call timeout
     */
    public Duration callTimeout() {
        return callTimeout;
    }

    /**
     * Overall timeout.
     * @return overall timeout
     */
    public Duration overallTimeout() {
        return overallTimeout;
    }

    public static final class Builder implements io.helidon.common.Builder<RetryingPolicy> {
        private int retries = 3;
        private Duration delay = Duration.ofMillis(200);
        private double delayFactor = 2;
        private Duration callTimeout = Duration.ofMillis(500);
        private Duration overallTimeout = Duration.ofSeconds(2);
        private Supplier<ScheduledExecutorService> executorService = () ->
                Executors.newSingleThreadScheduledExecutor(new ConfigThreadFactory("retry-policy"));

        private Builder() {
        }

        @Override
        public RetryingPolicy build() {
            return new RetryingPolicy(this);
        }

        /**
         * Initializes retry policy instance from configuration properties.
         * <p>
         * Optional {@code properties}:
         * <ul>
         * <li>{@code retries} - type {@code int}, see {@link #retries()}</li>
         * <li>{@code delay} - type {@link Duration}, see {@link #delay(Duration)}</li>
         * <li>{@code delay-factor} - type {@code double}, see {@link #delayFactor(double)}</li>
         * <li>{@code call-timeout} - type {@link Duration}, see {@link #callTimeout(Duration)}</li>
         * <li>{@code overall-timeout} - type {@link Duration}, see {@link #overallTimeout(Duration)}</li>
         * </ul>
         *
         * @param metaConfig meta-configuration used to initialize returned polling strategy
         * @return updated builder instance
         */
        public Builder config(Config metaConfig) {
            metaConfig.get("retries").asInt().ifPresent(this::retries);
            // delay
            metaConfig.get("delay").as(Duration.class)
                    .ifPresent(this::delay);
            // delay-factor
            metaConfig.get("delay-factor").asDouble()
                    .ifPresent(this::delayFactor);
            // call-timeout
            metaConfig.get("call-timeout").as(Duration.class)
                    .ifPresent(this::callTimeout);
            // overall-timeout
            metaConfig.get("overall-timeout").as(Duration.class)
                    .ifPresent(this::overallTimeout);

            return this;
        }

        /**
         * Configure number of retries.
         *
         * @param retries number of time the invocation will be attempted
         * @return updated builder instance
         */
        public Builder retries(int retries) {
            this.retries = retries;
            return this;
        }

        /**
         * Sets an initial delay between invocations, that is repeatedly multiplied by {@code delayFactor}.
         * <p>
         * The default value is 200ms.
         *
         * @param delay an overall timeout
         * @return a modified builder instance
         */
        public Builder delay(Duration delay) {
            this.delay = delay;
            return this;
        }

        /**
         * Sets a factor that prolongs the delay for an every new execute.
         * <p>
         * The default value is 2.
         *
         * @param delayFactor a delay prolonging factor
         * @return a modified builder instance
         */
        public Builder delayFactor(double delayFactor) {
            this.delayFactor = delayFactor;
            return this;
        }

        /**
         * Sets a limit for each invocation.
         * <p>
         * The default value is 500ms.
         *
         * @param callTimeout an invocation timeout - a limit per call
         * @return a modified builder instance
         */
        public Builder callTimeout(Duration callTimeout) {
            this.callTimeout = callTimeout;
            return this;
        }

        /**
         * Sets a overall limit for all invocation, including delays.
         * <p>
         * The default value is 2s.
         *
         * @param overallTimeout an overall timeout
         * @return a modified builder instance
         */
        public Builder overallTimeout(Duration overallTimeout) {
            this.overallTimeout = overallTimeout;
            return this;
        }

        /**
         * Sets a custom {@link ScheduledExecutorService executor} used to invoke a method call.
         * <p>
         * By default single-threaded executor is used.
         *
         * @param executorService the custom scheduled executor service
         * @return a modified builder instance
         */
        public Builder executorService(ScheduledExecutorService executorService) {
            this.executorService = () -> executorService;
            return this;
        }
    }
}
