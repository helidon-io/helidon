/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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

package io.helidon.scheduling;

import java.util.concurrent.ScheduledExecutorService;

/**
 * {@link io.helidon.scheduling.Scheduling Scheduled} task.
 */
public interface Task {
    /**
     * Human-readable description of the task invocation interval.
     *
     * @return interval description
     */
    String description();

    /**
     * {@link java.util.concurrent.ScheduledExecutorService Executor} used for invocation of scheduled tasks.
     *
     * @return used executor
     */
    ScheduledExecutorService executor();

    /**
     * Close the created task. This will cancel the scheduled future.
     */
    default void close() {
    }

    /**
     * ID used to identify this task.
     * It should be unique, as it is used to identify a single task, for example to cancel it.
     *
     * @return task ID
     */
    default String id() {
        // we need a unique id in this VM, this is implemented by Helidon tasks
        return String.valueOf(System.identityHashCode(this));
    }
}
