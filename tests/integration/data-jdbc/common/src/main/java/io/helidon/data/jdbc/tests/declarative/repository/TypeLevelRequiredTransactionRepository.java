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
import io.helidon.data.jdbc.tests.application.transaction.TransactionSql;
import io.helidon.transaction.Tx;

/**
 * Repository with a type-level transaction annotation retained by generated methods.
 */
@Data.Repository
@Data.Provider("jdbc")
@Tx.Required
public interface TypeLevelRequiredTransactionRepository {

    /**
     * Counts transaction-matrix rows through a type-level required transaction.
     *
     * @return row count
     */
    @Jdbc.Statement(TransactionSql.QUERY)
    @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
    long query();

    /**
     * Deletes the baseline row through a type-level required transaction.
     *
     * @return update count
     */
    @Jdbc.Statement(TransactionSql.UPDATE)
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long update();

    /**
     * Inserts a generated-key row through a type-level required transaction.
     *
     * @return generated key
     */
    @Jdbc.Statement(TransactionSql.GENERATED_KEY)
    @Jdbc.GeneratedKeys("id")
    long generatedKey();
}
