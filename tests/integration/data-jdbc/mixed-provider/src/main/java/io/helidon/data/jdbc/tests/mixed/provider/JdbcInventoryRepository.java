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
package io.helidon.data.jdbc.tests.mixed.provider;

import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;

/**
 * Repository explicitly assigned to the JDBC provider.
 */
@Data.Repository
@Data.Provider("jdbc")
@Jdbc.Client("mixed")
@SuppressWarnings("helidon:api:preview")
public interface JdbcInventoryRepository {

    /**
     * Finds an inventory row by identifier.
     *
     * @param id identifier
     * @return matching row, if present
     */
    @Jdbc.Statement("SELECT ID AS id, NAME AS name, AVAILABLE AS available FROM JDBC_INVENTORY WHERE ID = :id")
    Optional<JdbcInventory> findById(int id);
}
