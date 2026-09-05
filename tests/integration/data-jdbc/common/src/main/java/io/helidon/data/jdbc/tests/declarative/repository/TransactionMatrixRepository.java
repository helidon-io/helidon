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
 * Generated repository methods for the transaction propagation matrix.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface TransactionMatrixRepository {

    /**
     * Executes an unannotated query.
     *
     * @return row count
     */
    @Jdbc.Statement(TransactionSql.QUERY)
    @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
    long noneQuery();

    /**
     * Executes an unannotated update.
     *
     * @return update count
     */
    @Jdbc.Statement(TransactionSql.UPDATE)
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long noneUpdate();

    /**
     * Executes an unannotated generated-key insert.
     *
     * @return generated key
     */
    @Jdbc.Statement(TransactionSql.GENERATED_KEY)
    @Jdbc.GeneratedKeys("id")
    long noneGeneratedKey();

    /**
     * Executes a mandatory query.
     *
     * @return row count
     */
    @Tx.Mandatory
    @Jdbc.Statement(TransactionSql.QUERY)
    @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
    long mandatoryQuery();

    /**
     * Executes a mandatory update.
     *
     * @return update count
     */
    @Tx.Mandatory
    @Jdbc.Statement(TransactionSql.UPDATE)
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long mandatoryUpdate();

    /**
     * Executes a mandatory generated-key insert.
     *
     * @return generated key
     */
    @Tx.Mandatory
    @Jdbc.Statement(TransactionSql.GENERATED_KEY)
    @Jdbc.GeneratedKeys("id")
    long mandatoryGeneratedKey();

    /**
     * Executes a new-transaction query.
     *
     * @return row count
     */
    @Tx.New
    @Jdbc.Statement(TransactionSql.QUERY)
    @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
    long newQuery();

    /**
     * Executes a new-transaction update.
     *
     * @return update count
     */
    @Tx.New
    @Jdbc.Statement(TransactionSql.UPDATE)
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long newUpdate();

    /**
     * Executes a new-transaction generated-key insert.
     *
     * @return generated key
     */
    @Tx.New
    @Jdbc.Statement(TransactionSql.GENERATED_KEY)
    @Jdbc.GeneratedKeys("id")
    long newGeneratedKey();

    /**
     * Executes a never query.
     *
     * @return row count
     */
    @Tx.Never
    @Jdbc.Statement(TransactionSql.QUERY)
    @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
    long neverQuery();

    /**
     * Executes a never update.
     *
     * @return update count
     */
    @Tx.Never
    @Jdbc.Statement(TransactionSql.UPDATE)
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long neverUpdate();

    /**
     * Executes a never generated-key insert.
     *
     * @return generated key
     */
    @Tx.Never
    @Jdbc.Statement(TransactionSql.GENERATED_KEY)
    @Jdbc.GeneratedKeys("id")
    long neverGeneratedKey();

    /**
     * Executes a required query.
     *
     * @return row count
     */
    @Tx.Required
    @Jdbc.Statement(TransactionSql.QUERY)
    @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
    long requiredQuery();

    /**
     * Executes a required update.
     *
     * @return update count
     */
    @Tx.Required
    @Jdbc.Statement(TransactionSql.UPDATE)
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long requiredUpdate();

    /**
     * Executes a required generated-key insert.
     *
     * @return generated key
     */
    @Tx.Required
    @Jdbc.Statement(TransactionSql.GENERATED_KEY)
    @Jdbc.GeneratedKeys("id")
    long requiredGeneratedKey();

    /**
     * Executes a supported query.
     *
     * @return row count
     */
    @Tx.Supported
    @Jdbc.Statement(TransactionSql.QUERY)
    @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
    long supportedQuery();

    /**
     * Executes a supported update.
     *
     * @return update count
     */
    @Tx.Supported
    @Jdbc.Statement(TransactionSql.UPDATE)
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long supportedUpdate();

    /**
     * Executes a supported generated-key insert.
     *
     * @return generated key
     */
    @Tx.Supported
    @Jdbc.Statement(TransactionSql.GENERATED_KEY)
    @Jdbc.GeneratedKeys("id")
    long supportedGeneratedKey();

    /**
     * Executes an unsupported query.
     *
     * @return row count
     */
    @Tx.Unsupported
    @Jdbc.Statement(TransactionSql.QUERY)
    @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
    long unsupportedQuery();

    /**
     * Executes an unsupported update.
     *
     * @return update count
     */
    @Tx.Unsupported
    @Jdbc.Statement(TransactionSql.UPDATE)
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long unsupportedUpdate();

    /**
     * Executes an unsupported generated-key insert.
     *
     * @return generated key
     */
    @Tx.Unsupported
    @Jdbc.Statement(TransactionSql.GENERATED_KEY)
    @Jdbc.GeneratedKeys("id")
    long unsupportedGeneratedKey();
}
