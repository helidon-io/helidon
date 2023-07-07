/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.jdbc;

import java.util.stream.Stream;

import io.helidon.dbclient.DbExecuteContext;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementQuery;
import io.helidon.dbclient.DbStatementType;

/**
 * JDBC implementation of {@link DbStatementQuery} with transaction support.
 */
class JdbcTransactionStatementQuery extends JdbcTransactionStatement<DbStatementQuery> implements DbStatementQuery {

    /**
     * Create a new instance.
     *
     * @param connectionPool     connection pool
     * @param context            context
     * @param transactionContext transaction context
     */
    JdbcTransactionStatementQuery(JdbcConnectionPool connectionPool,
                                  DbExecuteContext context,
                                  TransactionContext transactionContext) {

        super(connectionPool, context, transactionContext);
    }

    @Override
    public DbStatementType statementType() {
        return DbStatementType.QUERY;
    }

    @Override
    public Stream<DbRow> execute() {
        return doExecute((future, context) -> JdbcStatementQuery.doExecute(this, future, context));
    }
}
