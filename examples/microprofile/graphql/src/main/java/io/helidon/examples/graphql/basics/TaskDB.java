/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * https://oss.oracle.com/licenses/upl.
 */

package io.helidon.examples.graphql.basics;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;

/**
 * CDI Bean to work with {@link Task}s.
 */
@ApplicationScoped
public class TaskDB {

    private Map<String, Task> tasks = new ConcurrentHashMap<>();

    /**
     * Create a {@link Task}.
     *
     * @param description task description
     * @return the created {@link Task}
     */
    public Task createTask(String description) {
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
    public Collection<Task> getTasks(Boolean completed) {
        return tasks.values().stream()
                .filter(task -> completed == null || task.isCompleted() == completed)
                .collect(Collectors.toList());
    }

    /**
     * Delete a {@link Task}.
     *
     * @param id task to delete
     * @return the deleted {@link Task}
     * @throws TaskNotFoundException if the task was not found
     */
    public Task deleteTask(String id) throws TaskNotFoundException {
        Task task = tasks.remove(id);
        if (task == null) {
            throw new TaskNotFoundException("Unable to find task with id " + id);
        }
        return task;
    }

    /**
     * Remove all completed {@link Task}s.
     *
     * @return the {@link Task}s left
     */
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
    public Task updateTask(String id, String description, Boolean completed) throws TaskNotFoundException {

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
            throw new TaskNotFoundException("Unable to find task with id " + id);
        }
    }
}
