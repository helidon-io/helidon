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
package io.helidon.tests.integration.data.jdbc.declarative;

import java.util.function.Consumer;
import java.util.stream.Stream;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.transaction.Tx;

/**
 * Streams ordinary query results through generated binding and record mapping.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface StreamingRepository {
    /**
     * Streams ordered values with repeated nullable input bindings.
     *
     * @param value The input may represent typed SQL NULL.
     * @param action The consumer owns no JDBC resources.
     */
    @Jdbc.Statement("SELECT DATA_VALUE FROM ST_VALUE WHERE :value IS NULL OR DATA_VALUE = :value ORDER BY ID")
    void values(String value, Consumer<Stream<String>> action);

    /**
     * Streams mapped records with positional binding and a control before the input.
     *
     * @param action The consumer processes detached records.
     * @param minimum The input selects the first identifier.
     */
    @Jdbc.Statement("SELECT ID AS id, DATA_VALUE AS value FROM ST_VALUE WHERE ID >= ? ORDER BY ID")
    void records(Consumer<Stream<Value>> action, int minimum);

    /**
     * Streams records with an injected mapper inside an intercepted transaction.
     *
     * @param action The consumer processes detached records.
     */
    @Tx.Required
    @Jdbc.RowMapper(StreamingValueMapper.class)
    @Jdbc.Statement("SELECT ID AS id, DATA_VALUE AS value FROM ST_VALUE ORDER BY ID")
    void mapped(Consumer<Stream<Value>> action);

    /**
     * Represents one detached query row.
     *
     * @param id The identifier establishes ordering.
     * @param value The value is detached from JDBC.
     */
    record Value(int id, String value) {
    }
}
