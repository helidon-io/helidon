/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.faulttolerance.AsyncConfig;
import io.helidon.faulttolerance.Bulkhead;
import io.helidon.faulttolerance.CircuitBreaker;
import io.helidon.faulttolerance.CircuitBreaker.State;
import io.helidon.faulttolerance.Fallback;
import io.helidon.faulttolerance.FaultTolerance;
import io.helidon.faulttolerance.FtHandlerTyped;
import io.helidon.faulttolerance.Retry;
import io.helidon.faulttolerance.RetryTimeoutException;
import io.helidon.faulttolerance.Timeout;

import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.interceptor.InvocationContext;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceException;
import org.eclipse.microprofile.metrics.Counter;

import static io.helidon.faulttolerance.SupplierHelper.toRuntimeException;
import static io.helidon.faulttolerance.SupplierHelper.unwrapThrowable;
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
     * Maps a {@code MethodStateKey} to a {@code MethodState}. The method state returned
     * caches the FT handler as well as some additional variables. This mapping must
     * be shared by all instances of this class.
     */
    private static final ConcurrentHashMap<MethodStateKey, MethodState> METHOD_STATES = new ConcurrentHashMap<>();
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
     * Helidon context in which to run business method.
     */
    private final Context helidonContext;
    /**
     * A boolean value indicates whether the fallback logic was called or not
     * on this invocation.
     */
    private final AtomicBoolean fallbackCalled = new AtomicBoolean(false);
    /**
     * Helper to properly propagate active request scope to other threads.
     */
    private final RequestScopeHelper requestScopeHelper;
    /**
     * FT handler for this invoker.
     */
    private final FtHandlerTyped<Object> handler;
    /**
     * State associated with a method instead of an invocation. Shared by all
     * invocations of same method.
     */
    private final MethodState methodState;
    /**
     * Start system nanos when handler is called.
     */
    private long handlerStartNanos;
    /**
     * Start system nanos when method {@code proceed()} is called.
     */
    private long invocationStartNanos;
    /**
     * Wraps method invocation in a supplier that can be cancelled. This is required
     * when a task is cancelled without its thread being interrupted.
     */
    private CancellableFtSupplier<Object> cancellableSupplier;

    /**
     * The {@code Supplier} passed to the FT handlers for execution.
     */
    private Supplier<?> handlerSupplier;

    /**
     * Constructor.
     *
     * @param context The invocation context.
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

    /**
     * Clears {@code METHOD_STATES} map.
     */
    static void clearMethodStatesMap() {
        METHOD_STATES.clear();
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
     * Invokes a method with one or more FT annotations. This method shall execute synchronously
     * or asynchronously w.r.t. its caller based on the nature of the intercepted method.
     *
     * @return value returned by method.
     */
    @Override
    public Object get() throws Throwable {
        // Supplier that shall be passed to FT handlers
        handlerSupplier = ftSupplierToSupplier(introspector.isAsynchronous()
                                                       ? asyncToSyncFtSupplier(context::proceed) : context::proceed);

        // Wrap supplier with Helidon context info
        FtSupplier<Object> contextSupplier = () ->
                Contexts.runInContextWithThrow(helidonContext,
                                               () -> ftSupplierToSupplier(() -> handler.invoke(handlerSupplier)).get());

        updateMetricsBefore();

        if (introspector.isAsynchronous()) {
            return callSupplierNewThread(contextSupplier);
        } else {
            Object result = null;
            Throwable throwable = null;
            try {
                result = callSupplier(contextSupplier);
            } catch (Throwable t) {
                throwable = t;
            }
            updateMetricsAfter(throwable);
            if (throwable != null) {
                if (throwable instanceof RetryTimeoutException rte) {
                    throw rte.lastRetryException();
                }
                throw throwable;
            }
            return result;
        }
    }

    /**
     * Converts an async supplier into a sync one by waiting on the async supplier
     * to produce an actual result. Will block thread indefinitely until such value
     * becomes available. Wraps supplier with cancellable supplier for async
     * cancellations.
     *
     * @param supplier async supplier
     * @return value produced by supplier
     * @param <T> type of value produced
     */
    @SuppressWarnings("unchecked")
    public <T> FtSupplier<T> asyncToSyncFtSupplier(FtSupplier<Object> supplier) {
        cancellableSupplier = CancellableFtSupplier.create(supplier);
        return () -> {
            Object result = cancellableSupplier.get();
            if (result instanceof CompletionStage<?> cs) {
                return (T) cs.toCompletableFuture().get();
            } else if (result instanceof Future<?> f) {
                return (T) f.get();
            } else {
                throw new InternalError("Supplier must return Future or CompletionStage");
            }
        };
    }

    /**
     * Maps an {@link FtSupplier} to a {@link Supplier}.
     *
     * @param supplier The supplier.
     * @return The new supplier.
     */
    Supplier<?> ftSupplierToSupplier(FtSupplier<Object> supplier) {
        return () -> {
            try {
                invocationStartNanos = System.nanoTime();       // record start
                return supplier.get();
            } catch (Throwable t) {
                throw toRuntimeException(t);
            }
        };
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

    private Object callSupplier(FtSupplier<Object> supplier) throws Throwable {
        Object result = null;
        Throwable cause = null;
        try {
            invocationStartNanos = System.nanoTime();
            result = supplier.get();
        } catch (Throwable t) {
            cause = map(unwrapThrowable(t));
        }
        if (cause != null) {
            throw cause;
        }
        return result;
    }

    private CompletableFuture<Object> callSupplierNewThread(FtSupplier<Object> supplier) {
        FtSupplier<Object> wrappedSupplier = requestScopeHelper.wrapInScope(supplier);

        // Call supplier in new thread
        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        AsyncConfig.Builder asyncBuilder = AsyncConfig.builder();

        // Handle a user-provided executor via @WithExecutor
        if (introspector.hasWithExecutor()) {
            WithExecutor withExecutor = introspector.withExecutor();
            try {
                ExecutorService executorService = CDI.current().select(ExecutorService.class, withExecutor).get();
                asyncBuilder.executor(executorService);
            } catch (UnsatisfiedResolutionException e) {
                throw new FaultToleranceException(e);
            }
        }

        // Invoke async call
        CompletableFuture<Object> asyncFuture = asyncBuilder.build().invoke(() -> {
            Thread.currentThread().setContextClassLoader(ccl);
            try {
                return callSupplier(wrappedSupplier);
            } catch (Throwable t) {
                throw toRuntimeException(t);
            }
        });

        // Set resultFuture based on supplier's outcome
        AtomicBoolean mayInterrupt = new AtomicBoolean(false);
        CompletableFuture<Object> resultFuture = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                mayInterrupt.set(mayInterruptIfRunning);
                return super.cancel(mayInterruptIfRunning);
            }
        };
        asyncFuture.whenComplete((result, throwable) -> {
            requestScopeHelper.clearScope();
            Throwable cause = unwrapThrowable(throwable);
            updateMetricsAfter(cause);
            if (throwable != null) {
                resultFuture.completeExceptionally(cause);
            } else {
                resultFuture.complete(result);
            }
        });

        // If resultFuture is cancelled, then cancel supplier call
        resultFuture.exceptionally(t -> {
            if (t instanceof CancellationException
                    || t instanceof org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException) {
                Objects.requireNonNull(cancellableSupplier);
                cancellableSupplier.cancel();
                // Cancel supplier in bulkhead in case it is queued
                if (introspector.hasBulkhead()) {
                    methodState.bulkhead.cancelSupplier(handlerSupplier);
                }
                asyncFuture.cancel(mayInterrupt.get());
            }
            return null;
        });

        return resultFuture;
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
            methodState.bulkhead = Bulkhead.create(builder -> builder.limit(introspector.getBulkhead().value())
                    .queueLength(introspector.isAsynchronous() ? introspector.getBulkhead().waitingTaskQueue() : 0));
        }

        if (introspector.hasTimeout()) {
            methodState.timeout = Timeout.create(builder -> builder.timeout(Duration.of(introspector.getTimeout().value(),
                                                                                        introspector.getTimeout().unit()))
                    .currentThread(!introspector.isAsynchronous()));
        }

        if (introspector.hasCircuitBreaker()) {
            methodState.breaker = CircuitBreaker.create(builder -> builder.delay(Duration.of(introspector.getCircuitBreaker()
                                                                                                     .delay(),
                                                                                             introspector.getCircuitBreaker()
                                                                                                     .delayUnit()))
                    .successThreshold(introspector.getCircuitBreaker().successThreshold())
                    .errorRatio((int) (introspector.getCircuitBreaker().failureRatio() * 100))
                    .volume(introspector.getCircuitBreaker().requestVolumeThreshold())
                    .applyOn(mapTypes(introspector.getCircuitBreaker().failOn()))
                    .skipOn(mapTypes(introspector.getCircuitBreaker().skipOn())));
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
            int maxRetries = calls(introspector.getRetry().maxRetries());

            methodState.retry = Retry.create(retryBuilder -> retryBuilder
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
                    .skipOn(mapTypes(introspector.getRetry().abortOn())));
            builder.addRetry(methodState.retry);
        }

        // Create and add fallback handler for this invocation
        if (introspector.hasFallback()) {
            Fallback<Object> fallback = Fallback.create(fallbackBuilder -> fallbackBuilder
                    .fallback(throwable -> {
                        FallbackHelper cfb = new FallbackHelper(context, introspector, throwable);

                        // Fallback executed in another thread
                        if (introspector.isAsynchronous()) {
                            // In a reactive env, we shouldn't block on a Future, so the FT spec
                            // states not to fallback in this case -- even though we can with VTs
                            // Note if method throws exception directly, fallback is required.
                            if (method.getReturnType().equals(Future.class)
                                    && throwable instanceof ExecutionException) {       // exception from Future
                                throw toRuntimeException(throwable);
                            }

                            CompletableFuture<?> f = callSupplierNewThread(asyncToSyncFtSupplier(cfb::execute));
                            try {
                                fallbackCalled.set(true);
                                return f.get();
                            } catch (Throwable t) {
                                throw toRuntimeException(t);
                            }
                        } else {
                            try {
                                fallbackCalled.set(true);
                                return callSupplier(cfb::execute);
                            } catch (Throwable t) {
                                throw toRuntimeException(t);
                            }
                        }
                    })
                    .applyOn(mapTypes(introspector.getFallback().applyOn()))
                    .skipOn(mapTypes(introspector.getFallback().skipOn())));
            builder.addFallback(fallback);
        }

        return builder.build();
    }

    private int calls(int configuredMaxRetries) {
        // add 1 for initial call
        return configuredMaxRetries == -1 ? Integer.MAX_VALUE : configuredMaxRetries + 1;
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

    /**
     * State associated with a method in {@code METHOD_STATES}.
     */
    private static class MethodState {
        private final ReentrantLock lock = new ReentrantLock();
        private Retry retry;
        private Bulkhead bulkhead;
        private CircuitBreaker breaker;
        private Timeout timeout;
        private State lastBreakerState;
        private long breakerTimerOpen;
        private long breakerTimerClosed;
        private long breakerTimerHalfOpen;
        private long startNanos;
    }

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
}
