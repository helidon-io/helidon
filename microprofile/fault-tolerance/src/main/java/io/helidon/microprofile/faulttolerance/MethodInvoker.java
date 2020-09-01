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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

import static io.helidon.microprofile.faulttolerance.ThrowableMapper.mapTypes;
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
     * Map of a class loader and a method into a method state. Class loaders are needed
     * when running TCKs where each test is considered a different application (with
     * potentially different configurations).
     */
    private static final ConcurrentHashMap<ClassLoader, ConcurrentHashMap<Method, MethodState>>
            FT_HANDLERS = new ConcurrentHashMap<>();

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

    /**
     * Record thread interruption request for later use.
     */
    private final AtomicBoolean mayInterruptIfRunning = new AtomicBoolean(false);

    /**
     * Async thread in used by this invocation. May be {@code null}. We use this
     * reference for thread interruptions.
     */
    private Thread asyncInterruptThread;

    private static class MethodState {
        private FtHandlerTyped<Object> handler;
        private Retry retry;
        private Bulkhead bulkhead;
        private CircuitBreaker breaker;
        private State lastBreakerState;
        private long breakerTimerOpen;
        private long breakerTimerClosed;
        private long breakerTimerHalfOpen;
        private long startNanos;
    }

    /**
     * State associated with a method instead of an invocation. Shared by all
     * invocations of same method.
     */
    private final MethodState methodState;

    /**
     * Future returned by this method invoker. Some special logic to handle async
     * cancellations and methods returning {@code Future}.
     *
     * @param <T> result type of future
     */
    @SuppressWarnings("unchecked")
    class InvokerCompletableFuture<T> extends CompletableFuture<T> {

        /**
         * If method returns {@code Future}, we let that value pass through
         * without further processing. See Section 5.2.1 of spec.
         *
         * @return value from this future
         * @throws ExecutionException if this future completed exceptionally
         * @throws InterruptedException if the current thread was interrupted
         */
        @Override
        public T get() throws InterruptedException, ExecutionException {
            T value = super.get();
            if (method.getReturnType() == Future.class) {
                return ((Future<T>) value).get();
            }
            return value;
        }

        /**
         * If method returns {@code Future}, we let that value pass through
         * without further processing. See Section 5.2.1 of spec.
         *
         * @param timeout the timeout
         * @param unit the timeout unit
         * @return value from this future
         * @throws CancellationException if this future was cancelled
         * @throws ExecutionException if this future completed exceptionally
         * @throws InterruptedException if the current thread was interrupted
         */
        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException,
                ExecutionException, java.util.concurrent.TimeoutException {
            T value = super.get();
            if (method.getReturnType() == Future.class) {
                return ((Future<T>) value).get(timeout, unit);
            }
            return value;
        }

        /**
         * Overridden to record {@code mayInterruptIfRunning} flag. This flag
         * is not currently not propagated over a chain of {@code Single<?>}'s.
         *
         * @param mayInterruptIfRunning Interrupt flag.
         * @@return {@code true} if this task is now cancelled.
         */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            MethodInvoker.this.mayInterruptIfRunning.set(mayInterruptIfRunning);
            return super.cancel(mayInterruptIfRunning);
        }
    }

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

        // Create method state using CCL to support multiples apps (TCKs)
        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        Objects.requireNonNull(ccl);
        ConcurrentHashMap<Method, MethodState> methodStates =
                FT_HANDLERS.computeIfAbsent(ccl, cl -> new ConcurrentHashMap<>());
        this.methodState = methodStates.computeIfAbsent(method, method -> {
            MethodState methodState = new MethodState();
            methodState.lastBreakerState = State.CLOSED;
            if (introspector.hasCircuitBreaker()) {
                methodState.breakerTimerOpen = 0L;
                methodState.breakerTimerClosed = 0L;
                methodState.breakerTimerHalfOpen = 0L;
                methodState.startNanos = System.nanoTime();
            }
            methodState.handler = createMethodHandler(methodState);
            return methodState;
        });

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
     * Invokes a method with one or more FT annotations.
     *
     * @return value returned by method.
     */
    @Override
    public Object get() throws Throwable {
        // Wrap method call with Helidon context
        Supplier<Single<?>> supplier = () -> {
            try {
                return Contexts.runInContextWithThrow(helidonContext,
                        () -> methodState.handler.invoke(toCompletionStageSupplier(context::proceed)));
            } catch (Exception e) {
                return Single.error(e);
            }
        };

        // Update metrics before calling method
        updateMetricsBefore();

        if (introspector.isAsynchronous()) {
            // Obtain single from supplier
            Single<?> single = supplier.get();

            // Convert single to CompletableFuture
            CompletableFuture<?> asyncFuture = single.toStage(true).toCompletableFuture();

            // Create CompletableFuture that is returned to caller
            CompletableFuture<Object> resultFuture = new InvokerCompletableFuture<>();

            // Update resultFuture based on outcome of asyncFuture
            asyncFuture.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    if (throwable instanceof CancellationException) {
                        single.cancel();
                        return;
                    }
                    Throwable cause;
                    if (throwable instanceof ExecutionException) {
                        cause = map(throwable.getCause());
                    } else {
                        cause = map(throwable);
                    }
                    updateMetricsAfter(cause);
                    resultFuture.completeExceptionally(cause);
                } else {
                    updateMetricsAfter(null);
                    resultFuture.complete(result);
                }
            });

            // Propagate cancellation of resultFuture to asyncFuture
            resultFuture.whenComplete((result, throwable) -> {
                if (throwable instanceof CancellationException) {
                    asyncFuture.cancel(true);
                }
            });
            return resultFuture;
        } else {
            Object result = null;
            Throwable cause = null;
            try {
                // Obtain single from supplier and map to CompletableFuture to handle void methods
                Single<?> single = supplier.get();
                CompletableFuture<?> future = single.toStage(true).toCompletableFuture();

                // Synchronously way for result
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
     * Wraps a supplier with additional code to preserve request context (if active)
     * when running in a different thread. This is required for {@code @Inject} and
     * {@code @Context} to work properly. Note that it is possible for only CDI's
     * request scope to be active at this time (e.g. in TCKs).
     */
    private FtSupplier<Object> requestContextSupplier(FtSupplier<Object> supplier) {
        FtSupplier<Object> wrappedSupplier;
        if (requestScope != null) {                     // Jersey and CDI
            wrappedSupplier = () -> requestScope.runInScope(requestContext,
                    (Callable<?>) (() -> {
                        try {
                            requestController.activate();
                            return supplier.get();
                        } catch (Throwable t) {
                            throw t instanceof Exception ? ((Exception) t) : new RuntimeException(t);
                        } finally {
                            requestController.deactivate();
                        }
                    }));
        } else if (requestController != null) {         // CDI only
            wrappedSupplier = () -> {
                try {
                    requestController.activate();
                    return supplier.get();
                } finally {
                    requestController.deactivate();
                }
            };
        } else {
            wrappedSupplier = supplier;
        }
        return wrappedSupplier;
    }

    /**
     * Creates a FT handler for an invocation by inspecting annotations.
     *
     * - fallback(retry(bulkhead(circuitbreaker(timeout(method)))))
     *
     * @param methodState State related to this invocation's method.
     */
    private FtHandlerTyped<Object> createMethodHandler(MethodState methodState) {
        FaultTolerance.TypedBuilder<Object> builder = FaultTolerance.typedBuilder();

        // Create and add timeout handler
        if (introspector.hasTimeout()) {
            Timeout timeout = Timeout.builder()
                    .timeout(Duration.of(introspector.getTimeout().value(), introspector.getTimeout().unit()))
                    .currentThread(!introspector.isAsynchronous())
                    .executor(EXECUTOR_SERVICE)
                    .build();
            builder.addTimeout(timeout);
        }

        // Create and add circuit breaker
        if (introspector.hasCircuitBreaker()) {
            methodState.breaker = CircuitBreaker.builder()
                    .delay(Duration.of(introspector.getCircuitBreaker().delay(),
                            introspector.getCircuitBreaker().delayUnit()))
                    .successThreshold(introspector.getCircuitBreaker().successThreshold())
                    .errorRatio((int) (introspector.getCircuitBreaker().failureRatio() * 100))
                    .volume(introspector.getCircuitBreaker().requestVolumeThreshold())
                    .applyOn(mapTypes(introspector.getCircuitBreaker().failOn()))
                    .skipOn(mapTypes(introspector.getCircuitBreaker().skipOn()))
                    .build();
            builder.addBreaker(methodState.breaker);
        }

        // Create and add bulkhead
        if (introspector.hasBulkhead()) {
            methodState.bulkhead = Bulkhead.builder()
                    .limit(introspector.getBulkhead().value())
                    .queueLength(introspector.getBulkhead().waitingTaskQueue())
                    .async(introspector.isAsynchronous())
                    .build();
            builder.addBulkhead(methodState.bulkhead);
        }


        // Create and add retry handler
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
                    .applyOn(mapTypes(introspector.getRetry().retryOn()))
                    .skipOn(mapTypes(introspector.getRetry().abortOn()))
                    .build();
            builder.addRetry(retry);
            methodState.retry = retry;      // keep reference to Retry
        }

        // Create and add fallback handler
        if (introspector.hasFallback()) {
            Fallback<Object> fallback = Fallback.builder()
                    .fallback(throwable -> {
                        CommandFallback cfb = new CommandFallback(context, introspector, throwable);
                        return toCompletionStageSupplier(cfb::execute).get();
                    })
                    .applyOn(mapTypes(introspector.getFallback().applyOn()))
                    .skipOn(mapTypes(introspector.getFallback().skipOn()))
                    .build();
            builder.addFallback(fallback);
        }

        return builder.build();
    }

    /**
     * Maps an {@link FtSupplier} to a supplier of {@link CompletionStage}.
     *
     * @param supplier The supplier.
     * @return The new supplier.
     */
    @SuppressWarnings("unchecked")
    Supplier<? extends CompletionStage<Object>> toCompletionStageSupplier(FtSupplier<Object> supplier) {
        return () -> {
            invocationStartNanos = System.nanoTime();

            CompletableFuture<Object> resultFuture = new CompletableFuture<>();
            if (introspector.isAsynchronous()) {
                // Wrap supplier with request context setup
                FtSupplier wrappedSupplier = requestContextSupplier(supplier);

                // Invoke supplier in a new thread
                Single<Object> single = Async.create().invoke(() -> {
                    try {
                        asyncInterruptThread = Thread.currentThread();
                        return wrappedSupplier.get();
                    } catch (Throwable t) {
                        return new InvokerAsyncException(t);        // wraps Throwable
                    }
                });

                // Handle async cancellations
                resultFuture.whenComplete((result, throwable) -> {
                    if (throwable instanceof CancellationException) {
                        single.cancel();        // will not interrupt by default

                        // If interrupt was requested, do it manually here
                        if (mayInterruptIfRunning.get() && asyncInterruptThread != null) {
                            asyncInterruptThread.interrupt();
                            asyncInterruptThread = null;
                        }
                    }
                });

                // The result must be Future<?>, {Completable}Future<?> or InvokerAsyncException
                single.thenAccept(result -> {
                    try {
                        // Handle exceptions thrown by an async method
                        if (result instanceof InvokerAsyncException) {
                            resultFuture.completeExceptionally(((Exception) result).getCause());
                        } else if (method.getReturnType() == Future.class) {
                            // If method returns Future, pass it without further processing
                            resultFuture.complete(result);
                        } else if (result instanceof CompletionStage<?>) {     // also CompletableFuture<?>
                            CompletionStage<Object> cs = (CompletionStage<Object>) result;
                            cs.whenComplete((o, t) -> {
                                if (t != null) {
                                    resultFuture.completeExceptionally(t);
                                } else {
                                    resultFuture.complete(o);
                                }
                            });
                        } else {
                            throw new InternalError("Return type validation failed for method " + method);
                        }
                    } catch (Throwable t) {
                        resultFuture.completeExceptionally(t);
                    }
                });
            } else {
                try {
                    resultFuture.complete(supplier.get());
                    return resultFuture;
                } catch (Throwable t) {
                    resultFuture.completeExceptionally(t);
                }
            }
            return resultFuture;
        };
    }

    /**
     * Collects information necessary to update metrics after method is called.
     */
    private void updateMetricsBefore() {
        handlerStartNanos = System.nanoTime();

        if (introspector.hasCircuitBreaker()) {
            synchronized (method) {
                // Breaker state may have changed since we recorded it last
                methodState.lastBreakerState = methodState.breaker.state();
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

        synchronized (method) {
            // Calculate execution time
            long executionTime = System.nanoTime() - handlerStartNanos;

            // Metrics for retries
            if (introspector.hasRetry()) {
                // Have retried the last call?
                long newValue = methodState.retry.retryCounter();
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
                if (methodState.lastBreakerState == State.OPEN) {
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
     * @param method The method.
     * @param name The counter's name.
     * @param newValue The new value.
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
