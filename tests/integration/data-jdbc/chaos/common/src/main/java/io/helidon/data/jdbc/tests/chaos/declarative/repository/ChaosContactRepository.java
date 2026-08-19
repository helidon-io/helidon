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
package io.helidon.data.jdbc.tests.chaos.declarative.repository;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.tests.chaos.application.ChaosSql;

/**
 * Generated repository used by declarative JDBC chaos smoke tests.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface ChaosContactRepository {
    /**
     * Executes malformed SQL and is expected to fail before returning a value.
     *
     * @return unreachable scalar value
     */
    @Jdbc.Statement(ChaosSql.MALFORMED_QUERY)
    @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
    long executeMalformedSql();

    /**
     * Inserts one contact row.
     *
     * @param id contact identifier
     * @param name contact name
     */
    @Jdbc.Statement(ChaosSql.INSERT_CONTACT)
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    void insertContact(long id, String name);

    /**
     * Reads text as a numeric value and is expected to fail during conversion.
     *
     * @return unreachable scalar value
     */
    @Jdbc.Statement(ChaosSql.CONVERSION_FAILURE_QUERY)
    @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
    long executeConversionFailureQuery();

    /**
     * Inserts one contact row and returns the generated key.
     *
     * @param name contact name
     * @return generated identifier
     */
    @Jdbc.Statement(ChaosSql.INSERT_GENERATED_CONTACT)
    @Jdbc.GeneratedKeys("id")
    long insertGeneratedContact(String name);

    /**
     * Counts committed chaos contacts after a failure.
     *
     * @return committed row count
     */
    @Jdbc.Statement(ChaosSql.COUNT_CONTACTS)
    @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
    long countContacts();
}
