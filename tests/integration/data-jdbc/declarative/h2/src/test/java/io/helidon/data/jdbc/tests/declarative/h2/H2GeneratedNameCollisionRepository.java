/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc.tests.declarative.h2;

import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;

/**
 * H2 repository whose application parameters collide with generated names.
 */
@Data.Repository
@Data.Provider("jdbc")
interface H2GeneratedNameCollisionRepository {

    /**
     * Returns a value while its parameter collides with the generated SQL field.
     *
     * @param SQL_FIND selected value
     * @return selected value
     */
    @Jdbc.Statement("SELECT ?")
    String find(String SQL_FIND);

    /**
     * Returns another value while its parameter collides with a second generated SQL field.
     *
     * @param SQL_LOOKUP selected value
     * @return selected value
     */
    @Jdbc.Statement("SELECT ?")
    String lookup(String SQL_LOOKUP);

    /**
     * Returns a value while its parameter collides with the generated client field.
     *
     * @param jdbcClient selected value
     * @return selected value
     */
    @Jdbc.Statement("SELECT ?")
    String client(String jdbcClient);

    /**
     * Returns another value while its parameter collides with the generated client field.
     *
     * @param jdbcClient selected value
     * @return selected value
     */
    @Jdbc.Statement("SELECT ?")
    String secondaryClient(String jdbcClient);

    /**
     * Inserts a contact while its parameters collide with generated lambda variables.
     *
     * @param row contact name
     * @param value contact email
     * @return generated identifier
     */
    @Jdbc.Statement("INSERT INTO CONTACT (NAME, EMAIL) VALUES (?, ?)")
    @Jdbc.GeneratedKeys("id")
    Optional<Long> insert(String row, String value);

    /**
     * Inserts another contact while its parameters collide with generated lambda variables.
     *
     * @param row contact name
     * @param value contact email
     * @return generated identifier
     */
    @Jdbc.Statement("INSERT INTO CONTACT (NAME, EMAIL) VALUES (?, ?)")
    @Jdbc.GeneratedKeys("id")
    Optional<Long> insertSecondary(String row, String value);
}
