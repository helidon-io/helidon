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
package io.helidon.data.jdbc.tests.declarative.repository;

import java.util.List;
import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.tests.application.ContactView;
import io.helidon.data.jdbc.tests.application.TestSql;

/**
 * Generated repository used by SQL-injection safety tests.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface SqlInjectionRepository {
    /**
     * Inserts one contact through generated named-parameter binding.
     *
     * @param name contact name
     * @param email contact email
     * @return generated identifier
     */
    @Jdbc.Statement("INSERT INTO CONTACT(NAME, EMAIL) VALUES (:name, :email)")
    @Jdbc.GeneratedKeys("id")
    long insert(String name, String email);

    /**
     * Finds one contact by exact name through generated named-parameter binding.
     *
     * @param name contact name
     * @return matching contact
     */
    @Jdbc.Statement("SELECT ID, NAME, EMAIL FROM CONTACT WHERE NAME = :name")
    Optional<ContactView> findByName(String name);

    /**
     * Finds contacts by exact name through generated named-parameter binding.
     *
     * @param name contact name
     * @return matching contacts
     */
    @Jdbc.Statement("SELECT ID, NAME, EMAIL FROM CONTACT WHERE NAME = :name ORDER BY ID")
    List<ContactView> findAllByName(String name);

    /**
     * Finds contacts by an exact name or email value through one repeated named parameter.
     *
     * @param value contact name or email value
     * @return matching contacts
     */
    @Jdbc.Statement("""
            SELECT ID, NAME, EMAIL
            FROM CONTACT
            WHERE NAME = :value OR EMAIL = :value
            ORDER BY ID
            """)
    List<ContactView> findAllByNameOrEmail(String value);

    /**
     * Renames contacts matched by exact source name.
     *
     * @param replacementName replacement name
     * @param sourceName source name
     * @return update count
     */
    @Jdbc.Statement(TestSql.RENAME_BY_NAME)
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long renameByName(String replacementName, String sourceName);

    /**
     * Deletes contacts matched by exact name.
     *
     * @param name contact name
     * @return delete count
     */
    @Jdbc.Statement(TestSql.DELETE_BY_NAME)
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long deleteByName(String name);
}
