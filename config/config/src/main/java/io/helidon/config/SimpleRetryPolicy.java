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

package io.helidon.config;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Logger;

import io.helidon.config.spi.RetryPolicy;

import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A default retry policy implementation with {@link ScheduledExecutorService}.
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
public final class SimpleRetryPolicy implements RetryPolicy {

    private static final Logger LOGGER = Logger.getLogger(SimpleRetryPolicy.class.getName());

    private final int retries;
    private final Duration delay;
    private final double delayFactor;
    private final Duration callTimeout;
    private final Duration overallTimeout;
    private final ScheduledExecutorService executorService;
    private volatile ScheduledFuture<?> future;

    private SimpleRetryPolicy(Builder builder) {
        this.retries = builder.retries;
        this.delay = builder.delay;
        this.delayFactor = builder.delayFactor;
        this.callTimeout = builder.callTimeout;
        this.overallTimeout = builder.overallTimeout;
        this.executorService = builder.executorService;
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
                future = localFuture;
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

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (future != null) {
            if (!future.isDone() && !future.isCancelled()) {
                return future.cancel(mayInterruptIfRunning);
            }
        }
        return false;
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

    public static final class Builder implements io.helidon.common.Builder<SimpleRetryPolicy> {
        private int retries;
        private Duration delay;
        private double delayFactor;
        private Duration callTimeout;
        private Duration overallTimeout;
        private ScheduledExecutorService executorService;

        @Override
        public SimpleRetryPolicy build() {
            return new SimpleRetryPolicy(this);
        }

        /**
         * Number of retries (excluding the first invocation).
         *
         * @param retries how many times to retry
         * @return updated builder instance
         */
        public Builder retries(int retries) {
            this.retries = retries;
            return this;
        }

        /**
         * Delay between the invocations.
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
         * Timeout for the individual invocation.
         *
         * @param callTimeout a timeout for the individual invocation
         * @return updated builder instance
         */
        public Builder callTimeout(Duration callTimeout) {
            this.callTimeout = callTimeout;
            return this;
        }

        /**
         * Overall timeout.
         *
         * @param overallTimeout an overall timeout
         * @return updated builder instance
         */
        public Builder overallTimeout(Duration overallTimeout) {
            this.overallTimeout = overallTimeout;
            return this;
        }

        /**
         * An executor service to schedule retries and run timed operations on.
         *
         * @param executorService service
         * @return updated builder instance
         */
        public Builder executorService(ScheduledExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }
    }
}
