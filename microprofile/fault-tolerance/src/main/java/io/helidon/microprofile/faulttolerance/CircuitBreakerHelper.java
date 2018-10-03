/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;

/**
 * A CircuitBreakerHelper keeps track of internal states, success and failure
 * ratios for, etc. for all commands. Similar computations are done internally
 * in Hystrix, but we cannot easily access them.
 */
public class CircuitBreakerHelper {

    /**
     * Internal state of a circuit breaker. We need to track this to implement
     * a different HALF_OPEN_MP to CLOSED_MP transition than the default in Hystrix.
     */
    enum State {
        CLOSED_MP(0),
        HALF_OPEN_MP(1),
        OPEN_MP(2);

        private int value;

        State(int value) {
            this.value = value;
        }
    }

    static class CommandData {

        private int size;

        private final boolean[] results;

        private State state = State.CLOSED_MP;

        private int successCount;

        private long[] inStateNanos = new long[State.values().length];

        private long lastNanosRead;

        CommandData(int capacity) {
            results = new boolean[capacity];
            size = 0;
            successCount = 0;
            lastNanosRead = System.nanoTime();
        }

        State getState() {
            return state;
        }

        void setState(State newState) {
            if (state != newState) {
                updateStateNanos(state);
                state = newState;
            }
        }

        long getInStateNanos(State queryState) {
            if (state == queryState) {
                updateStateNanos(state);
            }
            return inStateNanos[queryState.value];
        }

        private void updateStateNanos(State state) {
            long currentNanos = System.nanoTime();
            inStateNanos[state.value] += currentNanos - lastNanosRead;
            lastNanosRead = currentNanos;
        }

        int getSuccessCount() {
            return successCount;
        }

        void incSuccessCount() {
            successCount++;
        }

        boolean isAtCapacity() {
            return size == results.length;
        }

        void pushResult(boolean result) {
            if (isAtCapacity()) {
                shift();
            }
            results[size++] = result;
        }

        double getSuccessRatio() {
            if (isAtCapacity()) {
                int success = 0;
                for (int k = 0; k < size; k++) {
                    if (results[k]) success++;
                }
                return ((double) success) / size;
            }
            return -1.0;
        }

        double getFailureRatio() {
            double successRatio = getSuccessRatio();
            return successRatio >= 0.0 ? 1.0 - successRatio : -1.0;
        }

        private void shift() {
            if (size > 0) {
                for (int k = 0; k < size - 1; k++) {
                    results[k] = results[k + 1];
                }
                size--;
            }
        }
    }

    private static final Map<String, CommandData> COMMAND_STATS = new ConcurrentHashMap<>();

    private final FaultToleranceCommand command;

    private final CommandData commandData;

    CircuitBreakerHelper(FaultToleranceCommand command, CircuitBreaker circuitBreaker) {
        this.command = command;
        this.commandData = COMMAND_STATS.computeIfAbsent(
            command.getCommandKey().toString(),
            d -> new CommandData(circuitBreaker.requestVolumeThreshold()));
    }

    /**
     * Push a new result into the current window. Discards oldest result
     * if window is full.
     *
     * @param result New result to push.
     */
    void pushResult(boolean result) {
        commandData.pushResult(result);
    }

    /**
     * Computes success ratio over a complete window.
     *
     * @return Success ratio or -1 if window is not complete.
     */
    double getSuccessRatio() {
        return commandData.getSuccessRatio();
    }

    /**
     * Computes failure ratio over a complete window.
     *
     * @return Failure ratio or -1 if window is not complete.
     */
    double getFailureRatio() {
        return commandData.getFailureRatio();
    }

    /**
     * Returns state of circuit breaker.
     *
     * @return The state.
     */
    State getState() {
        return commandData.getState();
    }

    /**
     * Changes the state of a circuit breaker.
     * @param newState
     */
    void setState(State newState) {
        commandData.setState(newState);
    }

    /**
     * Gets success count for breaker.
     *
     * @return Success count.
     */
    int getSuccessCount() {
        return commandData.getSuccessCount();
    }

    /**
     * Increments success counter for breaker.
     */
    void incSuccessCount() {
        commandData.incSuccessCount();
    }

    /**
     * Returns underlying object for sync purposes only.
     *
     * @return Command data as an object.
     */
    Object getSyncObject() {
        return commandData;
    }

    /**
     * Returns nanos spent on each state.
     *
     * @param queryState The state.
     * @return The time spent in nanos.
     */
    long getInStateNanos(State queryState) {
        return commandData.getInStateNanos(queryState);
    }

    /**
     * Clear underlying command cache.
     */
    static void clearCache() {
        COMMAND_STATS.clear();
    }
}
