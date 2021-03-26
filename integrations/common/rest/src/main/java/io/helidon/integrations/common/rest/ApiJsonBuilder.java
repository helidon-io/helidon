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
 */

package io.helidon.integrations.common.rest;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/**
 * Common base class for builders that construct a JSON object.
 *
 * @param <T> type of the subclass
 */
public abstract class ApiJsonBuilder<T extends ApiJsonBuilder<T>> {
    private final Map<String, Consumer<JsonObjectBuilder>> values = new HashMap<>();
    private final Map<String, ApiJsonBuilder<?>> objects = new HashMap<>();
    private final Map<String, List<Consumer<JsonArrayBuilder>>> arrays = new HashMap<>();
    private final Map<String, List<ApiJsonBuilder<?>>> objectArrays = new HashMap<>();
    private final Map<String, Map<String, Consumer<JsonObjectBuilder>>> objectsAsMaps = new HashMap<>();

    /**
     * Default constructor.
     */
    protected ApiJsonBuilder() {
        super();
    }

    /**
     * Create a JSON object from this builder.
     *
     * @param factory builder factory to create objects
     * @return JSON object or empty
     */
    public Optional<JsonObject> toJson(JsonBuilderFactory factory) {
        JsonObjectBuilder payload = factory.createObjectBuilder();
        preBuild(factory, payload);
        values.forEach((key, value) -> value.accept(payload));
        objects.forEach((key, value) -> value.toJson(factory).ifPresent(it -> payload.add(key, it)));
        arrays.forEach((key, value) -> addArray(payload, factory, key, value));
        objectArrays.forEach((key, value) -> addObjectArray(payload, factory, key, value));
        objectsAsMaps.forEach((key, value) -> {
            JsonObjectBuilder childObject = factory.createObjectBuilder();
            value.forEach((childKey, childValue) -> childValue.accept(childObject));
            payload.add(key, childObject);
        });
        postBuild(factory, payload);
        return Optional.of(payload.build());
    }

    /**
     * Can be returned by subclasses that can be subclassed again.
     *
     * @return this instance as a subclass type
     */
    @SuppressWarnings("unchecked")
    protected T me() {
        return (T) this;
    }

    /**
     * Called before adding properties defined in this request.
     *
     * @param factory json factory
     * @param payload payload builder
     */
    protected void preBuild(JsonBuilderFactory factory, JsonObjectBuilder payload) {
    }

    /**
     * Called after adding properties defined in this request.
     *
     * @param factory json factory
     * @param payload payload builder
     */
    protected void postBuild(JsonBuilderFactory factory, JsonObjectBuilder payload) {
    }

    /**
     * Add an element to an array.
     *
     * @param name key in the json payload
     * @param element element of the array
     * @return updated request
     */
    protected T addToArray(String name, String element) {
        arrays.computeIfAbsent(name, it -> new LinkedList<>())
                .add(it -> it.add(element));

        return me();
    }

    /**
     * Add an element to an array.
     *
     * @param name key in the json payload
     * @param element element of the array
     * @return updated request
     */
    protected T addToArray(String name, int element) {
        arrays.computeIfAbsent(name, it -> new LinkedList<>())
                .add(it -> it.add(element));

        return me();
    }

    /**
     * Add an element to an array.
     *
     * @param name key in the json payload
     * @param element element of the array
     * @return updated request
     */
    protected T addToArray(String name, long element) {
        arrays.computeIfAbsent(name, it -> new LinkedList<>())
                .add(it -> it.add(element));

        return me();
    }

    /**
     * Add an element to an array.
     *
     * @param name key in the json payload
     * @param element element of the array
     * @return updated request
     */
    protected T addToArray(String name, double element) {
        arrays.computeIfAbsent(name, it -> new LinkedList<>())
                .add(it -> it.add(element));

        return me();
    }

    /**
     * Add an element to an array.
     *
     * @param name key in the json payload
     * @param element element of the array
     * @return updated request
     */
    protected T addToArray(String name, boolean element) {
        arrays.computeIfAbsent(name, it -> new LinkedList<>())
                .add(it -> it.add(element));

        return me();
    }

    /**
     * Add custom string to payload.
     * If such a name is already added, it will be replaced.
     *
     * @param name json key
     * @param value json String value
     * @return updated request
     */
    protected T add(String name, String value) {
        return add(name, builder -> builder.add(name, value));
    }

    /**
     * Add custom int to payload.
     * If such a name is already added, it will be replaced.
     *
     * @param name json key
     * @param value json value
     * @return updated request
     */
    protected T add(String name, int value) {
        return add(name, builder -> builder.add(name, value));
    }

    /**
     * Add custom double to payload.
     * If such a name is already added, it will be replaced.
     *
     * @param name json key
     * @param value json value
     * @return updated request
     */
    protected T add(String name, double value) {
        return add(name, builder -> builder.add(name, value));
    }

    /**
     * Add custom long to payload.
     * If such a name is already added, it will be replaced.
     *
     * @param name json key
     * @param value json value
     * @return updated request
     */
    protected T add(String name, long value) {
        return add(name, builder -> builder.add(name, value));
    }

    /**
     * Add custom boolean to payload.
     * If such a name is already added, it will be replaced.
     *
     * @param name json key
     * @param value json value
     * @return updated request
     */
    protected T add(String name, boolean value) {
        return add(name, builder -> builder.add(name, value));
    }

    /**
     * Add a custom object to payload.
     *
     * @param name json key
     * @param object json value
     * @return updated request
     */
    protected T add(String name, ApiJsonBuilder<?> object) {
        objects.put(name, object);
        return me();
    }

    /**
     * Add a string encoded with base64.
     *
     * @param name json key
     * @param base64Value base64 data
     * @return updated request
     */
    protected T addBase64(String name, Base64Value base64Value) {
        return add(name, base64Value.toBase64());
    }

    /**
     * Configure an empty array.
     *
     * @param name name of the property
     * @return updated builder
     */
    protected T emptyArray(String name) {
        arrays.put(name, new LinkedList<>());
        return me();
    }

    /**
     * Add an object to an array.
     *
     * @param name name of the nested property
     * @param element a {@link ApiJsonBuilder} of the element of the array
     * @return updated builder
     */
    protected T addToArray(String name, ApiJsonBuilder<?> element) {
        objectArrays.computeIfAbsent(name, it -> new LinkedList<>())
                .add(element);

        return me();
    }

    /**
     * Add a key/value pair to a named object.
     *
     * @param name name of the object to create under the root
     * @param key key of the nested property
     * @param value value of the nested property
     * @return updated builder
     */
    protected T addToObject(String name, String key, String value) {
        objectsAsMaps.computeIfAbsent(name, it -> new HashMap<>())
                .put(key, builder -> builder.add(key, value));

        return me();
    }

    /**
     * Add a key/value pair to a named object.
     *
     * @param name name of the object to create under the root
     * @param key key of the nested property
     * @param value value of the nested property
     * @return updated builder
     */
    protected T addToObject(String name, String key, int value) {
        objectsAsMaps.computeIfAbsent(name, it -> new HashMap<>())
                .put(key, builder -> builder.add(key, value));

        return me();
    }

    /**
     * Add a key/value pair to a named object.
     *
     * @param name name of the object to create under the root
     * @param key key of the nested property
     * @param value value of the nested property
     * @return updated builder
     */
    protected T addToObject(String name, String key, long value) {
        objectsAsMaps.computeIfAbsent(name, it -> new HashMap<>())
                .put(key, builder -> builder.add(key, value));

        return me();
    }

    /**
     * Add a key/value pair to a named object.
     *
     * @param name name of the object to create under the root
     * @param key key of the nested property
     * @param value value of the nested property
     * @return updated builder
     */
    protected T addToObject(String name, String key, double value) {
        objectsAsMaps.computeIfAbsent(name, it -> new HashMap<>())
                .put(key, builder -> builder.add(key, value));

        return me();
    }

    /**
     * Add a key/value pair to a named object.
     *
     * @param name name of the object to create under the root
     * @param key key of the nested property
     * @param value value of the nested property
     * @return updated builder
     */
    protected T addToObject(String name, String key, boolean value) {
        objectsAsMaps.computeIfAbsent(name, it -> new HashMap<>())
                .put(key, builder -> builder.add(key, value));

        return me();
    }

    private void addArray(JsonObjectBuilder payloadBuilder,
                          JsonBuilderFactory json,
                          String name,
                          List<Consumer<JsonArrayBuilder>> values) {

        JsonArrayBuilder arrayBuilder = json.createArrayBuilder();
        for (Consumer<JsonArrayBuilder> value : values) {
            value.accept(arrayBuilder);
        }

        payloadBuilder.add(name, arrayBuilder);
    }

    private void addObjectArray(JsonObjectBuilder payloadBuilder,
                                JsonBuilderFactory json,
                                String name,
                                List<ApiJsonBuilder<?>> values) {

        if (values == null) {
            return;
        }

        JsonArrayBuilder arrayBuilder = json.createArrayBuilder();
        for (ApiJsonBuilder<?> element : values) {
            element.toJson(json).ifPresent(arrayBuilder::add);
        }

        payloadBuilder.add(name, arrayBuilder);
    }

    private T add(String name, Consumer<JsonObjectBuilder> consumer) {
        values.put(name, consumer);

        return me();
    }
}
