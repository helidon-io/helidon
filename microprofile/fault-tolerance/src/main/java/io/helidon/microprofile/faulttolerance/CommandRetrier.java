/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.interceptor.InvocationContext;

import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.FailsafeExecutor;
import net.jodah.failsafe.Fallback;
import net.jodah.failsafe.Policy;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import net.jodah.failsafe.function.CheckedFunction;
import net.jodah.failsafe.util.concurrent.Scheduler;
import org.apache.commons.configuration.AbstractConfiguration;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
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

    private static final long   DEFAULT_DELAY_CORRECTION = 250L;
    private static final String FT_DELAY_CORRECTION = "fault-tolerance.delayCorrection";
    private static final int    DEFAULT_COMMAND_THREAD_POOL_SIZE = 8;
    private static final String FT_COMMAND_THREAD_POOL_SIZE = "fault-tolerance.commandThreadPoolSize";
    private static final long   DEFAULT_THREAD_WAITING_PERIOD = 2000L;
    private static final String FT_THREAD_WAITING_PERIOD = "fault-tolerance.threadWaitingPeriod";
    private static final long   DEFAULT_BULKHEAD_TASK_QUEUEING_PERIOD = 2000L;
    private static final String FT_BULKHEAD_TASK_QUEUEING_PERIOD = "fault-tolerance.bulkheadTaskQueueingPeriod";

    private final InvocationContext context;

    private final RetryPolicy<Object> retryPolicy;

    private final boolean isAsynchronous;

    private final MethodIntrospector introspector;

    private final Method method;

    private int invocationCount = 0;

    private FaultToleranceCommand command;

    private ClassLoader contextClassLoader;

    private final long delayCorrection;

    private final int commandThreadPoolSize;

    private final long threadWaitingPeriod;

    private final long bulkheadTaskQueueingPeriod;

    private CompletableFuture<?> taskQueued = new CompletableFuture<>();

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

        // Init Helidon config params
        Config config = ConfigProvider.getConfig();
        this.delayCorrection = config.getOptionalValue(FT_DELAY_CORRECTION, Long.class)
                .orElse(DEFAULT_DELAY_CORRECTION);
        this.commandThreadPoolSize = config.getOptionalValue(FT_COMMAND_THREAD_POOL_SIZE, Integer.class)
                .orElse(DEFAULT_COMMAND_THREAD_POOL_SIZE);
        this.threadWaitingPeriod = config.getOptionalValue(FT_THREAD_WAITING_PERIOD, Long.class)
                .orElse(DEFAULT_THREAD_WAITING_PERIOD);
        this.bulkheadTaskQueueingPeriod = config.getOptionalValue(FT_BULKHEAD_TASK_QUEUEING_PERIOD, Long.class)
                .orElse(DEFAULT_BULKHEAD_TASK_QUEUEING_PERIOD);

        final Retry retry = introspector.getRetry();
        if (retry != null) {
            // Initial setting for retry policy
            this.retryPolicy = new RetryPolicy<>()
                                   .withMaxRetries(retry.maxRetries())
                                   .withMaxDuration(Duration.of(retry.maxDuration(), retry.durationUnit()));
            this.retryPolicy.handle(retry.retryOn());

            // Set abortOn if defined
            if (retry.abortOn().length > 0) {
                this.retryPolicy.abortOn(retry.abortOn());
            }

            // Get delay and convert to nanos
            long delay = TimeUtil.convertToNanos(retry.delay(), retry.delayUnit());

            /*
             * Apply delay correction to account for time spent in our code. This
             * correction is necessary if user code measures intervals between
             * calls that include time spent in Helidon. See TCK test {@link
             * RetryTest#testRetryWithNoDelayAndJitter}. Failures may still occur
             * on heavily loaded systems.
             */
            Function<Long, Long> correction =
                    d -> Math.abs(d - TimeUtil.convertToNanos(delayCorrection, ChronoUnit.MILLIS));

            // Processing for jitter and delay
            if (retry.jitter() > 0) {
                long jitter = TimeUtil.convertToNanos(retry.jitter(), retry.jitterDelayUnit());

                // Need to compute a factor and adjust delay for Failsafe
                double factor;
                if (jitter > delay) {
                    final long diff = jitter - delay;
                    delay = delay + diff / 2;
                    factor = 1.0;
                } else {
                    factor = ((double) jitter) / delay;
                }
                this.retryPolicy.withDelay(Duration.of(correction.apply(delay), ChronoUnit.NANOS));
                this.retryPolicy.withJitter(factor);
            } else if (retry.delay() > 0) {
                this.retryPolicy.withDelay(Duration.of(correction.apply(delay), ChronoUnit.NANOS));
            }
        } else {
            this.retryPolicy = new RetryPolicy<>().withMaxRetries(0);     // no retries
        }
    }

    /**
     * Get command thread pool size.
     *
     * @return Thread pool size.
     */
    int commandThreadPoolSize() {
        return commandThreadPoolSize;
    }

    /**
     * Get thread waiting period.
     *
     * @return Thread waiting period.
     */
    long threadWaitingPeriod() {
        return threadWaitingPeriod;
    }

    FaultToleranceCommand getCommand() {
        return command;
    }

    /**
     * Retries running a command according to retry policy.
     *
     * @return Object returned by command.
     * @throws Exception If something goes wrong.
     */
    public Object execute() throws Exception {
        LOGGER.fine(() -> "Executing command with isAsynchronous = " + isAsynchronous);

        FailsafeExecutor<Object> failsafe = prepareFailsafeExecutor();

        try {
            if (isAsynchronous) {
                Scheduler scheduler = CommandScheduler.create(commandThreadPoolSize);
                failsafe = failsafe.with(scheduler);

                // Store context class loader to access config
                contextClassLoader = Thread.currentThread().getContextClassLoader();

                // Check CompletionStage first to process CompletableFuture here
                if (introspector.isReturnType(CompletionStage.class)) {
                    CompletionStage<?> completionStage = CommandCompletableFuture.create(
                            failsafe.getStageAsync(() -> (CompletionStage<?>) retryExecute()),
                            this::getCommand);
                    awaitBulkheadAsyncTaskQueued();
                    return completionStage;
                }

                // If not, it must be a subtype of Future
                if (introspector.isReturnType(Future.class)) {
                    Future<?> future = CommandCompletableFuture.create(
                            failsafe.getAsync(() -> (Future<?>) retryExecute()),
                            this::getCommand);
                    awaitBulkheadAsyncTaskQueued();
                    return future;
                }

                // Oops, something went wrong during validation
                throw new InternalError("Validation failed, return type must be Future or CompletionStage");
            } else {
                return failsafe.get(this::retryExecute);
            }
        } catch (FailsafeException e) {
            throw toException(e.getCause());
        }
    }

    /**
     * Set up the Failsafe executor. Add any fallback first, per Failsafe doc
     * about "typical" policy composition
     *
     * @return Failsafe executor.
     */
    private FailsafeExecutor<Object> prepareFailsafeExecutor() {
        List<Policy<Object>> policies = new ArrayList<>();
        if (introspector.hasFallback()) {
            CheckedFunction<ExecutionAttemptedEvent<?>, ?> fallbackFunction = event -> {
                final CommandFallback fallback = new CommandFallback(context, introspector, event.getLastFailure());
                Object result = fallback.execute();
                if (result instanceof CompletionStage<?>) {
                    result = ((CompletionStage<?>) result).toCompletableFuture();
                }
                if (result instanceof Future<?>) {
                    result = ((Future<?>) result).get();
                }
                return result;
            };
            policies.add(Fallback.of(fallbackFunction));
        }
        policies.add(retryPolicy);
        return Failsafe.with(policies);
    }

    /**
     * Creates a new command for each retry since Hystrix commands can only be
     * executed once. Fallback method is not overridden here to ensure all
     * retries are executed. If running in async mode, this method will execute
     * on a different thread.
     *
     * @return Object returned by command.
     */
    private Object retryExecute() throws Exception {
        // Config requires use of appropriate context class loader
        if (contextClassLoader != null) {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }

        final String commandKey = createCommandKey();
        command = new FaultToleranceCommand(this, commandKey, introspector, context,
                contextClassLoader, taskQueued);

        // Configure command before execution
        introspector.getHystrixProperties()
                .entrySet()
                .forEach(entry -> setProperty(commandKey, entry.getKey(), entry.getValue()));

        Object result;
        try {
            LOGGER.fine(() -> "About to execute command with key "
                    + command.getCommandKey()
                    + " on thread " + Thread.currentThread().getName());

            // Execute task
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
            if (isBulkheadRejection(cause)) {
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
     * A task can be queued on a bulkhead. When async and bulkheads are combined,
     * we need to ensure that they get queued in the correct order before
     * returning control back to the application. An exception thrown during
     * queueing is processed in {@link FaultToleranceCommand#execute()}.
     */
    private void awaitBulkheadAsyncTaskQueued() {
        if (introspector.hasBulkhead()) {
            try {
                taskQueued.get(bulkheadTaskQueueingPeriod, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                LOGGER.info(() -> "Bulkhead async task queueing exception " + e);
            }
        }
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
            boolean bulkheadRejection = isBulkheadRejection(cause);
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
     * Sets a Hystrix property on a command.
     *
     * @param commandKey Command key.
     * @param key Property key.
     * @param value Property value.
     */
    private void setProperty(String commandKey, String key, Object value) {
        final String actualKey = String.format("hystrix.command.%s.%s", commandKey, key);
        synchronized (ConfigurationManager.getConfigInstance()) {
            final AbstractConfiguration configManager = ConfigurationManager.getConfigInstance();
            if (configManager.getProperty(actualKey) == null) {
                configManager.setProperty(actualKey, value);
            }
        }
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

    /**
     * Checks if the parameter is a bulkhead exception. Note that Hystrix with semaphore
     * isolation may throw a {@code RuntimeException}, so we need to check the message
     * to determine if it is a semaphore exception.
     *
     * @param t Throwable to check.
     * @return Outcome of test.
     */
    private static boolean isBulkheadRejection(Throwable t) {
        return t instanceof RejectedExecutionException
                || t instanceof RuntimeException && t.getMessage().contains("could "
                + "not acquire a semaphore for execution");
    }
}
