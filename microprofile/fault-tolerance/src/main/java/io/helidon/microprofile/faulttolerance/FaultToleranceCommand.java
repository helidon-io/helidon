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
import java.util.Arrays;
import java.util.logging.Logger;

import javax.interceptor.InvocationContext;

import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import org.apache.commons.configuration.AbstractConfiguration;

import static com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy.THREAD;

/**
 * Class FaultToleranceCommand.
 */
public class FaultToleranceCommand extends HystrixCommand<Object> {
    private static final Logger LOGGER = Logger.getLogger(FaultToleranceCommand.class.getName());

    private static final String HELIDON_MICROPROFILE_FAULTTOLERANCE = "io.helidon.microprofile.faulttolerance";

    private final String commandKey;

    private final MethodIntrospector introspector;

    private final InvocationContext context;

    private long executionTime = -1L;

    private CircuitBreakerHelper breakerHelper;

    private BulkheadHelper bulkheadHelper;

    private long queuedNanos = -1L;

    /**
     * A Hystrix command that can be used to open or close a circuit breaker
     * by running a succession of passing or failing commands that are part
     * of a {@link Runnable}.
     */
    private static class RunnableCommand extends HystrixCommand<Object> {

        private final Runnable runnable;

        RunnableCommand(String commandKey, Runnable runnable) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(HELIDON_MICROPROFILE_FAULTTOLERANCE))
                        .andCommandKey(
                            HystrixCommandKey.Factory.asKey(commandKey))
                        .andCommandPropertiesDefaults(
                            HystrixCommandProperties.Setter().withFallbackEnabled(false)));
            this.runnable = runnable;
        }

        @Override
        protected Object run() throws Exception {
            runnable.run();
            return "";
        }
    }

    /**
     * Default thread pool size for a command or a command group.
     */
    private static final int MAX_THREAD_POOL_SIZE = 10;

    /**
     * Default max thread pool queue size.
     */
    private static final int MAX_THREAD_POOL_QUEUE_SIZE = -1;

    /**
     * Constructor. Specify a thread pool key if a {@code @Bulkhead} is specified
     * on the method. A unique thread pool key will enable setting limits for this
     * command only based on the {@code Bulkhead} properties.
     *
     * @param commandKey The command key.
     * @param introspector The method introspector.
     * @param context CDI invocation context.
     */
    public FaultToleranceCommand(String commandKey, MethodIntrospector introspector, InvocationContext context) {
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(HELIDON_MICROPROFILE_FAULTTOLERANCE))
                    .andCommandKey(
                        HystrixCommandKey.Factory.asKey(commandKey))
                    .andCommandPropertiesDefaults(
                        HystrixCommandProperties.Setter()
                                                .withFallbackEnabled(false)
                                                .withExecutionIsolationStrategy(THREAD))
                    .andThreadPoolKey(
                        introspector.hasBulkhead()
                        ? HystrixThreadPoolKey.Factory.asKey(commandKey)
                        : null)
                    .andThreadPoolPropertiesDefaults(
                        HystrixThreadPoolProperties.Setter()
                                                   .withCoreSize(
                                                       introspector.hasBulkhead()
                                                       ? introspector.getBulkhead().value()
                                                       : MAX_THREAD_POOL_SIZE)
                                                   .withMaximumSize(
                                                       introspector.hasBulkhead()
                                                       ? introspector.getBulkhead().value()
                                                       : MAX_THREAD_POOL_SIZE)
                                                   .withMaxQueueSize(
                                                       introspector.hasBulkhead()
                                                       ? introspector.getBulkhead().waitingTaskQueue()
                                                       : MAX_THREAD_POOL_QUEUE_SIZE)
                                                   .withQueueSizeRejectionThreshold(
                                                       introspector.hasBulkhead()
                                                       ? introspector.getBulkhead().waitingTaskQueue()
                                                       : MAX_THREAD_POOL_QUEUE_SIZE)));
        this.commandKey = commandKey;
        this.introspector = introspector;
        this.context = context;

        // Special initialization for methods with breakers
        if (introspector.hasCircuitBreaker()) {
            this.breakerHelper = new CircuitBreakerHelper(this, introspector.getCircuitBreaker());

            // Register gauges for this method
            FaultToleranceMetrics.registerGauge(introspector.getMethod(),
                                                FaultToleranceMetrics.BREAKER_OPEN_TOTAL,
                                                "Amount of time the circuit breaker has spent in open state",
                                                () -> breakerHelper.getInStateNanos(CircuitBreakerHelper.State.OPEN_MP));
            FaultToleranceMetrics.registerGauge(introspector.getMethod(),
                                                FaultToleranceMetrics.BREAKER_HALF_OPEN_TOTAL,
                                                "Amount of time the circuit breaker has spent in half-open state",
                                                () -> breakerHelper.getInStateNanos(CircuitBreakerHelper.State.HALF_OPEN_MP));
            FaultToleranceMetrics.registerGauge(introspector.getMethod(),
                                                FaultToleranceMetrics.BREAKER_CLOSED_TOTAL,
                                                "Amount of time the circuit breaker has spent in closed state",
                                                () -> breakerHelper.getInStateNanos(CircuitBreakerHelper.State.CLOSED_MP));
        }

        if (introspector.hasBulkhead()) {
            bulkheadHelper = new BulkheadHelper(commandKey, introspector.getBulkhead());
            // Record instance if command is getting queued
            if (bulkheadHelper.isAtMaxRunningInvocations()) {
                queuedNanos = System.nanoTime();
            }

            // Register gauges for this method
            FaultToleranceMetrics.registerGauge(introspector.getMethod(),
                                                FaultToleranceMetrics.BULKHEAD_CONCURRENT_EXECUTIONS,
                                                "Number of currently running executions",
                                                () -> bulkheadHelper.runningInvocations());
            if (introspector.isAsynchronous()) {
                FaultToleranceMetrics.registerGauge(introspector.getMethod(),
                                                    FaultToleranceMetrics.BULKHEAD_WAITING_QUEUE_POPULATION,
                                                    "Number of executions currently waiting in the queue",
                                                    () -> bulkheadHelper.waitingInvocations());
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

    /**
     * Code to run as part of this command. Called from superclass.
     *
     * @return Result of command.
     * @throws Exception If an error occurs.
     */
    @Override
    public Object run() throws Exception {
        if (introspector.hasBulkhead()) {
            bulkheadHelper.markAsRunning(this);

            // Update waiting time histogram
            if (introspector.isAsynchronous() && queuedNanos != -1L) {
                FaultToleranceMetrics.getHistogram(introspector.getMethod(),
                                                   FaultToleranceMetrics.BULKHEAD_WAITING_DURATION)
                                     .update(System.nanoTime() - queuedNanos);
            }
        }

        // Finally, invoke the user method
        try {
            return context.proceed();
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
        // Configure command before execution
        introspector.getHystrixProperties()
                    .entrySet()
                    .forEach(entry -> setProperty(entry.getKey(), entry.getValue()));

        // Ensure our internal state is consistent with Hystrix
        if (introspector.hasCircuitBreaker()) {
            breakerHelper.ensureConsistentState();
            LOGGER.info("Enter: breaker for " + getCommandKey() + " in state " + breakerHelper.getState());
        }

        // Record state of breaker
        boolean wasBreakerOpen = isCircuitBreakerOpen();

        // Track invocation in a bulkhead
        if (introspector.hasBulkhead()) {
            bulkheadHelper.trackInvocation(this);
        }

        // Execute command
        Object result = null;
        Throwable throwable = null;
        long startNanos = System.nanoTime();
        try {
            result = super.execute();
        } catch (Throwable t) {
            throwable = t;
        }

        executionTime = System.nanoTime() - startNanos;
        boolean hasFailed = (throwable != null);

        if (introspector.hasCircuitBreaker()) {
            // Keep track of failure ratios
            breakerHelper.pushResult(throwable == null);

            // Query breaker states
            boolean breakerWillOpen = false;
            boolean isClosedNow = !isCircuitBreakerOpen();

            /*
             * Special logic for MP circuit breakers to support failOn. If not a
             * throwable to fail on, restore underlying breaker and return.
             */
            if (hasFailed) {
                final Throwable throwableFinal = throwable;
                Class<? extends Throwable>[] throwableClasses = introspector.getCircuitBreaker().failOn();
                boolean failOn = Arrays.asList(throwableClasses)
                                       .stream()
                                       .anyMatch(c -> c.isAssignableFrom(throwableFinal.getClass()));
                if (!failOn) {
                    restoreBreaker();       // clears Hystrix counters
                    updateMetricsAfter(throwable, wasBreakerOpen, breakerWillOpen);
                    throw ExceptionUtil.wrapThrowable(throwable);
                }
            }

            /*
             * Special logic for MP circuit breakers to support an arbitrary success
             * threshold used to return a breaker back to its CLOSED state. Hystrix
             * only supports a threshold of 1 here, so additional logic is required.
             */
            synchronized (breakerHelper.getSyncObject()) {
                // If failure ratio exceeded, then switch state to OPEN_MP
                if (breakerHelper.getState() == CircuitBreakerHelper.State.CLOSED_MP) {
                    double failureRatio = breakerHelper.getFailureRatio();
                    if (failureRatio >= introspector.getCircuitBreaker().failureRatio()) {
                        breakerWillOpen = true;
                        breakerHelper.setState(CircuitBreakerHelper.State.OPEN_MP);
                        runTripBreaker();
                    }
                }

                // If latest run failed, may need to switch state to OPEN_MP
                if (hasFailed) {
                    if (breakerHelper.getState() == CircuitBreakerHelper.State.HALF_OPEN_MP) {
                        // If failed and in HALF_OPEN_MP, we need to force breaker to open
                        runTripBreaker();
                        breakerHelper.setState(CircuitBreakerHelper.State.OPEN_MP);
                    }
                    updateMetricsAfter(throwable, wasBreakerOpen, breakerWillOpen);
                    throw ExceptionUtil.wrapThrowable(throwable);
                }

                // Check next state of breaker based on outcome
                if (wasBreakerOpen && isClosedNow) {
                    // Last called was successful
                    breakerHelper.incSuccessCount();

                    // We stay in HALF_OPEN_MP until successThreshold is reached
                    if (breakerHelper.getSuccessCount() < introspector.getCircuitBreaker().successThreshold()) {
                        breakerHelper.setState(CircuitBreakerHelper.State.HALF_OPEN_MP);
                    } else {
                        breakerHelper.setState(CircuitBreakerHelper.State.CLOSED_MP);
                        breakerHelper.resetCommandData();
                    }
                }
            }

            updateMetricsAfter(throwable, wasBreakerOpen, breakerWillOpen);
        }

        // Untrack invocation in a bulkhead
        if (introspector.hasBulkhead()) {
            bulkheadHelper.untrackInvocation(this);
        }

        // Display circuit breaker state at exit
        if (introspector.hasCircuitBreaker()) {
            LOGGER.info("Exit: breaker for " + getCommandKey() + " in state " + breakerHelper.getState());
        }

        // Outcome of execution
        if (throwable != null) {
            throw ExceptionUtil.wrapThrowable(throwable);
        } else {
            return result;
        }
    }

    private void updateMetricsAfter(Throwable throwable, boolean wasBreakerOpen, boolean breakerWillOpen) {
        assert introspector.hasCircuitBreaker();
        Method method = introspector.getMethod();

        if (throwable == null) {
            // If no errors increment success counter
            FaultToleranceMetrics.getCounter(method, FaultToleranceMetrics.BREAKER_CALLS_SUCCEEDED_TOTAL).inc();
        } else if (!wasBreakerOpen) {
            // If error and breaker was closed, increment failed counter
            FaultToleranceMetrics.getCounter(method, FaultToleranceMetrics.BREAKER_CALLS_FAILED_TOTAL).inc();
            // If it will open, increment counter
            if (breakerWillOpen) {
                FaultToleranceMetrics.getCounter(method, FaultToleranceMetrics.BREAKER_OPENED_TOTAL).inc();
            }
        }
        if (wasBreakerOpen) {
            // If breaker was open, increment prevented counter
            FaultToleranceMetrics.getCounter(method, FaultToleranceMetrics.BREAKER_CALLS_PREVENTED_TOTAL).inc();
        }
    }

    /**
     * Run a failing command for an entire window plus one to force a circuit breaker
     * to open. Unfortunately, there is no access to the underlying circuit breaker
     * so this is the only way to control its internal state. Notice the use of
     * the same {@code commandKey}.
     */
    private void runTripBreaker() {
        if (!isCircuitBreakerOpen()) {
            LOGGER.info("Attempting to trip circuit breaker for command " + commandKey);
            final int windowSize = introspector.getCircuitBreaker().requestVolumeThreshold();
            for (int i = 0; i <= windowSize; i++) {
                try {
                    new RunnableCommand(commandKey, () -> {
                        throw new RuntimeException("Oops");
                    }).execute();
                } catch (Throwable t) {
                    LOGGER.info("### t = " + t);
                    // ignore
                }
            }
            if (!isCircuitBreakerOpen()) {
                LOGGER.info("Attempt to manually open breaker failed for command "
                        + commandKey);
            }
        }
    }

    /**
     * Run a successful command for an entire window plus one to force a circuit breaker
     * to close. Unfortunately, there is no access to the underlying circuit breaker
     * so this is the only way to control its internal state. Notice the use of
     * the same {@code commandKey}.
     */
    private void restoreBreaker() {
        if (isCircuitBreakerOpen()) {
            LOGGER.info("Attempting to restore circuit breaker for command " + commandKey);
            final int windowSize = introspector.getCircuitBreaker().requestVolumeThreshold();
            for (int i = 0; i <= windowSize; i++) {
                new RunnableCommand(commandKey, () -> {
                }).execute();
            }
            if (isCircuitBreakerOpen()) {
                LOGGER.info("Attempt to manually close breaker failed for command "
                        + commandKey);
            }
        }
    }

    /**
     * Sets a Hystrix property on a command.
     *
     * @param key Property key.
     * @param value Property value.
     */
    private void setProperty(String key, Object value) {
        final AbstractConfiguration configManager = ConfigurationManager.getConfigInstance();
        configManager.setProperty(String.format("hystrix.command.%s.%s", commandKey, key), value);
    }
}
