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
package io.helidon.tests.integration.dbclient.appl.model;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/**
 * Pokemon type POJO.
 */
public class Type {

    /** Map of pokemon types. */
    public static final Map<Integer, Type> TYPES = new HashMap<>();

    // Initialize pokemon types Map
    static {
        TYPES.put(1, new Type(1, "Normal"));
        TYPES.put(2, new Type(2, "Fighting"));
        TYPES.put(3, new Type(3, "Flying"));
        TYPES.put(4, new Type(4, "Poison"));
        TYPES.put(5, new Type(5, "Ground"));
        TYPES.put(6, new Type(6, "Rock"));
        TYPES.put(7, new Type(7, "Bug"));
        TYPES.put(8, new Type(8, "Ghost"));
        TYPES.put(9, new Type(9, "Steel"));
        TYPES.put(10, new Type(10, "Fire"));
        TYPES.put(11, new Type(11, "Water"));
        TYPES.put(12, new Type(12, "Grass"));
        TYPES.put(13, new Type(13, "Electric"));
        TYPES.put(14, new Type(14, "Psychic"));
        TYPES.put(15, new Type(15, "Ice"));
        TYPES.put(16, new Type(16, "Dragon"));
        TYPES.put(17, new Type(17, "Dark"));
        TYPES.put(18, new Type(18, "Fairy"));
    }

    private final int id;
    private final String name;

    public Type(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Type: {id=" + id + ", name=" + name + "}";
    }

    public JsonObject toJsonObject() {
        final JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("id", id);
        job.add("name", name);
        return job.build();
    }

}
