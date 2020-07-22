/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import javax.interceptor.InvocationContext;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import io.helidon.common.reactive.Single;
import io.helidon.faulttolerance.Async;
import io.helidon.faulttolerance.Bulkhead;
import io.helidon.faulttolerance.CircuitBreaker;
import io.helidon.faulttolerance.CircuitBreaker.State;
import io.helidon.faulttolerance.Fallback;
import io.helidon.faulttolerance.FaultTolerance;
import io.helidon.faulttolerance.FtHandlerTyped;
import io.helidon.faulttolerance.Retry;
import io.helidon.faulttolerance.Timeout;

import static io.helidon.microprofile.faulttolerance.FaultToleranceExtension.isFaultToleranceMetricsEnabled;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_CALLS_FAILED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_CALLS_PREVENTED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_CALLS_SUCCEEDED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_CLOSED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_HALF_OPEN_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_OPENED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_OPEN_TOTAL;
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
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.registerGauge;
import static io.helidon.microprofile.faulttolerance.ThrowableMapper.map;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.eclipse.microprofile.metrics.Counter;

/**
 * Runs a FT method.
 */
public class CommandRunner implements FtSupplier<Object> {

    private final Method method;

    private final InvocationContext context;

    private final MethodIntrospector introspector;

    private static final ConcurrentHashMap<Method, MethodState> ftHandlers = new ConcurrentHashMap<>();

    private boolean retried = false;

    private long timerStart;

    private static class MethodState {
        FtHandlerTyped<Object> handler;
        CircuitBreaker breaker;
        Retry retry;
        State lastBreakerState;
        long breakerTimerOpen;
        long breakerTimerClosed;
        long breakerTimerHalfOpen;
        long startNanos;
    }

    private final MethodState methodState;

    /**
     * Constructor.
     *
     * @param context The invocation context.
     * @param introspector The method introspector.
     */
    public CommandRunner(InvocationContext context, MethodIntrospector introspector) {
        this.context = context;
        this.introspector = introspector;
        this.method = context.getMethod();

        // Get or initialize new state for this method
        this.methodState = ftHandlers.computeIfAbsent(method, method -> {
            MethodState methodState = new MethodState();
            initMethodStateHandler(methodState);
            methodState.lastBreakerState = State.CLOSED;
            if (introspector.hasCircuitBreaker()) {
                methodState.breakerTimerOpen = 0L;
                methodState.breakerTimerClosed = 0L;
                methodState.breakerTimerHalfOpen = 0L;
                methodState.startNanos = System.nanoTime();
            }
            return methodState;
        });

        // Special initialization for methods with breakers
        if (isFaultToleranceMetricsEnabled() && introspector.hasCircuitBreaker()) {
            registerGauge(introspector.getMethod(),
                    BREAKER_OPEN_TOTAL,
                    "Amount of time the circuit breaker has spent in open state",
                    () -> methodState.breakerTimerOpen);
            registerGauge(introspector.getMethod(),
                    BREAKER_HALF_OPEN_TOTAL,
                    "Amount of time the circuit breaker has spent in half-open state",
                    () -> methodState.breakerTimerHalfOpen);
            registerGauge(introspector.getMethod(),
                    BREAKER_CLOSED_TOTAL,
                    "Amount of time the circuit breaker has spent in closed state",
                    () -> methodState.breakerTimerClosed);
        }
    }

    /**
     * Clears ftHandlers map of any cached handlers.
     */
    static void clearFtHandlersMap() {
        ftHandlers.clear();
    }

    /**
     * Invokes the FT method.
     *
     * @return Value returned by method.
     */
    @Override
    public Object get() throws Throwable {
        Single<Object> single;
        if (introspector.isAsynchronous()) {
            if (introspector.isReturnType(CompletionStage.class) || introspector.isReturnType(Future.class)) {
                // Invoke method in new thread and call get() to unwrap singles
                single = Async.create().invoke(() -> {
                    updateMetricsBefore();
                    return methodState.handler.invoke(toCompletionStageSupplier(context::proceed));
                });

                // Unwrap nested futures and map exceptions on complete
                CompletableFuture<Object> future = new CompletableFuture<>();
                single.whenComplete((o, t) -> {
                    Throwable cause = null;

                    // Update future to return
                    if (t == null) {
                        // If future whose value is a future, then unwrap them
                        Future<?> delegate = null;
                        if (o instanceof CompletionStage<?>) {
                            delegate = ((CompletionStage<?>) o).toCompletableFuture();
                        }
                        else if (o instanceof Future<?>) {
                            delegate = (Future<?>) o;
                        }
                        if (delegate != null) {
                            try {
                                future.complete(delegate.get());
                            } catch (Exception e) {
                                cause = map(e);
                                future.completeExceptionally(cause);
                            }
                        } else {
                            future.complete(o);
                        }
                    } else {
                        cause = map(t);
                        future.completeExceptionally(cause);
                    }

                    updateMetricsAfter(cause);
                });
                return future;
            }

            // Oops, something went wrong during validation
            throw new InternalError("Validation failed, return type must be Future or CompletionStage");
        } else {
            Object result = null;
            Throwable cause = null;

            single = methodState.handler.invoke(toCompletionStageSupplier(context::proceed));
            try {
                // Need to allow completion with no value (null) for void methods
                CompletableFuture<Object> future = single.toStage(true).toCompletableFuture();
                updateMetricsBefore();
                result = future.get();
            } catch (ExecutionException e) {
                cause = map(e.getCause());
            } catch (Throwable t) {
                cause = map(t);
            }

            updateMetricsAfter(cause);
            if (cause != null) {
                throw cause;
            }
            return result;
        }
    }

    /**
     * Creates a FT handler for a given method by inspecting annotations.
     */
    private void initMethodStateHandler(MethodState methodState) {
        FaultTolerance.TypedBuilder<Object> builder = FaultTolerance.typedBuilder();

        // Create and add circuit breaker
        if (introspector.hasCircuitBreaker()) {
            CircuitBreaker circuitBreaker = CircuitBreaker.builder()
                    .delay(Duration.of(introspector.getCircuitBreaker().delay(),
                            introspector.getCircuitBreaker().delayUnit()))
                    .successThreshold(introspector.getCircuitBreaker().successThreshold())
                    .errorRatio((int) (introspector.getCircuitBreaker().failureRatio() * 100))
                    .volume(introspector.getCircuitBreaker().requestVolumeThreshold())
                    .applyOn(introspector.getCircuitBreaker().failOn())
                    .build();
            builder.addBreaker(circuitBreaker);
            methodState.breaker = circuitBreaker;
        }

        // Create and add bulkhead
        if (introspector.hasBulkhead()) {
            Bulkhead bulkhead = Bulkhead.builder()
                    .limit(introspector.getBulkhead().value())
                    .queueLength(introspector.getBulkhead().waitingTaskQueue())
                    .async(introspector.isAsynchronous())
                    .build();
            builder.addBulkhead(bulkhead);
        }

        // Create and add timeout handler -- parent of breaker or bulkhead
        if (introspector.hasTimeout()) {
            Timeout timeout = Timeout.builder()
                    .timeout(Duration.of(introspector.getTimeout().value(), introspector.getTimeout().unit()))
                    .async(false)   // no async here
                    .build();
            builder.addTimeout(timeout);
        }

        // Create and add retry handler -- parent of timeout
        if (introspector.hasRetry()) {
            Retry retry = Retry.builder()
                    .retryPolicy(Retry.JitterRetryPolicy.builder()
                            .calls(introspector.getRetry().maxRetries() + 1)
                            .delay(Duration.ofMillis(introspector.getRetry().delay()))
                            .jitter(Duration.ofMillis(introspector.getRetry().jitter()))
                            .build())
                    .overallTimeout(Duration.ofNanos(Long.MAX_VALUE))       // not used
                    .build();
            builder.addRetry(retry);
            methodState.retry = retry;
        }

        // Create and add fallback handler -- parent of retry
        if (introspector.hasFallback()) {
            Fallback<Object> fallback = Fallback.builder()
                    .fallback(throwable -> {
                        CommandFallback cfb = new CommandFallback(context, introspector, throwable);
                        // getCounter(method, FALLBACK_CALLS_TOTAL).inc();
                        return toCompletionStageSupplier(cfb::execute).get();
                    })
                    .build();
            builder.addFallback(fallback);
        }

        // Set handler in method state
        methodState.handler = builder.build();
    }

    /**
     * Maps an {@link FtSupplier} to a supplier of {@link CompletionStage}. Avoids
     * unnecessary wrapping of stages.
     *
     * @param supplier The supplier.
     * @return The new supplier.
     */
    @SuppressWarnings("unchecked")
    static Supplier<? extends CompletionStage<Object>> toCompletionStageSupplier(FtSupplier<Object> supplier) {
        return () -> {
            try {
                Object result = supplier.get();
                return result instanceof CompletionStage<?> ? (CompletionStage<Object>) result
                        : CompletableFuture.completedFuture(result);
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
        };
    }

    /**
     * Collects information necessary to update metrics before method is called.
     */
    private synchronized void updateMetricsBefore() {
        timerStart = System.currentTimeMillis();
    }

    /**
     * Update metrics after method is called and depending on outcome.
     *
     * @param cause Exception cause or {@code null} if execution successful.
     */
    private synchronized void updateMetricsAfter(Throwable cause) {
        if (!isFaultToleranceMetricsEnabled()) {
            return;
        }

        // Calculate execution time
        long executionTime = System.currentTimeMillis() - timerStart;

        // Metrics for retries
        if (introspector.hasRetry()) {
            Counter counter = getCounter(method, RETRY_RETRIES_TOTAL);
            long oldCounter = counter.getCount();
            long newCounter = methodState.retry.retryCounter();

            // Have retried the last call?
            if (newCounter > oldCounter) {
                counter.inc(newCounter - oldCounter);       // akin to a set
                if (cause == null) {
                    getCounter(method, RETRY_CALLS_SUCCEEDED_RETRIED_TOTAL).inc();
                }
            } else {
                getCounter(method, RETRY_CALLS_SUCCEEDED_NOT_RETRIED_TOTAL).inc();
            }

            // Update failed calls
            if (cause != null) {
                getCounter(method, RETRY_CALLS_FAILED_TOTAL).inc();
            }
        }

        // Timeout
        if (introspector.hasTimeout()) {
            getHistogram(method, TIMEOUT_EXECUTION_DURATION).update(executionTime);
            getCounter(method, cause instanceof TimeoutException
                    ? TIMEOUT_CALLS_TIMED_OUT_TOTAL
                    : TIMEOUT_CALLS_NOT_TIMED_OUT_TOTAL).inc();
        }

        // Circuit breaker
        if (introspector.hasCircuitBreaker()) {
            Objects.requireNonNull(methodState.breaker);

            // Update counters based on state changes
            if (methodState.lastBreakerState != State.CLOSED) {
                getCounter(method, BREAKER_CALLS_PREVENTED_TOTAL).inc();
            } else if (methodState.breaker.state() == State.OPEN) {     // closed -> open
                getCounter(method, BREAKER_OPENED_TOTAL).inc();
            }

            // Update succeeded and failed
            if (cause == null) {
                getCounter(method, BREAKER_CALLS_SUCCEEDED_TOTAL).inc();
            } else if (!(cause instanceof CircuitBreakerOpenException)) {
                boolean failure = false;
                Class<? extends Throwable>[] failOn = introspector.getCircuitBreaker().failOn();
                for (Class<? extends Throwable> c : failOn) {
                    if (c.isAssignableFrom(cause.getClass())) {
                        failure = true;
                        break;
                    }
                }

                System.out.println("### failure " + failure + " " + cause);

                getCounter(method, failure ? BREAKER_CALLS_FAILED_TOTAL
                        : BREAKER_CALLS_SUCCEEDED_TOTAL).inc();
            }

            // Update times for gauges
            switch (methodState.lastBreakerState) {
                case OPEN:
                    methodState.breakerTimerOpen += System.nanoTime() - methodState.startNanos;
                    break;
                case CLOSED:
                    methodState.breakerTimerClosed += System.nanoTime() - methodState.startNanos;
                    break;
                case HALF_OPEN:
                    methodState.breakerTimerHalfOpen += System.nanoTime() - methodState.startNanos;
                    break;
                default:
                    throw new IllegalStateException("Unknown breaker state " + methodState.lastBreakerState);
            }

            // Update internal state
            methodState.lastBreakerState = methodState.breaker.state();
            methodState.startNanos = System.nanoTime();
        }

        // Global method counters
        getCounter(method, INVOCATIONS_TOTAL).inc();
        if (cause != null) {
            getCounter(method, INVOCATIONS_FAILED_TOTAL).inc();
        }
    }
}
