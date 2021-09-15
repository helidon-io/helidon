/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.metrics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages a single-thread executor which will invoke callbacks to enrollees at a frequency they request.
 * <p>
 * Some enrollments might arrive before the manager is started. We save those and act on them once the
 * manager starts. This makes sure the executor's thread starts only at native image runtime.
 * </p>
 * <p>
 * In production use, starting and stopping the executor and even enrolling callbacks are not performance-critical operations,
 * so simple synchronization on methods which access shared data is clear and sufficient.
 * </p>
 */
class PeriodicExecutor {

    static final PeriodicExecutor INSTANCE = create();

    static PeriodicExecutor create() {
        return new PeriodicExecutor();
    }

    private static final Logger LOGGER = Logger.getLogger(PeriodicExecutor.class.getName());

    private static final String STOP_LOG_MESSAGE = "Received stop request in state {0}";

    enum State {
        DORMANT, // never started
        STARTED, // started and still running
        STOPPED  // stopped
    }

    private static class Enrollment {
        private final Runnable runnable;
        private final Duration interval;

        Enrollment(Runnable runnable, Duration interval) {
            this.runnable = runnable;
            this.interval = interval;
        }
    }

    private State state = State.DORMANT;

    private ScheduledExecutorService currentTimeUpdaterExecutorService;

    private final Collection<Enrollment> deferredEnrollments = new ArrayList<>();

    private PeriodicExecutor() {
    }

    static void enroll(Runnable runnable, Duration interval) {
        INSTANCE.enrollRunner(runnable, interval);
    }

    static void start() {
        INSTANCE.startExecutor();
    }

    static void stop() {
        INSTANCE.stopExecutor();
    }

    static State state() {
        return INSTANCE.executorState();
    }

    synchronized void enrollRunner(Runnable runnable, Duration interval) {

        if (state == State.STARTED) {
            currentTimeUpdaterExecutorService.scheduleAtFixedRate(
                    runnable,
                    interval.toMillis(),
                    interval.toMillis(),
                    TimeUnit.MILLISECONDS);
        } else if (state == State.DORMANT) {
            deferredEnrollments.add(new Enrollment(runnable, interval));
        } else {
            LOGGER.log(Level.WARNING,
                    "Attempt to enroll when in unexpected state " + state + "; ignored",
                    new IllegalStateException());
        }
    }

    synchronized void startExecutor() {
        if (state == State.DORMANT) {
            LOGGER.log(Level.FINE, "Starting up with " + deferredEnrollments.size() + " deferred enrollments");
            state = State.STARTED;
            currentTimeUpdaterExecutorService = Executors.newSingleThreadScheduledExecutor();
            for (Enrollment deferredEnrollment : deferredEnrollments) {
                currentTimeUpdaterExecutorService.scheduleAtFixedRate(
                        deferredEnrollment.runnable,
                        deferredEnrollment.interval.toMillis(),
                        deferredEnrollment.interval.toMillis(),
                        TimeUnit.MILLISECONDS);
            }
            deferredEnrollments.clear();
        } else {
            LOGGER.log(Level.WARNING, String.format("Attempt to start; the expected state is %s but found %s; ignored",
                    State.DORMANT,
                    state),
                    new IllegalStateException());
        }
    }

    synchronized void stopExecutor() {
        LOGGER.log(Level.FINE, STOP_LOG_MESSAGE, state);
        switch (state) {
            case STARTED:
                currentTimeUpdaterExecutorService.shutdownNow();
                break;

            case DORMANT:
                break;

            default:
                LOGGER.log(Level.WARNING, String.format(
                        "Unexpected attempt to stop; the expected states are %s but found %s; ignored",
                        Set.of(State.DORMANT, State.STARTED),
                        state),
                        new IllegalStateException());
        }
        state = State.STOPPED;
    }

    State executorState() {
        return state;
    }
}
