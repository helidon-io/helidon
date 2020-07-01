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

package io.helidon.faulttolerance;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

import static io.helidon.faulttolerance.ResultWindow.Result.FAILURE;
import static io.helidon.faulttolerance.ResultWindow.Result.SUCCESS;

public class CircuitBreakerImpl implements CircuitBreaker {
    /*
     Configuration options
     */
    private final LazyValue<? extends ScheduledExecutorService> executor;
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
    private final AtomicReference<ScheduledFuture<Boolean>> schedule = new AtomicReference<>();
    private final ErrorChecker errorChecker;

    CircuitBreakerImpl(CircuitBreaker.Builder builder) {
        this.delayMillis = builder.delay().toMillis();
        this.successThreshold = builder.successThreshold();
        this.results = new ResultWindow(builder.volume(), builder.errorRatio());
        this.executor = builder.executor();
        this.errorChecker = ErrorChecker.create(builder.skipOn(), builder.applyOn());
    }

    @Override
    public <T> Multi<T> invokeMulti(Supplier<? extends Flow.Publisher<T>> supplier) {
        return invokeTask(DelayedTask.createMulti(supplier));
    }

    @Override
    public <T> Single<T> invoke(Supplier<? extends CompletionStage<T>> supplier) {
        return invokeTask(DelayedTask.createSingle(supplier));
    }

    private <U> U invokeTask(DelayedTask<U> task) {
        if (state.get() == State.CLOSED) {
            // run it!
            CompletionStage<Void> completion = task.execute();

            completion.handle((it, throwable) -> {
                Throwable exception = FaultTolerance.cause(throwable);
                if (exception == null || errorChecker.shouldSkip(exception)) {
                    // success
                    results.update(SUCCESS);
                } else {
                    results.update(FAILURE);
                    if (results.shouldOpen() && state.compareAndSet(State.CLOSED, State.OPEN)) {
                        results.reset();
                        // if we successfully switch to open, we need to schedule switch to half-open
                        scheduleHalf();
                    }
                }

                return it;
            });
            return task.result();
        } else if (state.get() == State.OPEN) {
            // fail it!
            return task.error(new CircuitBreakerOpenException("CircuitBreaker is open"));
        } else {
            // half-open
            if (halfOpenInProgress.compareAndSet(false, true)) {
                CompletionStage<Void> result = task.execute();
                result.handle((it, throwable) -> {
                    Throwable exception = FaultTolerance.cause(throwable);
                    if (exception == null || errorChecker.shouldSkip(exception)) {
                        // success
                        int successes = successCounter.incrementAndGet();
                        if (successes >= successThreshold) {
                            // transition to closed
                            successCounter.set(0);
                            state.compareAndSet(State.HALF_OPEN, State.CLOSED);
                            halfOpenInProgress.set(false);
                        }
                        halfOpenInProgress.set(false);
                    } else {
                        // failure
                        successCounter.set(0);
                        state.set(State.OPEN);
                        halfOpenInProgress.set(false);
                        // if we successfully switch to open, we need to schedule switch to half-open
                        scheduleHalf();
                    }

                    return it;
                });
                return task.result();
            } else {
                return task
                        .error(new CircuitBreakerOpenException("CircuitBreaker is half open, parallel execution in progress"));
            }
        }
    }

    private void scheduleHalf() {
        schedule.set(executor.get()
                             .schedule(() -> {
                                 state.compareAndSet(State.OPEN, State.HALF_OPEN);
                                 schedule.set(null);
                                 return true;
                             }, delayMillis, TimeUnit.MILLISECONDS));
    }

    public State state() {
        return state.get();
    }

    public void state(State newState) {
        if (newState == State.CLOSED) {
            if (state.get() == State.CLOSED) {
                // fine
                resetCounters();
                return;
            }

            ScheduledFuture<Boolean> future = schedule.getAndSet(null);
            if (future != null) {
                future.cancel(false);
            }
            resetCounters();
            state.set(State.CLOSED);
        } else if (newState == State.OPEN) {
            state.set(State.OPEN);
            ScheduledFuture<Boolean> future = schedule.getAndSet(null);
            if (future != null) {
                future.cancel(false);
            }
            resetCounters();
        } else {
            // half open
            resetCounters();
        }
    }

    private void resetCounters() {
        results.reset();
        successCounter.set(0);
    }
}
