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
package io.helidon.data.jdbc.tests.application;

import java.util.List;
import java.util.Optional;

/**
 * Generated-key operations implemented by both JDBC programming styles.
 */
public interface GeneratedKeyOperations {
    /**
     * Inserts a contact and returns one named scalar generated key.
     *
     * @param name contact name
     * @return generated identifier
     */
    long insertScalar(String name);

    /**
     * Inserts a contact and returns an optional named scalar generated key.
     *
     * @param name contact name
     * @return generated identifier
     */
    Optional<Long> insertOptionalScalar(String name);

    /**
     * Inserts a contact and returns named scalar generated keys as a list.
     *
     * @param name contact name
     * @return generated identifiers
     */
    List<Long> insertScalarList(String name);

    /**
     * Inserts a contact and maps generated columns into a record projection.
     *
     * @param name contact name
     * @param email contact email
     * @return generated contact projection
     */
    ContactView insertRecord(String name, String email);

    /**
     * Inserts a contact and maps generated columns through an application row mapper.
     *
     * @param name contact name
     * @return mapped generated columns
     */
    ContactLabel insertMapped(String name);

    /**
     * Inserts a contact while requesting an invalid generated key column.
     *
     * @param name contact name
     * @return unreachable generated identifier
     */
    long insertWithInvalidGeneratedKeyColumn(String name);
}
