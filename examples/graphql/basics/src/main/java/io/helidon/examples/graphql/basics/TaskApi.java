/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Query;

/**
 * CDI Bean to work with {@link Task}s.
 */
@GraphQLApi
@ApplicationScoped
public class TaskApi {

    @Inject
    private TaskDB taskDB;

    /**
     * Create a {@link Task}.
     *
     * @param description task description
     * @return the created {@link Task}
     */
    @Mutation
    @Description("Create a task with the given description")
    public Task createTask(@Name("description") String description) {
        return taskDB.createTask(description);
    }

    /**
     * Query {@link Task}s.
     *
     * @param completed optionally specify completion status
     * @return a {@link Collection} of {@link Task}s
     */
    @Query
    @Description("Query tasks and optionally specified only completed")
    public Collection<Task> getTasks(@Name("completed") Boolean completed) {
        return taskDB.getTasks(completed);
    }

    /**
     * Delete a {@link Task}.
     *
     * @param id task to delete
     * @return the deleted {@link Task}
     * @throws TaskNotFoundException if the task was not found
     */
    @Mutation
    @Description("Delete a task and return the deleted task details")
    public Task deleteTask(@Name("id") String id) throws TaskNotFoundException {
        return taskDB.deleteTask(id);
    }

    /**
     * Remove all completed {@link Task}s.
     *
     * @return the {@link Task}s left
     */
    @Mutation
    @Description("Remove all completed tasks and return the tasks left")
    public Collection<Task> deleteCompletedTasks() {
        return taskDB.deleteCompletedTasks();
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
    @Description("Update a task")
    public Task updateTask(@Name("id") @NonNull String id,
                           @Name("description") String description,
                           @Name("completed") Boolean completed) throws TaskNotFoundException {
        return taskDB.updateTask(id, description, completed);
    }
}
