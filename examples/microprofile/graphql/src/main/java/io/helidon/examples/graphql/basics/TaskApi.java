/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.examples.graphql.basics;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.metrics.annotation.Timed;

/**
 * A CDI Bean that exposes a GraphQL API to query and mutate {@link Task}s.
 */
@GraphQLApi
@ApplicationScoped
public class TaskApi {

    private static final String MESSAGE = "Unable to find task with id ";

    private Map<String, Task> tasks = new ConcurrentHashMap<>();

    /**
     * Create a {@link Task}.
     *
     * @param description task description
     * @return the created {@link Task}
     */
    @Mutation
    @Timed
    @Description("Create a task with the given description")
    public Task createTask(@Name("description") @NonNull String description) {
        if (description == null) {
            throw new IllegalArgumentException("Description must be provided");
        }
        Task task = new Task(description);
        tasks.put(task.getId(), task);
        return task;
    }

    /**
     * Query {@link Task}s.
     *
     * @param completed optionally specify completion status
     * @return a {@link Collection} of {@link Task}s
     */
    @Query
    @Timed
    @Description("Query tasks and optionally specify only completed")
    public Collection<Task> getTasks(@Name("completed") Boolean completed) {
        return tasks.values().stream()
                .filter(task -> completed == null || task.isCompleted() == completed)
                .collect(Collectors.toList());
    }

    /**
     * Return a {@link Task}.
     *
     * @param id task id
     * @return the {@link Task} with the given id
     * @throws TaskNotFoundException if the task was not found
     */
    @Query
    @Timed
    @Description("Return a given task")
    public Task findTask(@Name("id") @NonNull String id) throws TaskNotFoundException {
        return Optional.ofNullable(tasks.get(id))
                .orElseThrow(() -> new TaskNotFoundException(MESSAGE + id));
    }

    /**
     * Delete a {@link Task}.
     *
     * @param id task to delete
     * @return the deleted {@link Task}
     * @throws TaskNotFoundException if the task was not found
     */
    @Mutation
    @Timed
    @Description("Delete a task and return the deleted task details")
    public Task deleteTask(@Name("id") @NonNull String id) throws TaskNotFoundException {
        return Optional.ofNullable(tasks.remove(id))
                  .orElseThrow(() -> new TaskNotFoundException(MESSAGE + id));
    }

    /**
     * Remove all completed {@link Task}s.
     *
     * @return the {@link Task}s left
     */
    @Mutation
    @Timed
    @Description("Remove all completed tasks and return the tasks left")
    public Collection<Task> deleteCompletedTasks() {
        tasks.values().removeIf(Task::isCompleted);
        return tasks.values();
    }

    /**
     * Update a {@link Task}.
     *
     * @param id          task to update
     * @param description optional description
     * @param completed   optional completed
     * @return the updated {@link Task}
     * @throws TaskNotFoundException if the task was not found
     */
    @Mutation
    @Timed
    @Description("Update a task")
    public Task updateTask(@Name("id") @NonNull String id,
                           @Name("description") String description,
                           @Name("completed") Boolean completed) throws TaskNotFoundException {

        try {
            return tasks.compute(id, (k, v) -> {
                Objects.requireNonNull(v);

                if (description != null) {
                    v.setDescription(description);
                }
                if (completed != null) {
                    v.setCompleted(completed);
                }
                return v;
            });
        } catch (Exception e) {
            throw new TaskNotFoundException(MESSAGE + id);
        }
    }
}
