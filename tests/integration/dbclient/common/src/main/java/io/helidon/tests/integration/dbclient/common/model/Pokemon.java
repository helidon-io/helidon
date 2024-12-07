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
package io.helidon.tests.integration.dbclient.common.model;

import java.util.List;

/**
 * {@code Pokemon}.
 *
 * @param id    id
 * @param name  name
 * @param types types
 */
public record Pokemon(int id, String name, boolean healthy, List<Type> types) {

    /**
     * Create a new instance of pokemon.
     *
     * @param id    id
     * @param name  name
     * @param healthy whether pokemon is healthy
     * @param types types
     */
    public Pokemon(int id, String name, boolean healthy, Type... types) {
        this(id, name, healthy, List.of(types));
    }

    /**
     * Create a new instance of healthy pokemon.
     *
     * @param id    id
     * @param name  name
     * @param types types
     */
    public Pokemon(int id, String name, Type... types) {
        this(id, name, true, types);
    }

    /**
     * Create a new instance of healthy pokemon.
     *
     * @param id    id
     * @param name  name
     * @param types types
     */
    public Pokemon(int id, String name, List<Type> types) {
        this(id, name, true, types);
    }

}
