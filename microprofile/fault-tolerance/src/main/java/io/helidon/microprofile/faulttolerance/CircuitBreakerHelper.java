/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import com.netflix.config.ConfigurationManager;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;

/**
 * A CircuitBreakerHelper keeps track of internal states, success and failure
 * ratios for, etc. for all commands. Similar computations are done internally
 * in Hystrix, but we cannot easily access them.
 */
public class CircuitBreakerHelper {
    private static final Logger LOGGER = Logger.getLogger(CircuitBreakerHelper.class.getName());

    private static final String FORCE_OPEN = "hystrix.command.%s.circuitBreaker.forceOpen";
    private static final String FORCE_CLOSED = "hystrix.command.%s.circuitBreaker.forceClosed";

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

    /**
     * Data associated with a command for the purpose of tracking a circuit
     * breaker state.
     */
    static class CommandData {

        private int size;

        private final boolean[] results;

        private State state = State.CLOSED_MP;

        private int successCount;

        private long[] inStateNanos = new long[State.values().length];

        private long lastNanosRead;

        private ReentrantLock lock = new ReentrantLock();

        CommandData(int capacity) {
            results = new boolean[capacity];
            size = 0;
            successCount = 0;
            lastNanosRead = System.nanoTime();
        }

        ReentrantLock getLock() {
            return lock;
        }

        State getState() {
            return state;
        }

        long getCurrentStateNanos() {
            return System.nanoTime() - lastNanosRead;
        }

        void setState(State newState) {
            if (state != newState) {
                updateStateNanos(state);
                if (newState == State.HALF_OPEN_MP) {
                    successCount = 0;
                }
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

    private final CircuitBreaker circuitBreaker;

    CircuitBreakerHelper(FaultToleranceCommand command, CircuitBreaker circuitBreaker) {
        this.command = command;
        this.circuitBreaker = circuitBreaker;
    }

    private CommandData getCommandData() {
        return COMMAND_STATS.computeIfAbsent(
                command.getCommandKey().toString(),
                d -> new CommandData(circuitBreaker.requestVolumeThreshold()));
    }

    /**
     * Reset internal state of command data. Normally, this should be called when
     * returning to {@link State#CLOSED_MP} state. Since this is the same as the
     * initial state, we remove it from the map and re-create it later if needed.
     */
    void resetCommandData() {
        ReentrantLock lock = getCommandData().getLock();
        if (lock.isLocked()) {
            lock.unlock();
        }
        COMMAND_STATS.remove(command.getCommandKey().toString());
        LOGGER.info("Discarded command data for " + command.getCommandKey());
    }

    /**
     * Push a new result into the current window. Discards oldest result
     * if window is full.
     *
     * @param result New result to push.
     */
    void pushResult(boolean result) {
        getCommandData().pushResult(result);
    }

    /**
     * Returns nanos since switching to current state.
     *
     * @return Nanos in state.
     */
    long getCurrentStateNanos() {
        return getCommandData().getCurrentStateNanos();
    }

    /**
     * Computes failure ratio over a complete window.
     *
     * @return Failure ratio or -1 if window is not complete.
     */
    double getFailureRatio() {
        return getCommandData().getFailureRatio();
    }

    /**
     * Returns state of circuit breaker.
     *
     * @return The state.
     */
    State getState() {
        return getCommandData().getState();
    }

    /**
     * Changes the state of a circuit breaker.
     *
     * @param newState New state.
     */
    void setState(State newState) {
        getCommandData().setState(newState);
        if (newState == State.OPEN_MP) {
            openBreaker();
        } else {
            closeBreaker();
        }
        LOGGER.info("Circuit breaker for " + command.getCommandKey() + " now in state " + getState());
    }

    /**
     * Gets success count for breaker.
     *
     * @return Success count.
     */
    int getSuccessCount() {
        return getCommandData().getSuccessCount();
    }

    /**
     * Increments success counter for breaker.
     */
    void incSuccessCount() {
        getCommandData().incSuccessCount();
    }

    /**
     * Prevent concurrent access to underlying command data.
     */
    void lock() {
        getCommandData().getLock().lock();
    }

    /**
     * Unlock access to underlying command data.
     */
    void unlock() {
        getCommandData().getLock().unlock();
    }

    /**
     * Returns nanos spent on each state.
     *
     * @param queryState The state.
     * @return The time spent in nanos.
     */
    long getInStateNanos(State queryState) {
        return getCommandData().getInStateNanos(queryState);
    }

    /**
     * Force Hystrix's circuit breaker into an open state.
     */
    private void openBreaker() {
        if (!command.isCircuitBreakerOpen()) {
            ConfigurationManager.getConfigInstance().setProperty(
                    String.format(FORCE_OPEN, command.getCommandKey()), "true");
        }
    }

    /**
     * Force Hystrix's circuit breaker into a closed state.
     */
    private void closeBreaker() {
        if (command.isCircuitBreakerOpen()) {
            ConfigurationManager.getConfigInstance().setProperty(
                    String.format(FORCE_OPEN, command.getCommandKey()), "false");
            ConfigurationManager.getConfigInstance().setProperty(
                    String.format(FORCE_CLOSED, command.getCommandKey()), "true");
        }
    }
}
