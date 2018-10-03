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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.faulttolerance.Bulkhead;

/**
 * Class BulkheadHelper.
 */
public class BulkheadHelper {

    static class CommandData {

        private final int queueSize;

        private Set<FaultToleranceCommand> runningCommands = new HashSet<>();

        private Set<FaultToleranceCommand> allCommands = new HashSet<>();

        CommandData(int queueSize) {
            this.queueSize = queueSize;
        }

        synchronized boolean isQueueFull() {
            return runningCommands.size() == queueSize;
        }

        synchronized void addCommand(FaultToleranceCommand command) {
            allCommands.add(command);
        }

        synchronized void removeCommand(FaultToleranceCommand command) {
            allCommands.remove(command);
        }

        synchronized int runningCommands() {
            return runningCommands.size();
        }

        synchronized void markAsRunning(FaultToleranceCommand command) {
            runningCommands.add(command);
        }

        synchronized void markAsNotRunning(FaultToleranceCommand command) {
            runningCommands.remove(command);
        }

        synchronized int waitingCommands() {
            return allCommands.size() - runningCommands.size();
        }
    }

    private static final Map<String, CommandData> COMMAND_STATS = new ConcurrentHashMap<>();

    private final FaultToleranceCommand command;

    private final CommandData commandData;

    BulkheadHelper(FaultToleranceCommand command, Bulkhead bulkhead) {
        this.command = command;
        this.commandData = COMMAND_STATS.computeIfAbsent(
            command.getCommandKey().toString(),
            d -> new CommandData(bulkhead.waitingTaskQueue()));
    }

    /**
     * Track a new command instance related to a key.
     *
     * @param command Command instance.
     */
    void addCommand(FaultToleranceCommand command) {
        commandData.addCommand(command);
    }

    /**
     * Stop tracking a command instance.
     *
     * @param command Command instance.
     */
    void removeCommand(FaultToleranceCommand command) {
        commandData.removeCommand(command);
    }

    /**
     * Mark a command instance as running.
     *
     * @param command Command instance.
     */
    void markAsRunning(FaultToleranceCommand command) {
        commandData.markAsRunning(command);
    }

    /**
     * Mark a command instance as terminated.
     *
     * @param command Command instance.
     */
    void markAsNotRunning(FaultToleranceCommand command) {
        commandData.markAsNotRunning(command);
    }

    /**
     * Get the number of commands that are running.
     *
     * @return Number of commands running.
     */
    int runningCommands() {
        return commandData.runningCommands();
    }

    /**
     * Get the number of commands that are waiting.
     *
     * @return Number of commands waiting.
     */
    int waitingCommands() {
        return commandData.waitingCommands();
    }

    /**
     * Check if the command queue is full.
     *
     * @return Outcome of test.
     */
    boolean isQueueFull() {
        return commandData.isQueueFull();
    }

    /**
     * Clear underlying command cache.
     */
    static void clearCache() {
        COMMAND_STATS.clear();
    }
}
