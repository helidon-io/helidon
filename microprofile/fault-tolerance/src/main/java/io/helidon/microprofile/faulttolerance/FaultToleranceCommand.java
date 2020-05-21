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
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import javax.interceptor.InvocationContext;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import org.eclipse.microprofile.metrics.Histogram;

import static com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE;
import static com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy.THREAD;
import static io.helidon.microprofile.faulttolerance.CircuitBreakerHelper.State;
import static io.helidon.microprofile.faulttolerance.FaultToleranceExtension.isFaultToleranceMetricsEnabled;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_CALLS_FAILED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_CALLS_PREVENTED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_CALLS_SUCCEEDED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_CLOSED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_HALF_OPEN_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_OPENED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_OPEN_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BULKHEAD_CONCURRENT_EXECUTIONS;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BULKHEAD_WAITING_DURATION;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BULKHEAD_WAITING_QUEUE_POPULATION;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.METRIC_NAME_TEMPLATE;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.getCounter;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.getHistogram;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.registerGauge;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.registerHistogram;

/**
 * Class FaultToleranceCommand.
 */
public class FaultToleranceCommand extends HystrixCommand<Object> {
    private static final Logger LOGGER = Logger.getLogger(FaultToleranceCommand.class.getName());

    static final String HELIDON_MICROPROFILE_FAULTTOLERANCE = "io.helidon.microprofile.faulttolerance";

    private final String commandKey;

    private final MethodIntrospector introspector;

    private final InvocationContext context;

    private long executionTime = -1L;

    private CircuitBreakerHelper breakerHelper;

    private BulkheadHelper bulkheadHelper;

    private long queuedNanos = -1L;

    private Thread runThread;

    private ClassLoader contextClassLoader;

    private final long threadWaitingPeriod;

    /**
     * Helidon context in which to run business method.
     */
    private Context helidonContext;

    private CompletableFuture<?> taskQueued;

    /**
     * Constructor. Specify a thread pool key if a {@code @Bulkhead} is specified
     * on the method. A unique thread pool key will enable setting limits for this
     * command only based on the {@code Bulkhead} properties.
     *
     * @param commandRetrier The command retrier associated with this command.
     * @param commandKey The command key.
     * @param introspector The method introspector.
     * @param context CDI invocation context.
     * @param contextClassLoader Context class loader or {@code null} if not available.
     * @param taskQueued Future completed when task has been queued.
     */
    public FaultToleranceCommand(CommandRetrier commandRetrier, String commandKey,
                                 MethodIntrospector introspector,
                                 InvocationContext context, ClassLoader contextClassLoader,
                                 CompletableFuture<?> taskQueued) {
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(HELIDON_MICROPROFILE_FAULTTOLERANCE))
                .andCommandKey(
                        HystrixCommandKey.Factory.asKey(commandKey))
                .andCommandPropertiesDefaults(
                        HystrixCommandProperties.Setter()
                                .withFallbackEnabled(false)
                                .withExecutionIsolationStrategy(introspector.hasBulkhead()
                                        && !introspector.isAsynchronous() ? SEMAPHORE : THREAD)
                                .withExecutionIsolationThreadInterruptOnFutureCancel(true)
                                .withExecutionIsolationThreadInterruptOnTimeout(true)
                                .withExecutionTimeoutEnabled(false))
                .andThreadPoolKey(
                        introspector.hasBulkhead()
                                ? HystrixThreadPoolKey.Factory.asKey(commandKey)
                                : null)
                .andThreadPoolPropertiesDefaults(
                        HystrixThreadPoolProperties.Setter()
                                .withCoreSize(
                                        introspector.hasBulkhead()
                                                ? introspector.getBulkhead().value()
                                                : commandRetrier.commandThreadPoolSize())
                                .withMaximumSize(
                                        introspector.hasBulkhead()
                                                ? introspector.getBulkhead().value()
                                                : commandRetrier.commandThreadPoolSize())
                                .withMaxQueueSize(
                                        introspector.hasBulkhead() && introspector.isAsynchronous()
                                                ? introspector.getBulkhead().waitingTaskQueue()
                                                : -1)
                                .withQueueSizeRejectionThreshold(
                                        introspector.hasBulkhead() && introspector.isAsynchronous()
                                                ? introspector.getBulkhead().waitingTaskQueue()
                                                : -1)));
        this.commandKey = commandKey;
        this.introspector = introspector;
        this.context = context;
        this.contextClassLoader = contextClassLoader;
        this.threadWaitingPeriod = commandRetrier.threadWaitingPeriod();
        this.taskQueued = taskQueued;

        // Special initialization for methods with breakers
        if (introspector.hasCircuitBreaker()) {
            this.breakerHelper = new CircuitBreakerHelper(this, introspector.getCircuitBreaker());

            // Register gauges for this method
            if (isFaultToleranceMetricsEnabled()) {
                registerGauge(introspector.getMethod(),
                        BREAKER_OPEN_TOTAL,
                        "Amount of time the circuit breaker has spent in open state",
                        () -> breakerHelper.getInStateNanos(State.OPEN_MP));
                registerGauge(introspector.getMethod(),
                        BREAKER_HALF_OPEN_TOTAL,
                        "Amount of time the circuit breaker has spent in half-open state",
                        () -> breakerHelper.getInStateNanos(State.HALF_OPEN_MP));
                registerGauge(introspector.getMethod(),
                        BREAKER_CLOSED_TOTAL,
                        "Amount of time the circuit breaker has spent in closed state",
                        () -> breakerHelper.getInStateNanos(State.CLOSED_MP));
            }
        }

        if (introspector.hasBulkhead()) {
            bulkheadHelper = new BulkheadHelper(commandKey, introspector.getBulkhead());

            if (isFaultToleranceMetricsEnabled()) {
                // Record nanos to update metrics later
                queuedNanos = System.nanoTime();

                // Register gauges for this method
                registerGauge(introspector.getMethod(),
                        BULKHEAD_CONCURRENT_EXECUTIONS,
                        "Number of currently running executions",
                        () -> (long) bulkheadHelper.runningInvocations());
                if (introspector.isAsynchronous()) {
                    registerGauge(introspector.getMethod(),
                            BULKHEAD_WAITING_QUEUE_POPULATION,
                            "Number of executions currently waiting in the queue",
                            () -> (long) bulkheadHelper.waitingInvocations());
                }
            }
        }
    }

    /**
     * Get command's execution time in nanos.
     *
     * @return Execution time in nanos.
     * @throws IllegalStateException If called before command is executed.
     */
    long getExecutionTime() {
        if (executionTime == -1L) {
            throw new IllegalStateException("Command has not been executed yet");
        }
        return executionTime;
    }

    BulkheadHelper getBulkheadHelper() {
        return bulkheadHelper;
    }

    /**
     * Code to run as part of this command. Called from superclass.
     *
     * @return Result of command.
     * @throws Exception If an error occurs.
     */
    @Override
    public Object run() throws Exception {
        // Config requires use of appropriate context class loader
        if (contextClassLoader != null) {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }

        if (introspector.hasBulkhead()) {
            bulkheadHelper.markAsRunning(this);

            if (isFaultToleranceMetricsEnabled()) {
                // Register and update waiting time histogram
                if (introspector.isAsynchronous() && queuedNanos != -1L) {
                    Method method = introspector.getMethod();
                    Histogram histogram = getHistogram(method, BULKHEAD_WAITING_DURATION);
                    if (histogram == null) {
                        registerHistogram(
                                String.format(METRIC_NAME_TEMPLATE,
                                        method.getDeclaringClass().getName(),
                                        method.getName(),
                                        BULKHEAD_WAITING_DURATION),
                                "Histogram of the time executions spend waiting in the queue");
                        histogram = getHistogram(method, BULKHEAD_WAITING_DURATION);
                    }
                    histogram.update(System.nanoTime() - queuedNanos);
                }
            }
        }

        // Finally, invoke the user method
        try {
            runThread = Thread.currentThread();
            return Contexts.runInContextWithThrow(helidonContext, context::proceed);
        } finally {
            if (introspector.hasBulkhead()) {
                bulkheadHelper.markAsNotRunning(this);
            }
        }
    }

    /**
     * Executes this command returning a result or throwing an exception.
     *
     * @return The result.
     * @throws RuntimeException If something goes wrong.
     */
    @Override
    public Object execute() {
        this.helidonContext = Contexts.context().orElseGet(Context::create);
        boolean lockRemoved = false;

        // Get lock and check breaker delay
        if (introspector.hasCircuitBreaker()) {
            try {
                breakerHelper.lock();
                // OPEN_MP -> HALF_OPEN_MP
                if (breakerHelper.getState() == State.OPEN_MP) {
                    long delayNanos = TimeUtil.convertToNanos(introspector.getCircuitBreaker().delay(),
                            introspector.getCircuitBreaker().delayUnit());
                    if (breakerHelper.getCurrentStateNanos() > delayNanos) {
                        breakerHelper.setState(State.HALF_OPEN_MP);
                    }
                }
            } finally {
                breakerHelper.unlock();
            }

            logCircuitBreakerState("Enter");
        }

        // Record state of breaker
        boolean wasBreakerOpen = isCircuitBreakerOpen();

        // Track invocation in a bulkhead
        if (introspector.hasBulkhead()) {
            bulkheadHelper.trackInvocation(this);
        }

        // Execute command
        Object result = null;
        Future<Object> future = null;
        Throwable throwable = null;
        long startNanos = System.nanoTime();
        try {
            // Queue the task
            future = super.queue();

            // Notify successful queueing of task
            taskQueued.complete(null);

            // Execute and get result from task
            result = future.get();
        } catch (Exception e) {
            // Notify exception during task queueing
            taskQueued.completeExceptionally(e);

            if (e instanceof ExecutionException) {
                waitForThreadToComplete();
            }
            if (e instanceof InterruptedException) {
                future.cancel(true);
            }
            throwable = decomposeException(e);
        }

        executionTime = System.nanoTime() - startNanos;
        boolean hasFailed = (throwable != null);

        if (introspector.hasCircuitBreaker()) {
            try {
                breakerHelper.lock();

                // Keep track of failure ratios
                breakerHelper.pushResult(throwable == null);

                // Query breaker states
                boolean breakerOpening = false;
                boolean isClosedNow = !wasBreakerOpen;

                /*
                 * Special logic for MP circuit breakers to support failOn. If not a
                 * throwable to fail on, restore underlying breaker and return.
                 */
                if (hasFailed) {
                    final Throwable unwrappedThrowable = ExceptionUtil.unwrapHystrix(throwable);
                    Class<? extends Throwable>[] throwableClasses = introspector.getCircuitBreaker().failOn();
                    boolean failOn = Arrays.asList(throwableClasses)
                            .stream()
                            .anyMatch(c -> c.isAssignableFrom(unwrappedThrowable.getClass()));
                    if (!failOn) {
                        // If underlying circuit breaker is not open, this counts as successful
                        // run since it failed on an exception not listed in failOn.
                        updateMetricsAfter(breakerHelper.getState() != State.OPEN_MP ? null : throwable,
                                wasBreakerOpen, isClosedNow, breakerOpening);
                        logCircuitBreakerState("Exit 1");
                        throw ExceptionUtil.toWrappedException(throwable);
                    }
                }

                // CLOSED_MP -> OPEN_MP
                if (breakerHelper.getState() == State.CLOSED_MP) {
                    double failureRatio = breakerHelper.getFailureRatio();
                    if (failureRatio >= introspector.getCircuitBreaker().failureRatio()) {
                        breakerHelper.setState(State.OPEN_MP);
                        breakerOpening = true;
                    }
                }

                // HALF_OPEN_MP -> OPEN_MP
                if (hasFailed) {
                    if (breakerHelper.getState() == State.HALF_OPEN_MP) {
                        breakerHelper.setState(State.OPEN_MP);
                    }
                    updateMetricsAfter(throwable, wasBreakerOpen, isClosedNow, breakerOpening);
                    logCircuitBreakerState("Exit 2");
                    throw ExceptionUtil.toWrappedException(throwable);
                }

                // Otherwise, increment success count
                breakerHelper.incSuccessCount();

                // HALF_OPEN_MP -> CLOSED_MP
                if (breakerHelper.getState() == State.HALF_OPEN_MP) {
                    if (breakerHelper.getSuccessCount() == introspector.getCircuitBreaker().successThreshold()) {
                        breakerHelper.setState(State.CLOSED_MP);
                        breakerHelper.resetCommandData();
                        lockRemoved = true;
                        isClosedNow = true;
                    }
                }

                updateMetricsAfter(throwable, wasBreakerOpen, isClosedNow, breakerOpening);
            } finally {
                if (!lockRemoved) {
                    breakerHelper.unlock();
                }
            }
        }

        // Untrack invocation in a bulkhead
        if (introspector.hasBulkhead()) {
            bulkheadHelper.untrackInvocation(this);
        }

        // Display circuit breaker state at exit
        logCircuitBreakerState("Exit 3");

        // Outcome of execution
        if (throwable != null) {
            throw ExceptionUtil.toWrappedException(throwable);
        } else {
            return result;
        }
    }

    private void updateMetricsAfter(Throwable throwable, boolean wasBreakerOpen, boolean isClosedNow,
                                    boolean breakerWillOpen) {
        if (!isFaultToleranceMetricsEnabled()) {
            return;
        }

        assert introspector.hasCircuitBreaker();
        Method method = introspector.getMethod();

        if (throwable == null) {
            // If no errors increment success counter
            getCounter(method, BREAKER_CALLS_SUCCEEDED_TOTAL).inc();
        } else if (!wasBreakerOpen) {
            // If error and breaker was closed, increment failed counter
            getCounter(method, BREAKER_CALLS_FAILED_TOTAL).inc();
            // If it will open, increment counter
            if (breakerWillOpen) {
                getCounter(method, BREAKER_OPENED_TOTAL).inc();
            }
        }
        // If breaker was open and still is, increment prevented counter
        if (wasBreakerOpen && !isClosedNow) {
            getCounter(method, BREAKER_CALLS_PREVENTED_TOTAL).inc();
        }
    }

    /**
     * Logs circuit breaker state, if one is present.
     *
     * @param preamble Message preamble.
     */
    private void logCircuitBreakerState(String preamble) {
        if (introspector.hasCircuitBreaker()) {
            String hystrixState = isCircuitBreakerOpen() ? "OPEN" : "CLOSED";
            LOGGER.fine(() -> preamble + ": breaker for " + getCommandKey() + " in state "
                    + breakerHelper.getState() + " (Hystrix: " + hystrixState
                    + " Thread:" + Thread.currentThread().getName() + ")");
        }
    }

    /**
     * <p>After a timeout expires, Hystrix can report an {@link ExecutionException}
     * when a thread has been interrupted but it is still running (e.g. while in a
     * busy loop). Hystrix makes this possible by using another thread to monitor
     * the command's thread.</p>
     *
     * <p>According to the FT spec, the thread may continue to run, so here
     * we give it a chance to do that before completing the execution of the
     * command. For more information see TCK test {@code
     * TimeoutUninterruptableTest::testTimeout}.</p>
     */
    private void waitForThreadToComplete() {
        if (!introspector.isAsynchronous() && runThread != null && runThread.isInterrupted()) {
            try {
                int waitTime = 250;
                while (runThread.getState() == Thread.State.RUNNABLE && waitTime <= threadWaitingPeriod) {
                    LOGGER.fine(() -> "Waiting for completion of thread " + runThread);
                    Thread.sleep(waitTime);
                    waitTime += 250;
                }
            } catch (InterruptedException e) {
                // Falls through
            }
        }
    }
}
