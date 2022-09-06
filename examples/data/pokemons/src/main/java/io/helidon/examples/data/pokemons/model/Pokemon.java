/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.examples.data.pokemons.model;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * Pokemon entity.
 */
@Entity
public class Pokemon {
    @Id
    private int id;
    private String name;
    @ManyToOne()
    @JoinColumn(name = "idType")
    private Type type;

    /**
     * Create empty Pokemon with all values set to {@code null}.

     */
    public Pokemon() {
        this.id = -1;
        this.name = null;
        this.type = null;
    }

    /**
     * Create pokemon with name and type.
     *
     * @param id id of the beast
     * @param name name of the beast
     * @param type id of beast type
     */
    public Pokemon(int id, String name, Type type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type idType) {
        this.type = type;
    }

    public JsonObject toJson() {
        return Json.createObjectBuilder()
                .add("id", id)
                .add("name", name)
                .add("type", type != null
                        ? type.toJson()
                        : null
                ).build();
    }

}
