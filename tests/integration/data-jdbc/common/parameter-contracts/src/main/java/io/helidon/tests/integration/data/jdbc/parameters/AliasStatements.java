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
package io.helidon.tests.integration.data.jdbc.parameters;

import java.util.List;
import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;

/**
 * Supplies retained aliases from a library compiled without Java parameter names.
 *
 * @param <T> The concrete repository supplies the scalar input type.
 */
public interface AliasStatements<T> {
    /**
     * Matches nullable email values at repeated physical positions.
     *
     * @param original The email value supplies both markers.
     * @return The matching contact names are ordered by identifier.
     */
    @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE ((:email IS NULL AND EMAIL IS NULL) OR EMAIL = :email) ORDER BY ID")
    List<String> aliases(@Data.Param("email") T original);

    /**
     * Inserts a contact and returns its identifier.
     *
     * @param original The contact name is bound as a value.
     * @param contactEmail The nullable email is bound with its declared SQL type.
     * @return The generated identifier is present after a successful insert.
     */
    @Jdbc.Statement("INSERT INTO CONTACT(NAME, EMAIL) VALUES (:rows, :email)")
    @Jdbc.GeneratedKeys("id")
    Optional<Long> aliasInsert(@Data.Param("rows") T original, @Data.Param("email") T contactEmail);

    /**
     * Reorders Java arguments relative to physical SQL markers.
     *
     * @param original The identifier selects the contact.
     * @param text The replacement name is bound before the identifier.
     * @return The number of updated contacts is returned.
     */
    @Jdbc.Statement("UPDATE CONTACT SET NAME = :name WHERE ID = :id")
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    int aliasUpdate(@Data.Param("id") long original, @Data.Param("name") T text);
}
