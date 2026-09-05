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

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;

/**
 * Repository that receives the JDBC client named {@code inventory}.
 */
@Data.Repository
@Data.Provider("jdbc")
@Jdbc.Client("inventory")
public interface InventoryClientRepository {

    /**
     * Inserts one value through the injected client.
     *
     * @param id value identifier
     * @param name value name
     * @return affected row count
     */
    @Jdbc.Statement("INSERT INTO CLIENT_VALUE (ID, NAME) VALUES (:id, :name)")
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long insert(int id, String name);

    /**
     * Reads one value through the injected client.
     *
     * @param id value identifier
     * @return value name
     */
    @Jdbc.Statement("SELECT NAME FROM CLIENT_VALUE WHERE ID = :id")
    String find(int id);
}
