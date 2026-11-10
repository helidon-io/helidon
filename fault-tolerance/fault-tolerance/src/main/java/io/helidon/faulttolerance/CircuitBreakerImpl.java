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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Tag;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

@Service.PerInstance(CircuitBreakerConfigBlueprint.class)
class CircuitBreakerImpl implements CircuitBreaker {
    private static final int MAX_NAME_LENGTH = 256;

    /*
     Configuration options
     */
    private final ExecutorService executor;
    // how long to transition from open to half-open
    private final long delayMillis;
    // how many successful calls will close a half-open breaker
    private final int successThreshold;

    /*
    Runtime
     */
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    // rolling window for counting errors to (maybe) open the breaker
    private final ResultWindow results;
    // to close from half-open
    private final AtomicInteger successCounter = new AtomicInteger();
    private final AtomicBoolean halfOpenInProgress = new AtomicBoolean();
    private final AtomicReference<Future<Boolean>> schedule = new AtomicReference<>();
    private final ErrorChecker errorChecker;
    private final boolean applyOnConfigured;
    private final String name;
    private final String eventName;
    private final CircuitBreakerConfig config;
    private final boolean metricsEnabled;

    private Counter callsCounterMetric;
    private Counter openedCounterMetric;

    CircuitBreakerImpl(CircuitBreakerConfig config,
                       Supplier<MeterRegistry> meterRegistry) {
        this(config, meterRegistry, MetricsUtils.defaultEnabled());
    }

    @Service.Inject
    CircuitBreakerImpl(CircuitBreakerConfig config,
                       Supplier<MeterRegistry> meterRegistry,
                       ServiceRegistry serviceRegistry) {
        this(config, meterRegistry, MetricsUtils.defaultEnabled(serviceRegistry));
    }

    private CircuitBreakerImpl(CircuitBreakerConfig config,
                               Supplier<MeterRegistry> meterRegistry,
                               boolean metricsDefaultEnabled) {
        this.delayMillis = config.delay().toMillis();
        this.successThreshold = config.successThreshold();
        this.results = new ResultWindow(config.volume(), config.errorRatio());
        this.executor = config.executor().orElseGet(FaultTolerance.executor());
        this.errorChecker = ErrorChecker.create(config.skipOn(), config.applyOn());
        this.applyOnConfigured = !config.applyOn().isEmpty();
        this.name = config.name().orElseGet(() -> "circuit-breaker-" + System.identityHashCode(config));
        String configuredName = config.name().orElse(null);
        this.eventName = configuredName == null || configuredName.length() <= MAX_NAME_LENGTH
                ? configuredName : configuredName.substring(0, MAX_NAME_LENGTH);
        this.config = config;

        this.metricsEnabled = config.enableMetrics() || metricsDefaultEnabled;
        if (metricsEnabled) {
            var mr = meterRegistry.get();
            var mf = mr.metricsFactory();
            Tag nameTag = MetricsUtils.tag(mf, "name", name);
            callsCounterMetric = MetricsUtils.counterBuilder(mf, mr, FT_CIRCUITBREAKER_CALLS_TOTAL, nameTag);
            openedCounterMetric = MetricsUtils.counterBuilder(mf, mr, FT_CIRCUITBREAKER_OPENED_TOTAL, nameTag);
        }
    }

    @Override
    public CircuitBreakerConfig prototype() {
        return config;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public <T> T invoke(Supplier<? extends T> supplier) {
        if (metricsEnabled) {
            callsCounterMetric.increment();
        }
        return switch (state.get()) {
            case CLOSED -> executeTask(supplier);
            case HALF_OPEN -> halfOpenTask(supplier);
            case OPEN -> throw new CircuitBreakerOpenException("CircuitBreaker is open");
        };
    }

    @Override
    public State state() {
        return state.get();
    }

    @Override
    public void state(State newState) {
        if (newState == State.CLOSED) {
            if (state.get() == State.CLOSED) {
                // fine
                resetCounters();
                return;
            }

            Future<Boolean> future = schedule.getAndSet(null);
            if (future != null) {
                future.cancel(false);
            }
            resetCounters();
            changeState(State.CLOSED, "MANUAL");
        } else if (newState == State.OPEN) {
            changeState(State.OPEN, "MANUAL");
            Future<Boolean> future = schedule.getAndSet(null);
            if (future != null) {
                future.cancel(false);
            }
            resetCounters();
        } else {
            // half open
            resetCounters();
        }
    }

    Future<Boolean> schedule() {
        return schedule.get();
    }

    private static boolean callerCancelled(Throwable throwable) {
        return !(throwable instanceof Error)
                && Thread.currentThread().isInterrupted()
                && SupplierHelper.interrupted(throwable) != null;
    }

    private <U> U executeTask(Supplier<? extends U> supplier) {
        try {
            U result = supplier.get();
            results.update(ResultWindow.Result.SUCCESS);
            return result;
        } catch (Throwable t) {
            Throwable throwable = SupplierHelper.unwrapThrowable(t);
            boolean shouldSkip = errorChecker.shouldSkip(throwable);
            if (callerCancelled(t) && (!applyOnConfigured || shouldSkip)) {
                throw SupplierHelper.toRuntimeException(throwable);
            }
            if (shouldSkip) {
                results.update(ResultWindow.Result.SUCCESS);
            } else {
                results.update(ResultWindow.Result.FAILURE);
            }
            throw SupplierHelper.toRuntimeException(throwable);
        } finally {
            if (results.shouldOpen() && changeState(State.CLOSED, State.OPEN, "FAILURE_THRESHOLD")) {
                results.reset();
                // if we successfully switch to open, we need to schedule switch to half-open
                scheduleHalf();
                // update metrics for this transition
                if (metricsEnabled) {
                    openedCounterMetric.increment();
                }
            }
        }
    }

    private <U> U halfOpenTask(Supplier<? extends U> supplier) {
        // half-open
        if (halfOpenInProgress.compareAndSet(false, true)) {
            try {
                U result = supplier.get();
                // success
                int successes = successCounter.incrementAndGet();
                if (successes >= successThreshold) {
                    // transition to closed
                    successCounter.set(0);
                    changeState(State.HALF_OPEN, State.CLOSED, "SUCCESS_THRESHOLD");
                }
                return result;
            } catch (Throwable t) {
                Throwable throwable = SupplierHelper.unwrapThrowable(t);
                boolean shouldSkip = errorChecker.shouldSkip(throwable);
                if (callerCancelled(t) && (!applyOnConfigured || shouldSkip)) {
                    throw SupplierHelper.toRuntimeException(throwable);
                }
                if (shouldSkip) {
                    // success
                    int successes = successCounter.incrementAndGet();
                    if (successes >= successThreshold) {
                        // transition to closed
                        successCounter.set(0);
                        changeState(State.HALF_OPEN, State.CLOSED, "SUCCESS_THRESHOLD");
                    }
                } else {
                    // failure
                    successCounter.set(0);
                    changeState(State.OPEN, "HALF_OPEN_FAILURE");
                    // if we successfully switch to open, we need to schedule switch to half-open
                    scheduleHalf();
                }
                throw SupplierHelper.toRuntimeException(throwable);
            } finally {
                halfOpenInProgress.set(false);
            }
        } else {
            throw new CircuitBreakerOpenException("CircuitBreaker is half open, parallel execution in progress");
        }
    }

    private void scheduleHalf() {
        schedule.set(executor.submit(
                FaultTolerance.delayedCallable(() -> {
                    changeState(State.OPEN, State.HALF_OPEN, "DELAY_ELAPSED");
                    schedule.set(null);
                    return true;
                }, delayMillis)));
    }

    private boolean changeState(State expected, State next, String reason) {
        if (state.compareAndSet(expected, next)) {
            stateChanged(expected, next, reason);
            return true;
        }
        return false;
    }

    private void changeState(State next, String reason) {
        State previous = state.getAndSet(next);
        if (previous != next) {
            stateChanged(previous, next, reason);
        }
    }

    private void stateChanged(State previous, State next, String reason) {
        var event = CircuitBreakerStateChangeEvent.create();
        if (event != null) {
            event.record(eventName, previous, next, reason);
        }
    }

    private void resetCounters() {
        results.reset();
        successCounter.set(0);
    }
}
