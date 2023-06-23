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

import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbStatements;
import io.helidon.dbclient.common.CommonClientContext;

/**
 * JDBC statement context.
 */
class StatementContext {

    private final String statementName;
    private final String statement;
    private final CommonClientContext clientContext;
    private final JdbcConnectionPool connectionPool;

    private StatementContext(String statementName,
                             String statement,
                             CommonClientContext context,
                             JdbcConnectionPool connectionPool) {
        this.statementName = statementName;
        this.statement = statement;
        this.clientContext = context;
        this.connectionPool = connectionPool;
    }

    String statementName() {
        return statementName;
    }

    String statement() {
        return statement;
    }

    CommonClientContext clientContext() {
        return clientContext;
    }

    JdbcConnectionPool connectionPool() {
        return connectionPool;
    }

    DbStatements statements() {
        return clientContext.statements();
    }

    DbMapperManager dbMapperManager() {
        return clientContext.dbMapperManager();
    }

    MapperManager mapperManager() {
        return clientContext.mapperManager();
    }

    String dbType() {
        return clientContext.dbType();
    }

    static StatementContext create(String statementName,
                                   String statement,
                                   CommonClientContext clientContext,
                                   JdbcConnectionPool connectionPool) {
        return new StatementContext(statementName, statement, clientContext, connectionPool);
    }

}
