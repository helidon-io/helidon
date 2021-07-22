/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.examples.micrometer.se;

import java.util.Collections;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/**
 * POJO for the greeting message exchanged between the server and the client.
 */
public class GreetingMessage {

    /**
     * Label for tagging a {@code GreetingMessage} instance in JSON.
     */
    public static final String JSON_LABEL = "greeting";

    private static final JsonBuilderFactory JSON_BF = Json.createBuilderFactory(Collections.emptyMap());

    private String message;

    /**
     * Create a new greeting with the specified message content.
     *
     * @param message the message to store in the greeting
     */
    public GreetingMessage(String message) {
        this.message = message;
    }

    /**
     * Returns the message value.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the message value.
     *
     * @param message value to be set
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Converts a JSON object (typically read from the request payload)
     * into a {@code GreetingMessage}.
     *
     * @param jsonObject the {@link JsonObject} to convert.
     * @return {@code GreetingMessage} set according to the provided object
     */
    public static GreetingMessage fromRest(JsonObject jsonObject) {
        return new GreetingMessage(jsonObject.getString(JSON_LABEL));
    }

    /**
     * Prepares a {@link JsonObject} corresponding to this instance.
     *
     * @return {@code JsonObject} representing this {@code GreetingMessage} instance
     */
    public JsonObject forRest() {
        JsonObjectBuilder builder = JSON_BF.createObjectBuilder();
        return builder.add(JSON_LABEL, message)
                .build();
    }
}
