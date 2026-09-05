/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Tag;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

class RetryImpl implements Retry {
    private static final int MAX_RETAINED_FAILURES = 16;
    private static final WaitStrategy DEFAULT_WAIT_STRATEGY = new DefaultWaitStrategy();

    private final ErrorChecker errorChecker;
    private final long maxTimeNanos;
    private final Duration overallTimeout;
    private final RetryPolicy retryPolicy;
    private final RetryConfig retryConfig;
    private final AtomicLong retryCounter = new AtomicLong(0L);
    private final String name;
    private final boolean metricsEnabled;

    private Counter callsCounterMetric;
    private Counter retryCounterMetric;

    RetryImpl(RetryConfig retryConfig,
              Supplier<MeterRegistry> meterRegistry) {
        this(retryConfig, meterRegistry, MetricsUtils.defaultEnabled());
    }

    @Service.Inject
    RetryImpl(RetryConfig retryConfig,
              Supplier<MeterRegistry> meterRegistry,
              ServiceRegistry serviceRegistry) {
        this(retryConfig, meterRegistry, MetricsUtils.defaultEnabled(serviceRegistry));
    }

    private RetryImpl(RetryConfig retryConfig,
                      Supplier<MeterRegistry> meterRegistry,
                      boolean metricsDefaultEnabled) {
        this.name = retryConfig.name().orElseGet(() -> "retry-" + System.identityHashCode(retryConfig));
        this.errorChecker = ErrorChecker.create(retryConfig.skipOn(), retryConfig.applyOn());
        this.overallTimeout = retryConfig.overallTimeout();
        this.maxTimeNanos = durationToNanos(overallTimeout);
        this.retryPolicy = retryConfig.retryPolicy().orElseThrow();
        this.retryConfig = retryConfig;

        this.metricsEnabled = retryConfig.enableMetrics() || metricsDefaultEnabled;
        if (metricsEnabled) {
            var mr = meterRegistry.get();
            var mf = mr.metricsFactory();
            Tag nameTag = MetricsUtils.tag(mf, "name", name);
            callsCounterMetric = MetricsUtils.counterBuilder(mf, mr, FT_RETRY_CALLS_TOTAL, nameTag);
            retryCounterMetric = MetricsUtils.counterBuilder(mf, mr, FT_RETRY_RETRIES_TOTAL, nameTag);
        }
    }

    @Override
    public RetryConfig prototype() {
        return retryConfig;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public <T> T invoke(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier);
        return invoke(context -> supplier.get(), DEFAULT_WAIT_STRATEGY, true);
    }

    @Override
    public <T> T invoke(Function<RetryContext, ? extends T> function) {
        return invoke(function, DEFAULT_WAIT_STRATEGY, false);
    }

    @Override
    public <T> T invoke(Function<RetryContext, ? extends T> function, WaitStrategy waitStrategy) {
        return invoke(function, waitStrategy, false);
    }

    @Override
    public long retryCounter() {
        return retryCounter.get();
    }

    private static long durationToNanos(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException e) {
            return duration.isNegative() ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
    }

    private static long millisToNanos(long millis) {
        if (millis > Long.MAX_VALUE / 1_000_000) {
            return Long.MAX_VALUE;
        }
        return TimeUnit.MILLISECONDS.toNanos(millis);
    }

    private static long elapsedNanos(long startedNanos, long now) {
        return Math.max(0, now - startedNanos);
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable unwrapped = SupplierHelper.unwrapThrowable(throwable);
        if (unwrapped instanceof ExecutionException) {
            unwrapped = unwrapped.getCause();
        }
        return unwrapped == null ? throwable : unwrapped;
    }

    private static Throwable interrupted(Throwable throwable) {
        Throwable current = throwable;
        for (int i = 0; current != null && i < 64; i++) {
            if (current instanceof InterruptedException) {
                return current;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                return null;
            }
            current = cause;
        }
        return null;
    }

    private static <T> T throwLegacy(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new SupplierException(throwable);
    }

    private <T> T invoke(Function<RetryContext, ? extends T> function,
                         WaitStrategy waitStrategy,
                         boolean legacy) {
        Objects.requireNonNull(function);
        Objects.requireNonNull(waitStrategy);
        InvocationState state = new InvocationState();
        while (true) {
            try {
                if (metricsEnabled) {
                    callsCounterMetric.increment();
                }
                return function.apply(state.context());
            } catch (Throwable t) {
                Throwable throwable = unwrap(t);
                state.failure(throwable);
                Throwable interrupted = throwable instanceof Error ? null : interrupted(t);
                if (interrupted != null) {
                    Thread.currentThread().interrupt();
                    return terminateAfterInvocation(legacy,
                                                    RetryOutcome.Termination.INTERRUPTED,
                                                    state,
                                                    interrupted);
                }
                if (errorChecker.shouldSkip(throwable)) {
                    return terminateAfterInvocation(legacy,
                                                    RetryOutcome.Termination.NOT_RETRYABLE,
                                                    state,
                                                    throwable);
                }
            }

            Optional<Long> maybeDelay;
            try {
                maybeDelay = Objects.requireNonNull(retryPolicy.nextDelayMillis(state.startedMillis,
                                                                                state.previousDelayMillis,
                                                                                state.attempt),
                                                    "Retry policy returned null");
            } catch (RuntimeException t) {
                return terminatePolicyFailure(legacy, state, t);
            }
            if (maybeDelay.isEmpty()) {
                return terminateAfterInvocation(legacy,
                                                RetryOutcome.Termination.RETRIES_EXHAUSTED,
                                                state,
                                                state.lastFailure);
            }

            long delayMillis = maybeDelay.get();
            if (delayMillis < 0) {
                return terminatePolicyFailure(legacy,
                                              state,
                                              new IllegalArgumentException("Retry policy returned a negative delay: "
                                                                                   + delayMillis + " ms"));
            }

            long now = System.nanoTime();
            if (isTimedOut(state, now, delayMillis)) {
                return terminateTimeout(legacy, state, now);
            }

            boolean continueRetries;
            try {
                continueRetries = waitStrategy.await(Duration.ofMillis(delayMillis));
            } catch (RuntimeException t) {
                Throwable interrupted = interrupted(t);
                if (interrupted != null || Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    return terminateWait(legacy,
                                         RetryOutcome.Termination.INTERRUPTED,
                                         state,
                                         interrupted == null ? t : interrupted);
                }
                return terminateWait(legacy, RetryOutcome.Termination.WAIT_FAILED, state, t);
            }

            if (Thread.currentThread().isInterrupted()) {
                return terminateWait(legacy,
                                     RetryOutcome.Termination.INTERRUPTED,
                                     state,
                                     new InterruptedException("Retry wait interrupted"));
            }
            if (!continueRetries) {
                return terminateWait(legacy,
                                     RetryOutcome.Termination.CANCELLED,
                                     state,
                                     state.lastFailure);
            }

            now = System.nanoTime();
            if (isTimedOut(state, now, 0)) {
                return terminateTimeout(legacy, state, now);
            }

            retryCounter.getAndIncrement();
            if (metricsEnabled) {
                retryCounterMetric.increment();
            }
            state.nextAttempt(delayMillis, now);
        }
    }

    private <T> T terminateAfterInvocation(boolean legacy,
                                           RetryOutcome.Termination termination,
                                           InvocationState state,
                                           Throwable cause) {
        if (legacy) {
            return state.throwLegacy();
        }
        throw retryException(termination, state, cause);
    }

    private <T> T terminatePolicyFailure(boolean legacy, InvocationState state, Throwable cause) {
        if (legacy) {
            return throwLegacy(cause);
        }
        throw retryException(RetryOutcome.Termination.RETRY_POLICY_FAILED, state, cause);
    }

    private <T> T terminateWait(boolean legacy,
                                RetryOutcome.Termination termination,
                                InvocationState state,
                                Throwable cause) {
        if (legacy) {
            if (termination == RetryOutcome.Termination.INTERRUPTED) {
                Throwable lastFailure = state.legacyThrowable();
                if (cause != lastFailure) {
                    cause.addSuppressed(lastFailure);
                }
                throw new RuntimeException("Retries interrupted", cause);
            }
            return throwLegacy(cause);
        }
        throw retryException(termination, state, cause);
    }

    private <T> T terminateTimeout(boolean legacy, InvocationState state, long now) {
        String message = timeoutMessage(state, now);
        if (legacy) {
            throw new RetryTimeoutException(message, state.legacyThrowable());
        }
        throw new RetryException(message,
                                 state.outcome(RetryOutcome.Termination.TIMED_OUT, now),
                                 state.lastFailure);
    }

    private RetryException retryException(RetryOutcome.Termination termination,
                                          InvocationState state,
                                          Throwable cause) {
        String message = switch (termination) {
        case RETRIES_EXHAUSTED -> "Retries exhausted";
        case NOT_RETRYABLE -> "Invocation failed with a non-retriable throwable";
        case TIMED_OUT -> "Retry invocation timed out";
        case INTERRUPTED -> "Retry invocation interrupted";
        case CANCELLED -> "Retry invocation cancelled by the wait strategy";
        case WAIT_FAILED -> "Retry wait strategy failed";
        case RETRY_POLICY_FAILED -> "Retry policy failed";
        };
        return new RetryException(message, state.outcome(termination), cause);
    }

    private boolean isTimedOut(InvocationState state, long now, long additionalDelayMillis) {
        long elapsedNanos = elapsedNanos(state.startedNanos, now);
        if (elapsedNanos > maxTimeNanos) {
            return true;
        }
        long delayNanos = millisToNanos(additionalDelayMillis);
        return delayNanos > maxTimeNanos - elapsedNanos;
    }

    private String timeoutMessage(InvocationState state, long now) {
        return "Execution took too long. Already executing for: "
                + TimeUnit.NANOSECONDS.toMillis(elapsedNanos(state.startedNanos, now))
                + " ms, must be lower than overallTimeout duration of: "
                + overallTimeout
                + ".";
    }

    private static final class InvocationState {
        private final long startedMillis = System.currentTimeMillis();
        private final long startedNanos = System.nanoTime();
        private final Deque<Throwable> failures = new ArrayDeque<>(MAX_RETAINED_FAILURES);
        private int attempt = 1;
        private long previousDelayMillis;
        private Throwable lastFailure;
        private RetryContext context = new RetryContextImpl(1, Duration.ZERO, Duration.ZERO, null);

        private RetryContext context() {
            return context;
        }

        private RetryOutcome outcome(RetryOutcome.Termination termination) {
            return outcome(termination, System.nanoTime());
        }

        private RetryOutcome outcome(RetryOutcome.Termination termination, long now) {
            return new RetryOutcomeImpl(termination,
                                        attempt,
                                        Duration.ofNanos(elapsedNanos(startedNanos, now)),
                                        Duration.ofMillis(previousDelayMillis),
                                        lastFailure);
        }

        private void failure(Throwable failure) {
            lastFailure = failure;
            if (failures.size() == MAX_RETAINED_FAILURES) {
                failures.removeFirst();
            }
            failures.addLast(failure);
        }

        private void nextAttempt(long delayMillis, long now) {
            previousDelayMillis = delayMillis;
            if (attempt < Integer.MAX_VALUE) {
                attempt++;
            }
            context = new RetryContextImpl(attempt,
                                           Duration.ofNanos(elapsedNanos(startedNanos, now)),
                                           Duration.ofMillis(previousDelayMillis),
                                           lastFailure);
        }

        private <T> T throwLegacy() {
            return RetryImpl.throwLegacy(legacyThrowable());
        }

        private Throwable legacyThrowable() {
            for (Throwable failure : failures) {
                if (failure != lastFailure) {
                    lastFailure.addSuppressed(failure);
                }
            }
            return lastFailure;
        }
    }

    private static final class WaitInterruptedException extends RuntimeException {
        private static final long serialVersionUID = 2858492180285840898L;

        private WaitInterruptedException(InterruptedException cause) {
            super(cause);
        }
    }

    private static final class DefaultWaitStrategy implements WaitStrategy {
        @Override
        public boolean await(Duration delay) {
            try {
                Thread.sleep(delay);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new WaitInterruptedException(e);
            }
        }
    }
}
