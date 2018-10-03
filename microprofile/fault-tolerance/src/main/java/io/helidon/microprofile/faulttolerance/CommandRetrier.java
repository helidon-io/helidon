/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.faulttolerance;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import javax.interceptor.InvocationContext;

import com.netflix.hystrix.exception.HystrixRuntimeException;
import net.jodah.failsafe.AsyncFailsafe;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.SyncFailsafe;
import net.jodah.failsafe.function.CheckedFunction;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;

/**
 * Class CommandRetrier.
 */
public class CommandRetrier {
    private static final Logger LOGGER = Logger.getLogger(CommandRetrier.class.getName());

    private final InvocationContext context;

    private final RetryPolicy retryPolicy;

    private final boolean isAsynchronous;

    private final MethodIntrospector introspector;

    private final Method method;

    private boolean firstInvocation = true;

    private FaultToleranceCommand command;

    /**
     * Constructor.
     *
     * @param context The invocation context.
     * @param introspector The method introspector.
     */
    public CommandRetrier(InvocationContext context, MethodIntrospector introspector) {
        this.context = context;
        this.introspector = introspector;
        this.isAsynchronous = introspector.isAsynchronous();
        this.method = context.getMethod();

        final Retry retry = introspector.getRetry();
        if (retry != null) {
            // Initial setting for retry policy
            this.retryPolicy = new RetryPolicy()
                                   .withMaxRetries(retry.maxRetries())
                                   .withMaxDuration(retry.maxDuration(), TimeUtil.chronoUnitToTimeUnit(retry.durationUnit()))
                                   .retryOn(retry.retryOn());

            // Set abortOn if defined
            if (retry.abortOn().length > 0) {
                this.retryPolicy.abortOn(retry.abortOn());
            }

            // Processing for jitter and delay
            if (retry.jitter() > 0) {
                long delay = TimeUtil.convertToMillis(retry.delay(), retry.delayUnit());
                long jitter = TimeUtil.convertToMillis(retry.jitter(), retry.jitterDelayUnit());

                /*
                 * We need jitter <= delay so we compute factor for Failsafe so we split
                 * the difference, essentially making jitter and delay equal, and then set
                 * the factor to 1.0.
                 */
                double factor;
                if (jitter > delay) {
                    final long diff = jitter - delay;
                    delay = delay + diff / 2;
                    factor = 1.0;
                } else {
                    factor = ((double) jitter) / delay;
                }
                this.retryPolicy.withDelay(delay, TimeUnit.MILLISECONDS);
                this.retryPolicy.withJitter(factor);
            } else if (retry.delay() > 0) {
                this.retryPolicy.withDelay(retry.delay(), TimeUtil.chronoUnitToTimeUnit(retry.delayUnit()));
            }
        } else {
            this.retryPolicy = new RetryPolicy().withMaxRetries(0);     // no retries
        }
    }

    /**
     * Retries running a command according to retry policy.
     *
     * @return Object returned by command.
     * @throws Throwable If something fails.
     */
    @SuppressWarnings("unchecked")
    public Object execute() throws Throwable {
        LOGGER.fine("Executing command with isAsynchronous = " + isAsynchronous);
        final ScheduledExecutorService executor = CommandExecutor.getExecutorService();

        CheckedFunction fallbackFunction = t -> {
            final CommandFallback fallback = new CommandFallback(context, introspector);
            return fallback.execute();
        };

        if (isAsynchronous) {
            AsyncFailsafe<Object> failsafe = Failsafe.with(retryPolicy).with(executor);
            return introspector.hasFallback()
                   ? failsafe.withFallback(fallbackFunction).get(this::retryExecute)
                   : failsafe.get(this::retryExecute);
        } else {
            SyncFailsafe<Object> failsafe = Failsafe.with(retryPolicy);
            return introspector.hasFallback()
                   ? failsafe.withFallback(fallbackFunction).get(this::retryExecute)
                   : failsafe.get(this::retryExecute);
        }
    }

    /**
     * Creates a new command for each retry since Hystrix commands can only be
     * executed once. Fallback method is not overridden here to ensure all
     * retries are executed.
     *
     * @return Object returned by command.
     */
    private Object retryExecute() {
        final String commandKey = createCommandKey();
        command = new FaultToleranceCommand(commandKey, introspector, context);

        Object result;
        try {
            LOGGER.info("About to execute command with key " + commandKey);
            updateMetricsBefore();
            result = command.execute();
            updateMetricsAfter(null);
        } catch (HystrixRuntimeException e) {
            Throwable cause = e.getCause();
            updateMetricsAfter(cause);
            if (cause instanceof TimeoutException) {
                throw new org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException(cause);
            }
            if (cause instanceof RejectedExecutionException) {
                throw new BulkheadException(cause);
            }
            if (!(cause instanceof RuntimeException)) {
                cause = new RuntimeException(cause);
            }
            if (command.isCircuitBreakerOpen()) {
                throw new CircuitBreakerOpenException(cause);
            }
            throw (RuntimeException) cause;
        }
        return result;
    }

    /**
     * Update metrics before method is called.
     */
    private void updateMetricsBefore() {
        if (introspector.hasRetry() && !firstInvocation) {
            FaultToleranceMetrics.getCounter(method, FaultToleranceMetrics.RETRY_RETRIES_TOTAL).inc();
        }
    }

    /**
     * Update metrics after method is called and depending on outcome.
     *
     * @param cause Exception cause or {@code null} if execution successful.
     */
    private void updateMetricsAfter(Throwable cause) {
        // Global method counters
        FaultToleranceMetrics.getCounter(method, cause == null ? FaultToleranceMetrics.INVOCATIONS_TOTAL
                                                               : FaultToleranceMetrics.INVOCATIONS_FAILED_TOTAL).inc();

        // Retry counters
        if (introspector.hasRetry()) {
            if (cause == null) {
                FaultToleranceMetrics.getCounter(method, firstInvocation
                                   ? FaultToleranceMetrics.RETRY_CALLS_SUCCEEDED_NOT_RETRIED_TOTAL
                                   : FaultToleranceMetrics.RETRY_CALLS_SUCCEEDED_RETRIED_TOTAL).inc();
            } else if (!firstInvocation) {
                FaultToleranceMetrics.getCounter(method, FaultToleranceMetrics.RETRY_CALLS_FAILED_TOTAL).inc();
            }
        }

        // Timeout
        if (introspector.hasTimeout()) {
            FaultToleranceMetrics.getHistogram(method, FaultToleranceMetrics.TIMEOUT_EXECUTION_DURATION)
                                 .update(command.getExecutionTime());
            FaultToleranceMetrics.getCounter(method, cause instanceof TimeoutException
                               ? FaultToleranceMetrics.TIMEOUT_CALLS_TIMED_OUT_TOTAL
                               : FaultToleranceMetrics.TIMEOUT_CALLS_NOT_TIMED_OUT_TOTAL).inc();
        }

        // Bulkhead
        if (introspector.hasBulkhead()) {
            FaultToleranceMetrics.getHistogram(method, FaultToleranceMetrics.BULKHEAD_EXECUTION_DURATION)
                                 .update(command.getExecutionTime());
            FaultToleranceMetrics.getCounter(method, cause instanceof RejectedExecutionException
                               ? FaultToleranceMetrics.BULKHEAD_CALLS_REJECTED_TOTAL
                               : FaultToleranceMetrics.BULKHEAD_CALLS_ACCEPTED_TOTAL).inc();
        }

        // Update firstInvocation flag
        firstInvocation = false;
    }

    /**
     * Returns a key for a command. Keys are specific to the pair of instance (target)
     * and the method being called.
     *
     * @return A command key.
     */
    private String createCommandKey() {
        return method.getName() + Objects.hash(context.getTarget(), context.getMethod().hashCode());
    }
}
