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

package io.helidon.microprofile.graphql.server.test.types;

import org.eclipse.microprofile.graphql.Type;
import java.io.Serializable;
import java.util.UUID;

/**
 * A data class representing a single To Do List task.
 */
@Type
public class Task implements Serializable {

    /**
     * The creation time.
     */
    private long createdAt;

    /**
     * The completion status.
     */
    private boolean completed;

    /**
     * The task ID.
     */
    private String id;

    /**
     * The task description.
     */
    private String description;

    /**
     * Deserialization constructor.
     */
    public Task() {
    }

    /**
     * Construct Task instance.
     *
     * @param description task description
     */
    public Task(String description) {
        this.id = UUID.randomUUID().toString().substring(0, 6);
        this.createdAt = System.currentTimeMillis();
        this.description = description;
        this.completed = false;
    }

    /**
     * Get the creation time.
     *
     * @return the creation time
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Get the task ID.
     *
     * @return the task ID
     */
    public String getId() {
        return id;
    }

    /**
     * Get the task description.
     *
     * @return the task description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the task description.
     *
     * @param description the task description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get the completion status.
     *
     * @return true if it is completed, false otherwise.
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Sets the completion status.
     *
     * @param completed the completion status
     */
    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    @Override
    public String toString() {
        return "Task{"
                + "id=" + id
                + ", description=" + description
                + ", completed=" + completed
                + '}';
    }
}
