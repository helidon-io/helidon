/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

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
import io.helidon.faulttolerance.RetryTimeoutException;
import io.helidon.faulttolerance.Timeout;

import jakarta.interceptor.InvocationContext;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.metrics.Counter;

import static io.helidon.microprofile.faulttolerance.FaultToleranceExtension.isFaultToleranceMetricsEnabled;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BulkheadCallsTotal;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BulkheadExecutionsRunning;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BulkheadExecutionsWaiting;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BulkheadResult;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BulkheadRunningDuration;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BulkheadWaitingDuration;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.CircuitBreakerCallsTotal;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.CircuitBreakerOpenedTotal;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.CircuitBreakerResult;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.CircuitBreakerState;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.CircuitBreakerStateTotal;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.InvocationResult.EXCEPTION_THROWN;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.InvocationResult.VALUE_RETURNED;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.InvocationsTotal;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.RetryCallsTotal;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.RetryResult;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.RetryRetried;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.RetryRetriesTotal;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.TimeoutCallsTotal;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.TimeoutExecutionDuration;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.TimeoutTimedOut;
import static io.helidon.microprofile.faulttolerance.ThrowableMapper.map;
import static io.helidon.microprofile.faulttolerance.ThrowableMapper.mapTypes;

/**
 * Invokes a FT method applying semantics based on method annotations. An instance
 * of this class is created for each method invocation. Some state is shared across
 * all invocations of a method, including for circuit breakers and bulkheads.
 */
class MethodInvoker implements FtSupplier<Object> {

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
     * Maps a {@code MethodStateKey} to a {@code MethodState}. The method state returned
     * caches the FT handler as well as some additional variables. This mapping must
     * be shared by all instances of this class.
     */
    private static final ConcurrentHashMap<MethodStateKey, MethodState> METHOD_STATES = new ConcurrentHashMap<>();

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
     * Record thread interruption request for later use.
     */
    private final AtomicBoolean mayInterruptIfRunning = new AtomicBoolean(false);

    /**
     * Async thread in used by this invocation. May be {@code null}. We use this
     * reference for thread interruptions.
     */
    private Thread asyncInterruptThread;

    /**
     * A boolean value indicates whether the fallback logic was called or not
     * on this invocation.
     */
    private AtomicBoolean fallbackCalled = new AtomicBoolean(false);

    /**
     * Helper to properly propagate active request scope to other threads.
     */
    private final RequestScopeHelper requestScopeHelper;

    /**
     * State associated with a method in {@code METHOD_STATES}.
     */
    private static class MethodState {
        private Retry retry;
        private Bulkhead bulkhead;
        private CircuitBreaker breaker;
        private Timeout timeout;
        private State lastBreakerState;
        private long breakerTimerOpen;
        private long breakerTimerClosed;
        private long breakerTimerHalfOpen;
        private long startNanos;
        private final ReentrantLock lock = new ReentrantLock();
    }

    /**
     * FT handler for this invoker.
     */
    private final FtHandlerTyped<Object> handler;

    /**
     * A key used to lookup {@code MethodState} instances, which include FT handlers.
     * A class loader is necessary to support multiple applications as seen in the TCKs.
     * The method class in necessary given that the same method can inherited by different
     * classes with different FT annotations and should not share handlers. Finally, the
     * method is main part of the key.
     */
    private static class MethodStateKey {
        private final ClassLoader classLoader;
        private final Class<?> methodClass;
        private final Method method;

        MethodStateKey(ClassLoader classLoader, Class<?> methodClass, Method method) {
            this.classLoader = classLoader;
            this.methodClass = methodClass;
            this.method = method;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MethodStateKey that = (MethodStateKey) o;
            return classLoader.equals(that.classLoader)
                    && methodClass.equals(that.methodClass)
                    && method.equals(that.method);
        }

        @Override
        public int hashCode() {
            return Objects.hash(classLoader, methodClass, method);
        }
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
         * @throws ExecutionException   if this future completed exceptionally
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
         * @param unit    the timeout unit
         * @return value from this future
         * @throws CancellationException if this future was cancelled
         * @throws ExecutionException    if this future completed exceptionally
         * @throws InterruptedException  if the current thread was interrupted
         */
        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException,
                ExecutionException, java.util.concurrent.TimeoutException {
            T value = super.get(timeout, unit);
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
     * @param context      The invocation context.
     * @param introspector The method introspector.
     */
    MethodInvoker(InvocationContext context, MethodIntrospector introspector) {
        this.context = context;
        this.introspector = introspector;
        this.method = context.getMethod();
        this.helidonContext = Contexts.context().orElseGet(Context::create);

        // Create method state using CCL to support multiples apps (like in TCKs)
        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        Objects.requireNonNull(ccl);
        MethodStateKey methodStateKey = new MethodStateKey(ccl, context.getTarget().getClass(), method);
        this.methodState = METHOD_STATES.computeIfAbsent(methodStateKey, key -> {
            MethodState methodState = new MethodState();
            methodState.lastBreakerState = State.CLOSED;
            if (introspector.hasCircuitBreaker()) {
                methodState.breakerTimerOpen = 0L;
                methodState.breakerTimerClosed = 0L;
                methodState.breakerTimerHalfOpen = 0L;
                methodState.startNanos = System.nanoTime();
            }
            initMethodHandler(methodState);
            return methodState;
        });

        // Create a new method handler to ensure correct context in fallback
        handler = createMethodHandler(methodState);

        // Gather information about current request scope if active
        requestScopeHelper = new RequestScopeHelper();
        requestScopeHelper.saveScope();

        registerMetrics();
    }

    private void registerMetrics() {
        if (!isFaultToleranceMetricsEnabled()) {
            return;
        }

        if (introspector.hasCircuitBreaker()) {
            CircuitBreakerStateTotal.register(
                    () -> methodState.breakerTimerOpen,
                    introspector.getMethodNameTag(),
                    CircuitBreakerState.OPEN.get());
            CircuitBreakerStateTotal.register(
                    () -> methodState.breakerTimerHalfOpen,
                    introspector.getMethodNameTag(),
                    CircuitBreakerState.HALF_OPEN.get());
            CircuitBreakerStateTotal.register(
                    () -> methodState.breakerTimerClosed,
                    introspector.getMethodNameTag(),
                    CircuitBreakerState.CLOSED.get());
            CircuitBreakerOpenedTotal.register(
                    introspector.getMethodNameTag());
        }
        if (introspector.hasBulkhead()) {
            BulkheadExecutionsRunning.register(
                    () -> methodState.bulkhead.stats().concurrentExecutions(),
                    introspector.getMethodNameTag());
            if (introspector.isAsynchronous()) {
                BulkheadExecutionsWaiting.register(
                        () -> methodState.bulkhead.stats().waitingQueueSize(),
                        introspector.getMethodNameTag());
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
     * Clears {@code METHOD_STATES} map.
     */
    static void clearMethodStatesMap() {
        METHOD_STATES.clear();
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
                        () -> handler.invoke(toCompletionStageSupplier(context::proceed)));
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
                // Release request context
                requestScopeHelper.clearScope();

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
                    resultFuture.completeExceptionally(cause instanceof RetryTimeoutException
                            ? ((RetryTimeoutException) cause).lastRetryException() : cause);
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
            } finally {
                // Release request context
                requestScopeHelper.clearScope();
            }
            updateMetricsAfter(cause);
            if (cause instanceof RetryTimeoutException) {
                throw ((RetryTimeoutException) cause).lastRetryException();
            }
            if (cause != null) {
                throw cause;
            }
            return result;
        }
    }

    /**
     * Initializes method state by creating handlers for all FT annotations
     * except fallbacks. A fallback can reference the current invocation context
     * (via fallback method parameters) and cannot be cached.
     *
     * @param methodState State related to this invocation's method.
     */
    private void initMethodHandler(MethodState methodState) {
        if (introspector.hasBulkhead()) {
            methodState.bulkhead = Bulkhead.builder()
                    .limit(introspector.getBulkhead().value())
                    .queueLength(introspector.isAsynchronous() ? introspector.getBulkhead().waitingTaskQueue() : 0)
                    .cancelSource(false)        // for the FT TCK's
                    .build();
        }

        if (introspector.hasTimeout()) {
            methodState.timeout = Timeout.builder()
                    .timeout(Duration.of(introspector.getTimeout().value(), introspector.getTimeout().unit()))
                    .currentThread(!introspector.isAsynchronous())
                    .cancelSource(false)        // for the FT TCK's
                    .build();
        }

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
        }
    }

    /**
     * Creates a FT handler for this invocation. Handlers are composed as follows:
     * <p>
     * fallback(retry(circuitbreaker(timeout(bulkhead(method)))))
     * <p>
     * Uses the cached handlers defined in the method state for this invocation's
     * method, except for fallback.
     *
     * @param methodState State related to this invocation's method.
     */
    private FtHandlerTyped<Object> createMethodHandler(MethodState methodState) {
        FaultTolerance.TypedBuilder<Object> builder = FaultTolerance.typedBuilder();

        if (methodState.bulkhead != null) {
            builder.addBulkhead(methodState.bulkhead);
        }

        if (methodState.timeout != null) {
            builder.addTimeout(methodState.timeout);
        }

        if (methodState.breaker != null) {
            builder.addBreaker(methodState.breaker);
        }

        // Create a retry for this invocation only
        if (introspector.hasRetry()) {
            int maxRetries = introspector.getRetry().maxRetries();
            if (maxRetries == -1) {
                maxRetries = Integer.MAX_VALUE;
            } else {
                maxRetries++;       // add 1 for initial call
            }

            if (introspector.hasRetryExponentialBackoff()) {
                methodState.retry = Retry.builder()
                        .retryPolicy(Retry.ExponentialRetryPolicy.builder()
                                .calls(introspector.getRetry().maxRetries())
                                .initialDelay(Duration.ofMillis(introspector.getRetryExponentialBackoff().initialDelay()))
                                .maxDelay(Duration.ofMillis(introspector.getRetry().maxDuration()))
                                .factor(introspector.getRetryExponentialBackoff().factor())
                                .jitter(introspector.getRetry().jitter())
                                .build())
                        .overallTimeout(Duration.ofMillis(introspector.getRetry().maxDuration()))
                        .applyOn(mapTypes(introspector.getRetry().retryOn()))
                        .skipOn(mapTypes(introspector.getRetry().abortOn()))
                        .build();
                builder.addRetry(methodState.retry);
            } else if (introspector.hasRetryFibonacciBackoff()) {
                methodState.retry = Retry.builder()
                        .retryPolicy(Retry.FibonacciRetryPolicy.builder()
                                .calls(introspector.getRetry().maxRetries())
                                .initialDelay(Duration.ofMillis(introspector.getRetryFibonacciBackoff().initialDelay()))
                                .maxDelay(Duration.ofMillis(introspector.getRetry().maxDuration()))
                                .jitter(introspector.getRetry().jitter())
                                .build())
                        .overallTimeout(Duration.ofMillis(introspector.getRetry().maxDuration()))
                        .applyOn(mapTypes(introspector.getRetry().retryOn()))
                        .skipOn(mapTypes(introspector.getRetry().abortOn()))
                        .build();
                builder.addRetry(methodState.retry);
            } else {
                methodState.retry = Retry.builder()
                        .retryPolicy(Retry.JitterRetryPolicy.builder()
                                .calls(maxRetries)
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
                builder.addRetry(methodState.retry);
            }
        }

        // Create and add fallback handler for this invocation
        if (introspector.hasFallback()) {
            Fallback<Object> fallback = Fallback.builder()
                    .fallback(throwable -> {
                        fallbackCalled.set(true);
                        FallbackHelper cfb = new FallbackHelper(context, introspector, throwable);
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

            // Wrap supplier with request context setup
            FtSupplier<Object> wrappedSupplier = requestScopeHelper.wrapInScope(supplier);

            CompletableFuture<Object> resultFuture = new CompletableFuture<>();
            if (introspector.isAsynchronous()) {
                // Invoke supplier in new thread and propagate ccl for config
                ClassLoader ccl = Thread.currentThread().getContextClassLoader();
                Single<Object> single = Async.create().invoke(() -> {
                    try {
                        Thread.currentThread().setContextClassLoader(ccl);
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
                    resultFuture.complete(wrappedSupplier.get());
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
            methodState.lock.lock();
            try {
                // Breaker state may have changed since we recorded it last
                methodState.lastBreakerState = methodState.breaker.state();
            } finally {
                methodState.lock.unlock();
            }
        }
    }

    /**
     * Update metrics after method is called and depending on outcome.
     *
     * @param cause Mapped cause or {@code null} if successful.
     */
    private void updateMetricsAfter(Throwable cause) {
        if (!isFaultToleranceMetricsEnabled()) {
            return;
        }

        methodState.lock.lock();
        try {
            // Calculate execution time
            long executionTime = System.nanoTime() - handlerStartNanos;

            // Retries
            if (introspector.hasRetry()) {
                long retryCounter = methodState.retry.retryCounter();
                boolean wasRetried = retryCounter > 0;
                Counter retryRetriesTotal = RetryRetriesTotal.get(introspector.getMethodNameTag());

                // Update retry counter
                if (wasRetried) {
                    retryRetriesTotal.inc(retryCounter);
                }

                // Update retry metrics based on outcome
                if (cause == null) {
                    RetryCallsTotal.get(introspector.getMethodNameTag(),
                            wasRetried ? RetryRetried.TRUE.get() : RetryRetried.FALSE.get(),
                            RetryResult.VALUE_RETURNED.get()).inc();
                } else if (cause instanceof RetryTimeoutException) {
                    RetryCallsTotal.get(introspector.getMethodNameTag(),
                            wasRetried ? RetryRetried.TRUE.get() : RetryRetried.FALSE.get(),
                            RetryResult.MAX_DURATION_REACHED.get()).inc();
                } else {
                    // Exception thrown but not RetryTimeoutException
                    int maxRetries = introspector.getRetry().maxRetries();
                    if (maxRetries == -1) {
                        maxRetries = Integer.MAX_VALUE;
                    }
                    if (retryCounter == maxRetries) {
                        RetryCallsTotal.get(introspector.getMethodNameTag(),
                                wasRetried ? RetryRetried.TRUE.get() : RetryRetried.FALSE.get(),
                                RetryResult.MAX_RETRIES_REACHED.get()).inc();
                    } else if (retryCounter < maxRetries) {
                        RetryCallsTotal.get(introspector.getMethodNameTag(),
                                wasRetried ? RetryRetried.TRUE.get() : RetryRetried.FALSE.get(),
                                RetryResult.EXCEPTION_NOT_RETRYABLE.get()).inc();
                    }
                }
            }

            // Timeout
            if (introspector.hasTimeout()) {
                if (cause instanceof org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException) {
                    TimeoutCallsTotal.get(introspector.getMethodNameTag(),
                            TimeoutTimedOut.TRUE.get()).inc();
                } else {
                    TimeoutCallsTotal.get(introspector.getMethodNameTag(),
                            TimeoutTimedOut.FALSE.get()).inc();
                }
                TimeoutExecutionDuration.get(introspector.getMethodNameTag()).update(executionTime);
            }

            // CircuitBreaker
            if (introspector.hasCircuitBreaker()) {
                Objects.requireNonNull(methodState.breaker);

                if (methodState.lastBreakerState == State.OPEN) {
                    CircuitBreakerCallsTotal.get(introspector.getMethodNameTag(),
                            CircuitBreakerResult.CIRCUIT_BREAKER_OPEN.get()).inc();
                } else if (methodState.breaker.state() == State.OPEN) {     // closed -> open
                    CircuitBreakerOpenedTotal.get(introspector.getMethodNameTag()).inc();
                }

                if (cause == null) {
                    CircuitBreakerCallsTotal.get(introspector.getMethodNameTag(),
                            CircuitBreakerResult.SUCCESS.get()).inc();
                } else if (!(cause instanceof CircuitBreakerOpenException)) {
                    boolean skipOnThrowable = Arrays.stream(introspector.getCircuitBreaker().skipOn())
                            .anyMatch(c -> c.isAssignableFrom(cause.getClass()));
                    boolean failOnThrowable = Arrays.stream(introspector.getCircuitBreaker().failOn())
                            .anyMatch(c -> c.isAssignableFrom(cause.getClass()));

                    if (skipOnThrowable || !failOnThrowable) {
                        CircuitBreakerCallsTotal.get(introspector.getMethodNameTag(),
                                CircuitBreakerResult.SUCCESS.get()).inc();
                    } else {
                        CircuitBreakerCallsTotal.get(introspector.getMethodNameTag(),
                                CircuitBreakerResult.FAILURE.get()).inc();
                    }
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
                Counter bulkheadAccepted = BulkheadCallsTotal.get(introspector.getMethodNameTag(),
                        BulkheadResult.ACCEPTED.get());
                if (stats.callsAccepted() > bulkheadAccepted.getCount()) {
                    bulkheadAccepted.inc(stats.callsAccepted() - bulkheadAccepted.getCount());
                }
                Counter bulkheadRejected = BulkheadCallsTotal.get(introspector.getMethodNameTag(),
                        BulkheadResult.REJECTED.get());
                if (stats.callsRejected() > bulkheadRejected.getCount()) {
                    bulkheadRejected.inc(stats.callsRejected() - bulkheadRejected.getCount());
                }

                // Update histograms if task accepted
                if (!(cause instanceof BulkheadException)) {
                    long waitingTime = invocationStartNanos - handlerStartNanos;
                    BulkheadRunningDuration.get(introspector.getMethodNameTag())
                            .update(executionTime - waitingTime);
                    if (introspector.isAsynchronous()) {
                        BulkheadWaitingDuration.get(introspector.getMethodNameTag()).update(waitingTime);
                    }
                }
            }

            // Global method counters
            if (cause == null) {
                InvocationsTotal.get(introspector.getMethodNameTag(),
                        VALUE_RETURNED.get(),
                        introspector.getFallbackTag(fallbackCalled.get())).inc();
            } else {
                InvocationsTotal.get(introspector.getMethodNameTag(),
                        EXCEPTION_THROWN.get(),
                        introspector.getFallbackTag(fallbackCalled.get())).inc();
            }
        } finally {
            methodState.lock.unlock();
        }
    }
}
