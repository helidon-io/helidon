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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.faulttolerance.Bulkhead;

/**
 * Helper class to keep track of invocations associated with a bulkhead.
 */
public class BulkheadHelper {

    /**
     * A command ID is unique to a target (object) and method. A {@link
     * FaultToleranceCommand} instance is created for each invocation of that
     * target/method pair and is assigned the same invocation ID. This class
     * collects information about all those invocations, including their state:
     * waiting or running.
     */
    static class InvocationData {

        /**
         * Maximum number of concurrent invocations.
         */
        private final int maxRunningInvocations;

        /**
         * The waiting queue size.
         */
        private final int waitingQueueSize;

        /**
         * All invocations in running state. Must be a subset of {@link #allInvocations}.
         */
        private Set<FaultToleranceCommand> runningInvocations = new HashSet<>();

        /**
         * All invocations associated with a invocation.
         */
        private Set<FaultToleranceCommand> allInvocations = new HashSet<>();

        InvocationData(int maxRunningCommands, int waitingQueueSize) {
            this.maxRunningInvocations = maxRunningCommands;
            this.waitingQueueSize = waitingQueueSize;
        }

        synchronized boolean isWaitingQueueFull() {
            return waitingInvocations() == waitingQueueSize;
        }

        synchronized boolean isAtMaxRunningInvocations() {
            return runningInvocations.size() == maxRunningInvocations;
        }

        synchronized void trackInvocation(FaultToleranceCommand invocation) {
            allInvocations.add(invocation);
        }

        synchronized void untrackInvocation(FaultToleranceCommand invocation) {
            allInvocations.remove(invocation);
        }

        synchronized int runningInvocations() {
            return runningInvocations.size();
        }

        synchronized void markAsRunning(FaultToleranceCommand invocation) {
            runningInvocations.add(invocation);
        }

        synchronized void markAsNotRunning(FaultToleranceCommand invocation) {
            runningInvocations.remove(invocation);
        }

        synchronized int waitingInvocations() {
            return allInvocations.size() - runningInvocations.size();
        }
    }

    /**
     * Tracks all invocations associated with a command ID.
     */
    private static final Map<String, InvocationData> COMMAND_STATS = new ConcurrentHashMap<>();

    /**
     * Command key.
     */
    private final String commandKey;

    /**
     * Annotation instance.
     */
    private final Bulkhead bulkhead;

    BulkheadHelper(String commandKey, Bulkhead bulkhead) {
        this.commandKey = commandKey;
        this.bulkhead = bulkhead;
    }

    private InvocationData invocationData() {
        return COMMAND_STATS.computeIfAbsent(
                commandKey,
                d -> new InvocationData(bulkhead.value(), bulkhead.waitingTaskQueue()));
    }

    /**
     * Track a new invocation instance related to a key.
     */
    void trackInvocation(FaultToleranceCommand invocation) {
        invocationData().trackInvocation(invocation);
    }

    /**
     * Stop tracking a invocation instance.
     */
    void untrackInvocation(FaultToleranceCommand invocation) {
        invocationData().untrackInvocation(invocation);

        // Attempt to cleanup state when not in use
        if (runningInvocations() == 0 && waitingInvocations() == 0) {
            COMMAND_STATS.remove(commandKey);
        }
    }

    /**
     * Mark a invocation instance as running.
     */
    void markAsRunning(FaultToleranceCommand invocation) {
        invocationData().markAsRunning(invocation);
    }

    /**
     * Mark a invocation instance as terminated.
     */
    void markAsNotRunning(FaultToleranceCommand invocation) {
        invocationData().markAsNotRunning(invocation);
    }

    /**
     * Get the number of invocations that are running.
     *
     * @return Number of invocations running.
     */
    int runningInvocations() {
        return invocationData().runningInvocations();
    }

    /**
     * Get the number of invocations that are waiting.
     *
     * @return Number of invocations waiting.
     */
    int waitingInvocations() {
        return invocationData().waitingInvocations();
    }

    /**
     * Check if the invocation queue is full.
     *
     * @return Outcome of test.
     */
    boolean isWaitingQueueFull() {
        return invocationData().isWaitingQueueFull();
    }

    /**
     * Check if at maximum number of running invocations.
     *
     * @return Outcome of test.
     */
    boolean isAtMaxRunningInvocations() {
        return invocationData().isAtMaxRunningInvocations();
    }

    boolean isInvocationRunning(FaultToleranceCommand command) {
        return invocationData().runningInvocations.contains(command);
    }
}
