/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.examples.dbclient.pokemons.jdbc.mapper;

/**
 * POJO representing Pokemon.
 */
public class Pokemon {

    private int id;
    private String name;
    private int idType;

    /**
     * Default constructor.
     */
    public Pokemon() {
        // JSON-B
    }

    /**
     * Create pokemon with name and type.
     *
     * @param name name of the beast
     * @param type type of the beast
     * @param idType id of beast type
     */
    public Pokemon(int id, String name, int idType) {
        this.name = name;
        this.idType = idType;
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

    public int getIdType() {
        return idType;
    }

    public void setIdType(int idType) {
        this.idType = idType;
    }
}
