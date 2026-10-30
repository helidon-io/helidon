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
package io.helidon.tests.integration.data.jdbc.declarative.repository;

import java.util.Iterator;
import java.util.List;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.JdbcClient;

/**
 * Declares portable batches using existing Java parameter names and positional bindings.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface BatchRepository {
    @Jdbc.Statement("INSERT INTO CONTACT (NAME, EMAIL) VALUES (:rows, :statement)")
    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
    JdbcClient.BatchResult insert(List<String> rows, String statement);

    @Jdbc.Statement("UPDATE CONTACT SET NAME = :name, EMAIL = :email WHERE ID = :id OR ID = :id")
    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
    JdbcClient.BatchResult records(Iterable<Change> rows);

    @Jdbc.Statement("UPDATE CONTACT SET NAME = ?, EMAIL = ? WHERE ID = ?")
    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
    JdbcClient.BatchResult aligned(Iterator<String> rows, String statement, long... iterator);

    record Change(String name, String email, long id) {
    }
}
