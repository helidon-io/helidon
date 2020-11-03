/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * https://oss.oracle.com/licenses/upl.
 */

package io.helidon.examples.graphql.basics;

import java.util.UUID;

/**
 * A data class representing a single To Do List task.
 */
public class Task {

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
