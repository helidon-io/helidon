/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.demo.todos.backend;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.UUID;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import com.datastax.driver.core.Row;

/**
 * Data object for backend.
 */
public final class Todo {

    /**
     * Date formatter to format the dates of the TODO entries.
     */
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSVV");

    /**
     * Factory for creating JSON builders.
     */
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    /**
     * The TODO ID.
     */
    private String id;

    /**
     * The user ID associated with this TODO.
     */
    private String userId;

    /**
     * The TODO title.
     */
    private String title;

    /**
     * The TODO completed flag.
     */
    private Boolean completed;

    /**
     * The TODO creation timestamp.
     */
    private long created;

    /**
     * Create a new {@code Todo} instance from a database entry in JSON format.
     * @param jsonObject the database entry
     * @return the created instance
     */
    public static Todo fromDb(final JsonObject jsonObject) {

        Todo result = new Todo();
        result.id = jsonObject.getString("id");
        result.userId = jsonObject.getString("user");
        result.title = jsonObject.getString("message");
        result.completed = jsonObject.getBoolean("completed");
        result.created = Instant.from(DATE_FORMAT
                .parse(jsonObject.getString("created"))).toEpochMilli();
        return result;
    }

    /**
     * Create a new {@code Todo} instance from a REST entry.
     * The created entry will be new, i.e the {@code completed} flag will be set
     * to {@code false} and the {@code created} timestamp set to the current
     * time.
     * @param jsonObject the REST entry
     * @param userId the user ID associated with this entry
     * @param id the entry ID
     * @return the created instance
     */
    public static Todo newTodoFromRest(final JsonObject jsonObject,
                                       final String userId,
                                       final String id) {

        Todo result = new Todo();
        result.id = id;
        result.userId = userId;
        result.title = jsonObject.getString("title");
        result.completed = jsonObject.getBoolean("completed", false);
        result.created = System.currentTimeMillis();
        return result;
    }

    /**
     * Create a new {@code Todo} instance from a REST entry.
     * @param jsonObject the REST entry
     * @param userId the user ID associated with this entry
     * @param id the entry ID
     * @return the created instance
     */
    public static Todo fromRest(final JsonObject jsonObject,
                                final String userId,
                                final String id) {

        Todo result = new Todo();
        result.id = id;
        result.userId = userId;
        result.title = jsonObject.getString("title", "");
        result.completed = jsonObject.getBoolean("completed");
        JsonNumber created = jsonObject.getJsonNumber("created");
        if (null != created) {
            result.created = created.longValue();
        }
        return result;
    }

    /**
     * Create a new {@code Todo} instance from a database entry.
     * @param row the database entry
     * @return the created instance
     */
    public static Todo fromDb(final Row row) {

        Todo result = new Todo();
        result.id = row.getString("id");
        result.userId = row.getString("user");
        result.title = row.getString("message");
        result.completed = row.getBool("completed");
        result.created = row.getTimestamp("created").getTime();
        return result;
    }

    /**
     * Create a new {@code Todo} instance.
     * The created entry will be new, i.e the {@code completed} flag will be set
     * to {@code false} and the {@code created} timestamp set to the current
     * time.
     * @param userId the user ID associated with the new entry
     * @param title the title for the new entry
     * @return the created instance
     */
    public static Todo create(final String userId, final String title) {
        Todo result = new Todo();

        result.id = UUID.randomUUID().toString();
        result.userId = userId;
        result.title = title;
        result.completed = false;
        result.created = System.currentTimeMillis();

        return result;
    }

    /**
     * Convert this {@code Todo} instance to the JSON database format.
     * @return {@code JsonObject}
     */
    public JsonObject forDb() {
        //to store to DB
        JsonObjectBuilder builder = JSON.createObjectBuilder();
        return builder.add("id", id)
                .add("user", userId)
                .add("message", title)
                .add("completed", completed)
                .add("created", created)
                .build();
    }

    /**
     * Convert this {@code Todo} instance to the JSON REST format.
     * @return {@code JsonObject}
     */
    public JsonObject forRest() {
        //to send over to rest
        JsonObjectBuilder builder = JSON.createObjectBuilder();
        return builder.add("id", id)
                .add("user", userId)
                .add("title", title)
                .add("completed", completed)
                .add("created", created)
                .build();
    }

    /**
     * Get the TODO ID.
     * @return the {@code String} identifying this entry
     */
    public String getId() {
        return id;
    }

    /**
     * Get the user ID associated with this TODO.
     * @return the {@code String} identifying the user
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Get the TODO title.
     * @return title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Get the completed flag.
     * @return completed flag.
     */
    public Boolean getCompleted() {
        return completed;
    }

    /**
     * Set the completed flag.
     * @param iscomplete the completed flag value
     */
    public void setCompleted(final boolean iscomplete) {
        this.completed = iscomplete;
    }

    /**
     * Get the creation timestamp.
     * @return timestamp
     */
    public long getCreated() {
        return created;
    }

    @Override
    public String toString() {
        return "Todo{"
                + "id='" + id + '\''
                + ", userId='" + userId + '\''
                + ", title='" + title + '\''
                + ", completed=" + completed
                + ", created=" + created
                + '}';
    }
}
