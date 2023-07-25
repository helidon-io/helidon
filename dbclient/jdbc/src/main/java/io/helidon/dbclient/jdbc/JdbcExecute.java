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

import io.helidon.dbclient.DbClientContext;
import io.helidon.dbclient.DbExecuteBase;
import io.helidon.dbclient.DbStatementDml;
import io.helidon.dbclient.DbStatementGet;
import io.helidon.dbclient.DbStatementQuery;

import static io.helidon.dbclient.DbStatementType.DELETE;
import static io.helidon.dbclient.DbStatementType.DML;
import static io.helidon.dbclient.DbStatementType.INSERT;
import static io.helidon.dbclient.DbStatementType.UPDATE;

/**
 * JDBC implementation of {@link io.helidon.dbclient.DbExecute}.
 */
class JdbcExecute extends DbExecuteBase {

    private final JdbcConnectionPool connectionPool;

    /**
     * Create a new instance.
     *
     * @param context        context
     * @param connectionPool connection pool
     */
    JdbcExecute(DbClientContext context, JdbcConnectionPool connectionPool) {
        super(context);
        this.connectionPool = connectionPool;
    }

    /**
     * Get the connection pool.
     *
     * @return connection pool
     */
    JdbcConnectionPool connectionPool() {
        return connectionPool;
    }

    JdbcClientContext jdbcContext() {
        return context(JdbcClientContext.class);
    }

    @Override
    public DbStatementQuery createNamedQuery(String stmtName, String stmt) {
        return new JdbcStatementQuery(connectionPool, JdbcExecuteContext.jdbcCreate(stmtName, stmt, jdbcContext()));
    }

    @Override
    public DbStatementGet createNamedGet(String stmtName, String stmt) {
        return new JdbcStatementGet(connectionPool, JdbcExecuteContext.jdbcCreate(stmtName, stmt, jdbcContext()));
    }

    @Override
    public DbStatementDml createNamedDmlStatement(String stmtName, String stmt) {
        return new JdbcStatementDml(connectionPool, DML, JdbcExecuteContext.jdbcCreate(stmtName, stmt, jdbcContext()));
    }

    @Override
    public DbStatementDml createNamedInsert(String stmtName, String stmt) {
        return new JdbcStatementDml(connectionPool, INSERT, JdbcExecuteContext.jdbcCreate(stmtName, stmt, jdbcContext()));
    }

    @Override
    public DbStatementDml createNamedUpdate(String stmtName, String stmt) {
        return new JdbcStatementDml(connectionPool, UPDATE, JdbcExecuteContext.jdbcCreate(stmtName, stmt, jdbcContext()));
    }

    @Override
    public DbStatementDml createNamedDelete(String stmtName, String stmt) {
        return new JdbcStatementDml(connectionPool, DELETE, JdbcExecuteContext.jdbcCreate(stmtName, stmt, jdbcContext()));
    }

    @Override
    public <C> C unwrap(Class<C> cls) {
        if (Connection.class.isAssignableFrom(cls)) {
            return cls.cast(connectionPool.connection());
        } else {
            throw new UnsupportedOperationException(String.format("Class %s is not supported for unwrap", cls.getName()));
        }
    }

}
