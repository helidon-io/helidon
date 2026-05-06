/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.common.task;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Api;

import static java.lang.System.Logger.Level.TRACE;

/**
 * Guard for a scoped operation with a deadline.
 * <p>
 * The guard schedules an action to run when the configured timeout expires. Closing the guard cancels the scheduled
 * action if it has not already run.
 */
@Api.Internal
public final class DeadlineGuard implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(DeadlineGuard.class.getName());
    private static final ScheduledExecutorService EXECUTOR = defaultExecutor();

    private final AtomicReference<State> state = new AtomicReference<>(State.OPEN);
    private final AtomicReference<Throwable> timeoutActionFailure = new AtomicReference<>();

    private volatile ScheduledFuture<?> future;

    private DeadlineGuard() {
    }

    /**
     * Creates a new guard and starts its timeout immediately.
     * <p>
     * A zero or negative timeout creates a guard that never expires.
     *
     * @param timeout timeout to wait before invoking the timeout action
     * @param timeoutAction action to invoke when the timeout expires
     * @return active deadline guard
     */
    public static DeadlineGuard create(Duration timeout, Runnable timeoutAction) {
        return create(timeout, timeoutAction, EXECUTOR);
    }

    // Intended for testing: allows deterministic executor injection.
    static DeadlineGuard create(Duration timeout, Runnable timeoutAction, ScheduledExecutorService executor) {
        return create(timeout, timeoutAction, executor, () -> {
        });
    }

    // Intended for testing: observes when the timeout task has won the guard.
    static DeadlineGuard create(Duration timeout,
                                Runnable timeoutAction,
                                ScheduledExecutorService executor,
                                Runnable timeoutClaimed) {
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(timeoutAction, "timeoutAction");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(timeoutClaimed, "timeoutClaimed");

        DeadlineGuard guard = new DeadlineGuard();
        if (timeout.isZero() || timeout.isNegative()) {
            return guard;
        }

        guard.future = executor.schedule(() -> guard.timeout(timeoutAction, timeoutClaimed),
                                         timeoutNanos(timeout),
                                         TimeUnit.NANOSECONDS);
        return guard;
    }

    /**
     * Whether this guard's timeout elapsed and its timeout action was invoked.
     *
     * @return whether the guard timed out
     */
    public boolean timedOut() {
        return state.get() == State.TIMED_OUT;
    }

    /**
     * Failure thrown by the timeout action, if any.
     *
     * @return timeout action failure
     */
    public Optional<Throwable> timeoutActionFailure() {
        return Optional.ofNullable(timeoutActionFailure.get());
    }

    @Override
    public void close() {
        if (state.compareAndSet(State.OPEN, State.CLOSED)) {
            ScheduledFuture<?> scheduled = future;
            if (scheduled != null) {
                scheduled.cancel(false);
            }
        }
    }

    private void timeout(Runnable timeoutAction, Runnable timeoutClaimed) {
        if (!state.compareAndSet(State.OPEN, State.TIMED_OUT)) {
            return;
        }
        timeoutClaimed.run();
        try {
            timeoutAction.run();
        } catch (Throwable t) {
            timeoutActionFailure.compareAndSet(null, t);
            LOGGER.log(TRACE, "Deadline guard timeout action failed", t);
        }
    }

    private static long timeoutNanos(Duration timeout) {
        try {
            return timeout.toNanos();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private static ScheduledThreadPoolExecutor defaultExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                1,
                Thread.ofPlatform()
                        .daemon()
                        .name("helidon-deadline-guard-", 1)
                        .factory());
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private enum State {
        OPEN,
        CLOSED,
        TIMED_OUT
    }
}
