/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.Collection;

import io.helidon.service.registry.Service;

/**
 * Manager of scheduled tasks.
 * <p>
 * Each task can be assigned a task manager. If none is assigned, the one in the global
 * {@link io.helidon.service.registry.ServiceRegistry} will be used.
 */
@Service.Contract
public interface TaskManager {
    /**
     * Close all current tasks and remove them from this task manager.
     */
    void shutdown();

    /**
     * Register a task with this task manager.
     *
     * @param task a scheduling task that was started
     */
    void register(Task task);

    /**
     * Remove a task from this task manager.
     *
     * @param task task to remove
     * @return {@code true} if the task was managed by this manager and successfully removed
     */
    boolean remove(Task task);

    /**
     * A collection of tasks currently managed by this manager.
     * As methods on this instance can be called concurrently, the collection returned may not match
     * the tasks managed by this instance in any point of time in the future.
     * <p>
     * The collection returned is not mutable, and is not connected to the underlying task storage.
     *
     * @return a collection of tasks
     */
    Collection<Task> tasks();
}
