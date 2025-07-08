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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.service.registry.Service;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class TaskManagerImpl implements TaskManager {
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();

    TaskManagerImpl() {
    }

    @Override
    public void shutdown() {
        List<Task> allTasks = List.copyOf(tasks.values());
        for (Task task : allTasks) {
            task.close();
            tasks.remove(task.id());
        }
    }

    @Override
    public void register(Task task) {
        tasks.put(task.id(), task);
    }

    @Override
    public boolean remove(Task task) {
        var existing = tasks.remove(task.id());
        return existing != null;
    }

    @Override
    public Collection<Task> tasks() {
        return List.copyOf(tasks.values());
    }
}
