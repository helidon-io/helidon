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

import java.sql.Connection;

import io.helidon.dbclient.DbStatementDml;
import io.helidon.dbclient.DbStatementGet;
import io.helidon.dbclient.DbStatementQuery;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.common.CommonClientContext;
import io.helidon.dbclient.common.CommonExecute;

class JdbcExecute extends CommonExecute {

    private final JdbcConnectionPool connectionPool;

    JdbcExecute(CommonClientContext context, JdbcConnectionPool connectionPool) {
        super(context);
        this.connectionPool = connectionPool;
    }

    @Override
    public DbStatementQuery createNamedQuery(String statementName, String statement) {
        return JdbcStatementQuery.create(
                StatementContext.create(statementName, statement, DbStatementType.QUERY, context(), connectionPool));
    }

    @Override
    public DbStatementGet createNamedGet(String statementName, String statement) {
        return JdbcStatementGet.create(
                StatementContext.create(statementName, statement, DbStatementType.GET, context(), connectionPool));
    }

    @Override
    public DbStatementDml createNamedDmlStatement(String statementName, String statement) {
        return JdbcStatementDml.create(
                StatementContext.create(statementName, statement, DbStatementType.DML, context(), connectionPool));
    }

    @Override
    public DbStatementDml createNamedInsert(String statementName, String statement) {
        return JdbcStatementDml.create(
                StatementContext.create(statementName, statement, DbStatementType.INSERT, context(), connectionPool));
    }

    @Override
    public DbStatementDml createNamedUpdate(String statementName, String statement) {
        return JdbcStatementDml.create(
                StatementContext.create(statementName, statement, DbStatementType.UPDATE, context(), connectionPool));
    }

    @Override
    public DbStatementDml createNamedDelete(String statementName, String statement) {
        return JdbcStatementDml.create(
                StatementContext.create(statementName, statement, DbStatementType.DELETE, context(), connectionPool));
    }

    @Override
    public <C> C unwrap(Class<C> cls) {
        if (Connection.class.isAssignableFrom(cls)) {
            return cls.cast(connectionPool.connection());
        } else {
            throw new UnsupportedOperationException(String.format("Class %s is not supported for unwrap", cls.getName()));
        }
    }

    JdbcConnectionPool connectionPool() {
        return connectionPool;
    }

    static JdbcExecute create(CommonClientContext context, JdbcConnectionPool connectionPool) {
        return new JdbcExecute(context, connectionPool);
    }

}
