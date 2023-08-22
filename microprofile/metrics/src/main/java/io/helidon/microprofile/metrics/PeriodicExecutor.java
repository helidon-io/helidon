/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages a single-thread executor which will invoke callbacks to enrollees at a frequency they request.
 * <p>
 * Some enrollments might arrive before the manager is started. We save those and act on them once the
 * manager starts. This makes sure the executor's thread starts only at native image runtime.
 * </p>
 */
class PeriodicExecutor {

    static final PeriodicExecutor INSTANCE = create();

    static PeriodicExecutor create() {
        return new PeriodicExecutor();
    }

    private static final System.Logger LOGGER = System.getLogger(PeriodicExecutor.class.getName());

    private static final String STOP_LOG_MESSAGE = "Received stop request in state {0}";

    /**
     * Current state of the executor.
     *
     * Normally, the life cycle is DORMANT -> STARTED -> STOPPED & then JVM exit. Particularly in some testing situations, the
     * singleton PeriodicExecutor might be reused serially with the life cycle DORMANT -> STARTED -> STOPPED -> STARTED -> ...
     */
    enum State {
        DORMANT, // never started
        STARTED, // started and still running
        STOPPED;  // stopped

        boolean isStartable() {
            return this != STARTED;
        }
    }

    private static class Enrollment {
        private final Runnable runnable;
        private final Duration interval;

        Enrollment(Runnable runnable, Duration interval) {
            this.runnable = runnable;
            this.interval = interval;
        }
    }

    private volatile State state = State.DORMANT;

    private ScheduledExecutorService currentTimeUpdaterExecutorService;

    private final Collection<Enrollment> deferredEnrollments = new ArrayList<>();

    private final Lock access = new ReentrantLock(true);

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

    void enrollRunner(Runnable runnable, Duration interval) {
        sync("enroll runner", () -> {
            if (state == State.STARTED) {
                currentTimeUpdaterExecutorService.scheduleAtFixedRate(
                        runnable,
                        interval.toMillis(),
                        interval.toMillis(),
                        TimeUnit.MILLISECONDS);
            } else {
                deferredEnrollments.add(new Enrollment(runnable, interval));
                if (state == State.STOPPED) {
                    // Unusual during production use, more likely during testing. Keep the logging for diagnosing production
                    // problems if they occur.
                    LOGGER.log(Level.DEBUG,
                               "Recording deferred enrollment even though in unexpected state " + State.STOPPED,
                               new IllegalStateException());
                }
            }
        });
    }

    void startExecutor() {
        sync("start", () -> {
            if (state.isStartable()) {
                LOGGER.log(Level.DEBUG, "Starting up with " + deferredEnrollments.size() + " deferred enrollments"
                        + (state == State.DORMANT ? "" : " even though in state " + state));
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
                LOGGER.log(Level.WARNING, String.format("Attempt to start in unexpected state state %s; ignored",
                                                        state),
                           new IllegalStateException());
            }
        });
    }

    void stopExecutor() {
        sync("stop", () -> {
            LOGGER.log(Level.DEBUG, STOP_LOG_MESSAGE, state);
            switch (state) {
            case STARTED:
                currentTimeUpdaterExecutorService.shutdownNow();
                break;

            case DORMANT:
                break;

            default:
                LOGGER.log(Level.DEBUG, String.format(
                        "Unexpected attempt to stop; the expected states are %s but found %s; ignored",
                        Set.of(State.DORMANT, State.STARTED),
                        state),
                           new IllegalStateException());
            }
            state = State.STOPPED;
        });
    }

    State executorState() {
        return state;
    }

    private void sync(String taskDescription, Runnable task) {
        access.lock();
        try {
            task.run();
        } finally {
            access.unlock();
        }
    }
}
