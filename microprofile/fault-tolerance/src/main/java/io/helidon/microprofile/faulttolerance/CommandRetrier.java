/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import javax.interceptor.InvocationContext;

import com.netflix.hystrix.exception.HystrixRuntimeException;
import net.jodah.failsafe.AsyncFailsafe;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.FailsafeFuture;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.SyncFailsafe;
import net.jodah.failsafe.function.CheckedFunction;
import net.jodah.failsafe.util.concurrent.Scheduler;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;

import static io.helidon.microprofile.faulttolerance.ExceptionUtil.toException;
import static io.helidon.microprofile.faulttolerance.FaultToleranceExtension.isFaultToleranceMetricsEnabled;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BULKHEAD_CALLS_ACCEPTED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BULKHEAD_CALLS_REJECTED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BULKHEAD_EXECUTION_DURATION;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.INVOCATIONS_FAILED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.INVOCATIONS_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.RETRY_CALLS_FAILED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.RETRY_CALLS_SUCCEEDED_NOT_RETRIED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.RETRY_CALLS_SUCCEEDED_RETRIED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.RETRY_RETRIES_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.TIMEOUT_CALLS_NOT_TIMED_OUT_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.TIMEOUT_CALLS_TIMED_OUT_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.TIMEOUT_EXECUTION_DURATION;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.getCounter;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.getHistogram;

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

    private int invocationCount = 0;

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
                long delay = TimeUtil.convertToNanos(retry.delay(), retry.delayUnit());
                long jitter = TimeUtil.convertToNanos(retry.jitter(), retry.jitterDelayUnit());

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
                this.retryPolicy.withDelay(delay, TimeUnit.NANOSECONDS);
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
     * @throws Exception If something goes wrong.
     */
    @SuppressWarnings("unchecked")
    public Object execute() throws Exception {
        LOGGER.fine("Executing command with isAsynchronous = " + isAsynchronous);

        CheckedFunction<? extends Throwable, ?> fallbackFunction = t -> {
            final CommandFallback fallback = new CommandFallback(context, introspector, t);
            return fallback.execute();
        };

        try {
            if (isAsynchronous) {
                Scheduler scheduler = CommandScheduler.create();
                AsyncFailsafe<Object> failsafe = Failsafe.with(retryPolicy).with(scheduler);

                // Check CompletionStage first to process CompletableFuture here
                if (introspector.isReturnType(CompletionStage.class)) {
                    CompletionStage<?> completionStage = (introspector.hasFallback()
                            ? failsafe.withFallback(fallbackFunction)
                            .future(() -> (CompletionStage<?>) retryExecute())
                            : failsafe.future(() -> (CompletionStage<?>) retryExecute()));
                    return completionStage;
                }

                // If not, it must be a subtype of Future
                if (introspector.isReturnType(Future.class)) {
                    FailsafeFuture<?> chainedFuture = (introspector.hasFallback()
                            ? failsafe.withFallback(fallbackFunction).get(this::retryExecute)
                            : failsafe.get(this::retryExecute));
                    return new FailsafeChainedFuture<>(chainedFuture);
                }

                // Oops, something went wrong during validation
                throw new InternalError("Validation failed, return type must be Future or CompletionStage");
            } else {
                SyncFailsafe<Object> failsafe = Failsafe.with(retryPolicy);
                return introspector.hasFallback()
                        ? failsafe.withFallback(fallbackFunction).get(this::retryExecute)
                        : failsafe.get(this::retryExecute);
            }
        } catch (FailsafeException e) {
            throw toException(e.getCause());
        }
    }

    /**
     * Creates a new command for each retry since Hystrix commands can only be
     * executed once. Fallback method is not overridden here to ensure all
     * retries are executed.
     *
     * @return Object returned by command.
     */
    private Object retryExecute() throws Exception {
        final String commandKey = createCommandKey();
        command = new FaultToleranceCommand(commandKey, introspector, context);

        Object result;
        try {
            LOGGER.info("About to execute command with key " + command.getCommandKey());
            invocationCount++;
            updateMetricsBefore();
            result = command.execute();
            updateMetricsAfter(null);
        } catch (ExceptionUtil.WrappedException e) {
            Throwable cause = e.getCause();
            if (cause instanceof HystrixRuntimeException) {
                cause = cause.getCause();
            }

            updateMetricsAfter(cause);

            if (cause instanceof TimeoutException) {
                throw new org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException(cause);
            }
            if (cause instanceof RejectedExecutionException) {
                throw new BulkheadException(cause);
            }
            if (isHystrixBreakerException(cause)) {
                throw new CircuitBreakerOpenException(cause);
            }
            throw toException(cause);
        }
        return result;
    }

    /**
     * Update metrics before method is called.
     */
    private void updateMetricsBefore() {
        if (isFaultToleranceMetricsEnabled()) {
            if (introspector.hasRetry() && invocationCount > 1) {
                getCounter(method, RETRY_RETRIES_TOTAL).inc();
            }
        }
    }

    /**
     * Update metrics after method is called and depending on outcome.
     *
     * @param cause Exception cause or {@code null} if execution successful.
     */
    private void updateMetricsAfter(Throwable cause) {
        if (!isFaultToleranceMetricsEnabled()) {
            return;
        }

        // Special logic for methods with retries
        if (introspector.hasRetry()) {
            final Retry retry = introspector.getRetry();
            boolean firstInvocation = (invocationCount == 1);

            if (cause == null) {
                getCounter(method, INVOCATIONS_TOTAL).inc();
                getCounter(method, firstInvocation
                                   ? RETRY_CALLS_SUCCEEDED_NOT_RETRIED_TOTAL
                                   : RETRY_CALLS_SUCCEEDED_RETRIED_TOTAL).inc();
            } else if (retry.maxRetries() == invocationCount - 1) {
                getCounter(method, RETRY_CALLS_FAILED_TOTAL).inc();
                getCounter(method, INVOCATIONS_FAILED_TOTAL).inc();
                getCounter(method, INVOCATIONS_TOTAL).inc();
            }
        } else {
            // Global method counters
            getCounter(method, INVOCATIONS_TOTAL).inc();
            if (cause != null) {
                getCounter(method, INVOCATIONS_FAILED_TOTAL).inc();
            }
        }

        // Timeout
        if (introspector.hasTimeout()) {
            getHistogram(method, TIMEOUT_EXECUTION_DURATION)
                                 .update(command.getExecutionTime());
            getCounter(method, cause instanceof TimeoutException
                               ? TIMEOUT_CALLS_TIMED_OUT_TOTAL
                               : TIMEOUT_CALLS_NOT_TIMED_OUT_TOTAL).inc();
        }

        // Bulkhead
        if (introspector.hasBulkhead()) {
            boolean bulkheadRejection = (cause instanceof RejectedExecutionException);
            if (!bulkheadRejection) {
                getHistogram(method, BULKHEAD_EXECUTION_DURATION).update(command.getExecutionTime());
            }
            getCounter(method, bulkheadRejection ? BULKHEAD_CALLS_REJECTED_TOTAL
                    : BULKHEAD_CALLS_ACCEPTED_TOTAL).inc();
        }
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

    /**
     * Hystrix throws a {@code RuntimeException}, so we need to check
     * the message to determine if it is a breaker exception.
     *
     * @param t Throwable to check.
     * @return Outcome of test.
     */
    private static boolean isHystrixBreakerException(Throwable t) {
        return t instanceof RuntimeException && t.getMessage().contains("Hystrix "
                + "circuit short-circuited and is OPEN");
    }
}
