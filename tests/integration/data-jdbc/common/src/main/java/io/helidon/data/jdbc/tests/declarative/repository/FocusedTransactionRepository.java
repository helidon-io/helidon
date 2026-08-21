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

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.tests.application.transaction.TransactionSql;
import io.helidon.transaction.Tx;

/**
 * Generated repository methods for focused transaction behavior tests.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface FocusedTransactionRepository {

    /**
     * Inserts a row in the current transaction, or starts one when none is active.
     *
     * @param value row value
     * @return update count
     */
    @Tx.Required
    @Jdbc.Statement(TransactionSql.INSERT_NAMED_VALUE)
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long insertRequired(String value);

    /**
     * Inserts a row in an independent transaction.
     *
     * @param value row value
     * @return update count
     */
    @Tx.New
    @Jdbc.Statement(TransactionSql.INSERT_NAMED_VALUE)
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long insertNew(String value);

    /**
     * Inserts a row outside a suspended caller transaction.
     *
     * @param value row value
     * @return update count
     */
    @Tx.Unsupported
    @Jdbc.Statement(TransactionSql.INSERT_NAMED_VALUE)
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long insertUnsupported(String value);

    /**
     * Runs an invalid query in a required transaction to force a driver failure.
     *
     * @return no rows because the statement must fail
     */
    @Tx.Required
    @Jdbc.Statement(TransactionSql.INVALID_QUERY)
    @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
    List<String> failRequired();
}
