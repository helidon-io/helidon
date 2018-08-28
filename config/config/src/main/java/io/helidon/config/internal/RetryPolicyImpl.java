/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.internal;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Logger;

import io.helidon.config.ConfigException;
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
public class RetryPolicyImpl implements RetryPolicy {

    private static final Logger LOGGER = Logger.getLogger(RetryPolicyImpl.class.getName());

    private final int retries;
    private final Duration delay;
    private final double delayFactor;
    private final Duration callTimeout;
    private final Duration overallTimeout;
    private final ScheduledExecutorService executorService;
    private volatile ScheduledFuture future;

    /**
     * Initialize retry policy.
     *
     * @param retries         number of retries (excluding the first invocation)
     * @param delay           delay between the invocations
     * @param delayFactor     a delay multiplication factor
     * @param callTimeout     a timeout for the individual invocation
     * @param overallTimeout  an overall timeout
     * @param executorService an executor service
     */
    public RetryPolicyImpl(int retries,
                           Duration delay,
                           double delayFactor,
                           Duration callTimeout,
                           Duration overallTimeout,
                           ScheduledExecutorService executorService) {
        this.retries = retries;
        this.delay = delay;
        this.delayFactor = delayFactor;
        this.callTimeout = callTimeout;
        this.overallTimeout = overallTimeout;
        this.executorService = executorService;
    }

    @Override
    public <T> T execute(Supplier<T> call) throws ConfigException {

        Duration currentDelay = Duration.ZERO;
        long overallTimeoutsLeft = overallTimeout.toMillis();
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
            } catch (Throwable t) {
                if (t instanceof CancellationException) {
                    throw new ConfigException("An invocation has been canceled.", t);
                }
                if (t instanceof InterruptedException) {
                    throw new ConfigException("An invocation has been interrupted.", t);
                }
                if (t instanceof TimeoutException) {
                    throw new ConfigException("A timeout has been reached.", t);
                }
            }
            currentDelay = nextDelay(i, currentDelay);
        }
        throw new ConfigException("All repeated calls failed.");
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

    public int getRetries() {
        return retries;
    }

    public Duration getDelay() {
        return delay;
    }

    public double getDelayFactor() {
        return delayFactor;
    }

    public Duration getCallTimeout() {
        return callTimeout;
    }

    public Duration getOverallTimeout() {
        return overallTimeout;
    }
}
