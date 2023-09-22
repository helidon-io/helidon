/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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
package io.helidon.dbclient.tests.common.model;

import java.util.HashMap;
import java.util.Map;

/**
 * {@code Critter} type POJO.
 */
public record Kind(int id, String name) {

    /**
     * Map of {@code Critter} types by ID.
     */
    public static final Map<Integer, Kind> KINDS = new HashMap<>();

    static {
        KINDS.put(1, new Kind(1, "Normal"));
        KINDS.put(2, new Kind(2, "Fighting"));
        KINDS.put(3, new Kind(3, "Flying"));
        KINDS.put(4, new Kind(4, "Poison"));
        KINDS.put(5, new Kind(5, "Ground"));
        KINDS.put(6, new Kind(6, "Rock"));
        KINDS.put(7, new Kind(7, "Bug"));
        KINDS.put(8, new Kind(8, "Ghost"));
        KINDS.put(9, new Kind(9, "Steel"));
        KINDS.put(10, new Kind(10, "Fire"));
        KINDS.put(11, new Kind(11, "Water"));
        KINDS.put(12, new Kind(12, "Grass"));
        KINDS.put(13, new Kind(13, "Electric"));
        KINDS.put(14, new Kind(14, "Psychic"));
        KINDS.put(15, new Kind(15, "Ice"));
        KINDS.put(16, new Kind(16, "Dragon"));
        KINDS.put(17, new Kind(17, "Dark"));
        KINDS.put(18, new Kind(18, "Fairy"));
    }

    @Override
    public String toString() {
        return "Type: {id=" + id + ", name=" + name + "}";
    }

}
