/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.logging.Logger;

import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbResult;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRowResult;
import io.helidon.dbclient.DbStatement;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.DbStatements;
import io.helidon.dbclient.common.AbstractDbExecute;
import io.helidon.dbclient.common.InterceptorSupport;

/**
 * Helidon DB implementation for JDBC drivers.
 */
class JdbcDbClient implements DbClient {

    /**
     * Local logger instance.
     */
    private static final Logger LOGGER = Logger.getLogger(JdbcDbClient.class.getName());

    private final ExecutorService executorService;
    private final ConnectionPool connectionPool;
    private final DbStatements statements;
    private final DbMapperManager dbMapperMananger;
    private final MapperManager mapperManager;
    private final InterceptorSupport interceptors;

    JdbcDbClient(JdbcDbClientProviderBuilder builder) {
        this.executorService = builder.executorService();
        this.connectionPool = builder.connectionPool();
        this.statements = builder.statements();
        this.dbMapperMananger = builder.dbMapperMananger();
        this.mapperManager = builder.mapperManager();
        this.interceptors = builder.interceptors();
    }

    @Override
    public <T> T inTransaction(Function<DbExecute, T> executor) {
        return executor.apply(new JdbcExecute(executorService, statements, interceptors));
    }

    @Override
    public <T> T execute(Function<DbExecute, T> executor) {
        return executor.apply(new JdbcExecute(executorService, statements, interceptors));
    }

    @Override
    public CompletionStage<Void> ping() {
        return execute(exec -> exec.namedUpdate("ping"))
                // need to get from Long to Void
                .thenRun(() -> {
                });
    }

    @Override
    public String dbType() {
        return connectionPool.dbType();
    }

    private class JdbcExecute extends AbstractDbExecute implements DbExecute {
        private final ExecutorService executorService;
        private final InterceptorSupport interceptors;

        JdbcExecute(ExecutorService executorService,
                    DbStatements statements,
                    InterceptorSupport interceptors) {
            super(statements);
            this.executorService = executorService;
            this.interceptors = interceptors;
        }

        @Override
        public DbStatement<?, DbRowResult<DbRow>> createNamedQuery(String statementName, String statement) {
            return new JdbcStatementQuery(DbStatementType.QUERY,
                                          connectionPool,
                                          executorService,
                                          statementName,
                                          statement,
                                          dbMapperMananger,
                                          mapperManager,
                                          interceptors);
        }

        @Override
        public DbStatement<?, CompletionStage<Optional<DbRow>>> createNamedGet(String statementName, String statement) {
            return new JdbcStatementGet(connectionPool,
                                        executorService,
                                        statementName,
                                        statement,
                                        dbMapperMananger,
                                        mapperManager,
                                        interceptors);
        }

        @Override
        public DbStatement<?, CompletionStage<Long>> createNamedDmlStatement(String statementName, String statement) {
            return new JdbcStatementDml(DbStatementType.DML,
                                        connectionPool,
                                        executorService,
                                        statementName,
                                        statement,
                                        dbMapperMananger,
                                        mapperManager,
                                        interceptors);
        }

        @Override
        public DbStatement<?, CompletionStage<Long>> createNamedInsert(String statementName, String statement) {
            return new JdbcStatementDml(DbStatementType.INSERT,
                                        connectionPool,
                                        executorService,
                                        statementName,
                                        statement,
                                        dbMapperMananger,
                                        mapperManager,
                                        interceptors);
        }

        @Override
        public DbStatement<?, CompletionStage<Long>> createNamedUpdate(String statementName, String statement) {
            return new JdbcStatementDml(DbStatementType.UPDATE,
                                        connectionPool,
                                        executorService,
                                        statementName,
                                        statement,
                                        dbMapperMananger,
                                        mapperManager,
                                        interceptors);
        }

        @Override
        public DbStatement<?, CompletionStage<Long>> createNamedDelete(String statementName, String statement) {
            return new JdbcStatementDml(DbStatementType.DELETE,
                                        connectionPool,
                                        executorService,
                                        statementName,
                                        statement,
                                        dbMapperMananger,
                                        mapperManager,
                                        interceptors);
        }

        @Override
        public DbStatement<?, DbResult> createNamedStatement(String statementName, String statement) {
            return new JdbcStatementGeneric(connectionPool,
                                            executorService,
                                            statementName,
                                            statement,
                                            dbMapperMananger,
                                            mapperManager,
                                            interceptors);
        }
    }
}
