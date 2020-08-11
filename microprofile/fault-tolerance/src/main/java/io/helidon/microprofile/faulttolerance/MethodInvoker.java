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

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.enterprise.context.control.RequestContextController;
import javax.enterprise.inject.spi.CDI;
import javax.interceptor.InvocationContext;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
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

import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.eclipse.microprofile.metrics.Counter;
import org.glassfish.jersey.process.internal.RequestContext;
import org.glassfish.jersey.process.internal.RequestScope;

import static io.helidon.microprofile.faulttolerance.FaultToleranceExtension.isFaultToleranceMetricsEnabled;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_CALLS_FAILED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_CALLS_PREVENTED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_CALLS_SUCCEEDED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_CLOSED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_HALF_OPEN_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_OPENED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_OPEN_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BULKHEAD_CALLS_ACCEPTED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BULKHEAD_CALLS_REJECTED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BULKHEAD_CONCURRENT_EXECUTIONS;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BULKHEAD_EXECUTION_DURATION;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BULKHEAD_WAITING_DURATION;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BULKHEAD_WAITING_QUEUE_POPULATION;
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

/**
 * Invokes a FT method applying semantics based on method annotations. An instance
 * of this class is created for each method invocation. Some state is shared across
 * all invocations of the method, including for circuit breakers and bulkheads.
 */
public class MethodInvoker implements FtSupplier<Object> {
    private static final Logger LOGGER = Logger.getLogger(MethodInvoker.class.getName());

    /**
     * The method being intercepted.
     */
    private final Method method;

    /**
     * Invocation context for the interception.
     */
    private final InvocationContext context;

    /**
     * Helper class to extract information about the method.
     */
    private final MethodIntrospector introspector;

    /**
     * Map of methods to their internal state.
     */
    private static final ConcurrentHashMap<Method, MethodState> FT_HANDLERS = new ConcurrentHashMap<>();

    /**
     * Executor service shared by instances of this class.
     */
    private static final ScheduledExecutorService EXECUTOR_SERVICE = Executors.newScheduledThreadPool(16);

    /**
     * Start system nanos when handler is called.
     */
    private long handlerStartNanos;

    /**
     * Start system nanos when method {@code proceed()} is called.
     */
    private long invocationStartNanos;

    /**
     * Helidon context in which to run business method.
     */
    private final Context helidonContext;

    /**
     * Jersey's request scope object. Will be non-null if request scope is active.
     */
    private RequestScope requestScope;

    /**
     * Jersey's request scope object.
     */
    private RequestContext requestContext;

    /**
     * CDI's request scope controller used for activation/deactivation.
     */
    private RequestContextController requestController;

    private static class MethodState {
        private Bulkhead bulkhead;
        private CircuitBreaker breaker;
        private State lastBreakerState;
        private long breakerTimerOpen;
        private long breakerTimerClosed;
        private long breakerTimerHalfOpen;
        private long startNanos;
    }

    /**
     * State associated with a method instead of an invocation. Includes bulkhead
     * and breaker handlers that are shared across all invocations of same method.
     */
    private final MethodState methodState;

    /**
     * The handler for this invocation. Will share bulkhead an breaker
     * sub-handlers across all invocations for the same method.
     */
    private FtHandlerTyped<Object> handler;

    /**
     * Handler for retries. Will be {@code null} if no retry specified in method.
     */
    private Retry retry;

    /**
     * Constructor.
     *
     * @param context The invocation context.
     * @param introspector The method introspector.
     */
    public MethodInvoker(InvocationContext context, MethodIntrospector introspector) {
        this.context = context;
        this.introspector = introspector;
        this.method = context.getMethod();
        this.helidonContext = Contexts.context().orElseGet(Context::create);

        // Initialize method state and created handler for it
        synchronized (method) {
            this.methodState = FT_HANDLERS.computeIfAbsent(method, method -> {
                MethodState methodState = new MethodState();
                methodState.lastBreakerState = State.CLOSED;
                if (introspector.hasCircuitBreaker()) {
                    methodState.breakerTimerOpen = 0L;
                    methodState.breakerTimerClosed = 0L;
                    methodState.breakerTimerHalfOpen = 0L;
                    methodState.startNanos = System.nanoTime();
                }
                return methodState;
            });
            handler = createMethodHandler();
        }

        // Gather information about current request scope if active
        try {
            requestController = CDI.current().select(RequestContextController.class).get();
            requestScope = CDI.current().select(RequestScope.class).get();
            requestContext = requestScope.current();
        } catch (Exception e) {
            requestScope = null;
            LOGGER.fine(() -> "Request context not active for method " + method
                    + " on thread " + Thread.currentThread().getName());
        }

        // Registration of gauges for bulkhead and circuit breakers
        if (isFaultToleranceMetricsEnabled()) {
            if (introspector.hasCircuitBreaker()) {
                registerGauge(method, BREAKER_OPEN_TOTAL,
                        "Amount of time the circuit breaker has spent in open state",
                        () -> methodState.breakerTimerOpen);
                registerGauge(method, BREAKER_HALF_OPEN_TOTAL,
                        "Amount of time the circuit breaker has spent in half-open state",
                        () -> methodState.breakerTimerHalfOpen);
                registerGauge(method, BREAKER_CLOSED_TOTAL,
                        "Amount of time the circuit breaker has spent in closed state",
                        () -> methodState.breakerTimerClosed);
            }
            if (introspector.hasBulkhead()) {
                registerGauge(method, BULKHEAD_CONCURRENT_EXECUTIONS,
                        "Number of currently running executions",
                        () -> methodState.bulkhead.stats().concurrentExecutions());
                if (introspector.isAsynchronous()) {
                    registerGauge(method, BULKHEAD_WAITING_QUEUE_POPULATION,
                            "Number of executions currently waiting in the queue",
                            () -> methodState.bulkhead.stats().waitingQueueSize());
                }
            }
        }
    }

    @Override
    public String toString() {
        String s = super.toString();
        StringBuilder sb = new StringBuilder();
        sb.append(s.substring(s.lastIndexOf('.') + 1))
                .append(" ")
                .append(method.getDeclaringClass().getSimpleName())
                .append(".")
                .append(method.getName())
                .append("()");
        return sb.toString();
    }

    /**
     * Clears ftHandlers map of any cached handlers.
     */
    static void clearFtHandlersMap() {
        FT_HANDLERS.clear();
    }

    /**
     * Invokes the FT method.
     *
     * @return Value returned by method.
     */
    @Override
    public Object get() throws Throwable {
        // Wrap method call with Helidon context and init metrics
        Supplier<Single<Object>> supplier = () -> {
            try {
                return Contexts.runInContextWithThrow(helidonContext,
                        () -> handler.invoke(toCompletionStageSupplier(context::proceed)));
            } catch (Exception e) {
                return Single.error(e);
            }
        };

        /*
         * Call method preserving request scope if active. This is required for
         * @Inject and @Context to work properly. Note that it's possible for only
         * CDI's request scope to be active at this time (e.g. in TCKs).
         */
        Supplier<Single<Object>> wrappedSupplier;
        if (requestScope != null) {                     // Jersey and CDI
            wrappedSupplier = () -> {
                try {
                    return requestScope.runInScope(requestContext, (() -> {
                        try {
                            requestController.activate();
                            return supplier.get();
                        } finally {
                            requestController.deactivate();
                        }
                    }));
                } catch (Exception e) {
                    return Single.error(e);
                }
            };
        } else if (requestController != null) {         // CDI only
            wrappedSupplier = () -> {
                try {
                    requestController.activate();
                    return supplier.get();
                } catch (Exception e) {
                    return Single.error(e);
                } finally {
                    requestController.deactivate();
                }
            };
        } else {
            wrappedSupplier = supplier;
        }

        // Final supplier that handles metrics and maps exceptions
        FtSupplier<Object> finalSupplier = () -> {
            Object result = null;
            Throwable cause = null;
            try {
                // Need to allow completion with no value (null) for void methods
                CompletableFuture<Object> future = wrappedSupplier.get()
                        .toStage(true).toCompletableFuture();
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
        };

        // Special cases for sync and async invocations
        if (introspector.isAsynchronous()) {
            if (introspector.isReturnType(CompletionStage.class) || introspector.isReturnType(Future.class)) {
                CompletableFuture<Object> finalResult = new CompletableFuture<>();
                Async.create().invoke(() -> {
                    try {
                        return finalResult.complete(finalSupplier.get());
                    } catch (Throwable t) {
                        return finalResult.completeExceptionally(t);
                    }
                });
                return finalResult;
            }

            // Oops, something went wrong during validation
            throw new InternalError("Validation failed, return type must be Future or CompletionStage");
        } else {
            return finalSupplier.get();
        }
    }

    /**
     * Creates a FT handler for an invocation by inspecting annotations. Circuit breakers
     * and bulkheads are shared across all invocations --associated with methods.
     */
    private FtHandlerTyped<Object> createMethodHandler() {
        FaultTolerance.TypedBuilder<Object> builder = FaultTolerance.typedBuilder();

        // Create and add circuit breaker
        if (introspector.hasCircuitBreaker()) {
            if (methodState.breaker == null) {
                methodState.breaker = CircuitBreaker.builder()
                                .delay(Duration.of(introspector.getCircuitBreaker().delay(),
                                        introspector.getCircuitBreaker().delayUnit()))
                                .successThreshold(introspector.getCircuitBreaker().successThreshold())
                                .errorRatio((int) (introspector.getCircuitBreaker().failureRatio() * 100))
                                .volume(introspector.getCircuitBreaker().requestVolumeThreshold())
                                .applyOn(introspector.getCircuitBreaker().failOn())
                                .skipOn(introspector.getCircuitBreaker().skipOn())
                                .build();
            }
            builder.addBreaker(methodState.breaker);
        }

        // Create and add bulkhead
        if (introspector.hasBulkhead()) {
            if (methodState.bulkhead == null) {
                methodState.bulkhead = Bulkhead.builder()
                                .limit(introspector.getBulkhead().value())
                                .queueLength(introspector.getBulkhead().waitingTaskQueue())
                                .async(introspector.isAsynchronous())
                                .build();
            }
            builder.addBulkhead(methodState.bulkhead);
        }

        // Create and add timeout handler -- parent of breaker or bulkhead
        if (introspector.hasTimeout()) {
            Timeout timeout = Timeout.builder()
                    .timeout(Duration.of(introspector.getTimeout().value(), introspector.getTimeout().unit()))
                    .async(false)   // no async here
                    .executor(EXECUTOR_SERVICE)
                    .build();
            builder.addTimeout(timeout);
        }

        // Create and add retry handler -- parent of timeout
        if (introspector.hasRetry()) {
            Retry retry = Retry.builder()
                    .retryPolicy(Retry.JitterRetryPolicy.builder()
                            .calls(introspector.getRetry().maxRetries() + 1)
                            .delay(Duration.of(introspector.getRetry().delay(),
                                               introspector.getRetry().delayUnit()))
                            .jitter(Duration.of(introspector.getRetry().jitter(),
                                                introspector.getRetry().jitterDelayUnit()))
                            .build())
                    .overallTimeout(Duration.of(introspector.getRetry().maxDuration(),
                                                introspector.getRetry().durationUnit()))
                    .applyOn(introspector.getRetry().retryOn())
                    .skipOn(introspector.getRetry().abortOn())
                    .build();
            builder.addRetry(retry);
            this.retry = retry;
        }

        // Create and add fallback handler -- parent of retry
        if (introspector.hasFallback()) {
            Fallback<Object> fallback = Fallback.builder()
                    .fallback(throwable -> {
                        CommandFallback cfb = new CommandFallback(context, introspector, throwable);
                        return toCompletionStageSupplier(cfb::execute).get();
                    })
                    .applyOn(introspector.getFallback().applyOn())
                    .skipOn(introspector.getFallback().skipOn())
                    .build();
            builder.addFallback(fallback);
        }

        FtHandlerTyped<Object> result = builder.build();
        System.out.println("\n### tree \n" + result);
        return result;
    }

    /**
     * Maps an {@link FtSupplier} to a supplier of {@link CompletionStage}. Avoids
     * unnecessary wrapping of stages only when method is asynchronous.
     *
     * @param supplier The supplier.
     * @return The new supplier.
     */
    @SuppressWarnings("unchecked")
    Supplier<? extends CompletionStage<Object>> toCompletionStageSupplier(FtSupplier<Object> supplier) {
        return () -> {
            try {
                invocationStartNanos = System.nanoTime();

                // This is the actual method invocation
                Object result = supplier.get();

                // Return value without additional wrapping
                return introspector.isAsynchronous() && result instanceof CompletionStage<?>
                        ? (CompletionStage<Object>) result
                        : CompletableFuture.completedFuture(result);
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
        };
    }

    /**
     * Collects information necessary to update metrics before method is called.
     */
    private void updateMetricsBefore() {
        handlerStartNanos = System.nanoTime();
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

        synchronized (method) {
            // Calculate execution time
            long executionTime = System.nanoTime() - handlerStartNanos;

            // Metrics for retries
            if (introspector.hasRetry()) {
                // Have retried the last call?
                long newValue = retry.retryCounter();
                if (updateCounter(method, RETRY_RETRIES_TOTAL, newValue)) {
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

            // Bulkhead
            if (introspector.hasBulkhead()) {
                Objects.requireNonNull(methodState.bulkhead);
                Bulkhead.Stats stats = methodState.bulkhead.stats();
                updateCounter(method, BULKHEAD_CALLS_ACCEPTED_TOTAL, stats.callsAccepted());
                updateCounter(method, BULKHEAD_CALLS_REJECTED_TOTAL, stats.callsRejected());
                long waitingTime = invocationStartNanos - handlerStartNanos;
                getHistogram(method, BULKHEAD_EXECUTION_DURATION).update(executionTime - waitingTime);
                if (introspector.isAsynchronous()) {
                    getHistogram(method, BULKHEAD_WAITING_DURATION).update(waitingTime);
                }
            }

            // Global method counters
            getCounter(method, INVOCATIONS_TOTAL).inc();
            if (cause != null) {
                getCounter(method, INVOCATIONS_FAILED_TOTAL).inc();
            }
        }
    }

    /**
     * Sets the value of a monotonically increasing counter using {@code inc()}.
     *
     * @param method the method.
     * @param name the counter's name.
     * @param newValue the new value.
     * @return A value of {@code true} if counter updated, {@code false} otherwise.
     */
    private static boolean updateCounter(Method method, String name, long newValue) {
        Counter counter = getCounter(method, name);
        long oldValue = counter.getCount();
        if (newValue > oldValue) {
            counter.inc(newValue - oldValue);
            return true;
        }
        return false;
    }
}
