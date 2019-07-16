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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbStatementDml;
import io.helidon.dbclient.DbStatementGeneric;
import io.helidon.dbclient.DbStatementGet;
import io.helidon.dbclient.DbStatementQuery;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.DbStatements;
import io.helidon.dbclient.DbTransaction;
import io.helidon.dbclient.common.AbstractDbExecute;
import io.helidon.dbclient.common.InterceptorSupport;

/**
 * Helidon DB implementation for JDBC drivers.
 */
class JdbcDbClient implements DbClient {
    private final ExecutorService executorService;
    private final ConnectionPool connectionPool;
    private final DbStatements statements;
    private final DbMapperManager dbMapperManager;
    private final MapperManager mapperManager;
    private final InterceptorSupport interceptors;

    JdbcDbClient(JdbcDbClientProviderBuilder builder) {
        this.executorService = builder.executorService();
        this.connectionPool = builder.connectionPool();
        this.statements = builder.statements();
        this.dbMapperManager = builder.dbMapperManager();
        this.mapperManager = builder.mapperManager();
        this.interceptors = builder.interceptors();
    }

    @Override
    public <T> CompletionStage<T> inTransaction(Function<DbTransaction, CompletionStage<T>> executor) {
        CompletionStage<Connection> connection = CompletableFuture.supplyAsync(connectionPool::connection, executorService)
                .thenApply(conn -> {
                    try {
                        conn.setAutoCommit(false);
                    } catch (SQLException e) {
                        throw new DbClientException("Failed to set autocommit to false", e);
                    }
                    return conn;
                });

        JdbcExecute execute = new JdbcExecute(statements,
                                              executorService,
                                              interceptors,
                                              connectionPool.dbType(),
                                              connection,
                                              dbMapperManager,
                                              mapperManager);

        execute.whenComplete(success -> {
            if (success) {
                execute.doCommit();
            } else {
                execute.doRollback();
            }
        });

        return executor.apply(execute);
    }

    @Override
    public <T> CompletionStage<T> execute(Function<DbExecute, CompletionStage<T>> executor) {
        CompletionStage<Connection> connection = CompletableFuture.supplyAsync(connectionPool::connection, executorService)
                .thenApply(conn -> {
                    try {
                        conn.setAutoCommit(true);
                    } catch (SQLException e) {
                        throw new DbClientException("Failed to set autocommit to true", e);
                    }
                    return conn;
                });

        JdbcExecute execute = new JdbcExecute(statements,
                                              executorService,
                                              interceptors,
                                              connectionPool.dbType(),
                                              connection,
                                              dbMapperManager,
                                              mapperManager);

        execute.whenComplete(success -> execute.doClose());

        return executor.apply(execute);
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

    private static class JdbcExecute extends AbstractDbExecute implements DbTransaction {
        private final JdbcExecuteContext context;
        private volatile boolean setRollbackOnly = false;

        JdbcExecute(DbStatements statements,
                    ExecutorService executorService,
                    InterceptorSupport interceptors,
                    String dbType,
                    CompletionStage<Connection> connection,
                    DbMapperManager dbMapperManager,
                    MapperManager mapperManager) {
            super(statements);

            this.context = JdbcExecuteContext.create(executorService,
                                                     interceptors,
                                                     dbType,
                                                     connection,
                                                     dbMapperManager,
                                                     mapperManager);
        }

        @Override
        public DbStatementQuery createNamedQuery(String statementName, String statement) {
            return new JdbcStatementQuery(context,
                                          JdbcStatementContext.create(DbStatementType.QUERY, statementName, statement));

        }

        @Override
        public DbStatementGet createNamedGet(String statementName, String statement) {
            return new JdbcStatementGet(context,
                                        JdbcStatementContext.create(DbStatementType.GET, statementName, statement));
        }

        @Override
        public DbStatementDml createNamedDmlStatement(String statementName, String statement) {
            return new JdbcStatementDml(context,
                                        JdbcStatementContext.create(DbStatementType.DML, statementName, statement));
        }

        @Override
        public DbStatementDml createNamedInsert(String statementName, String statement) {
            return new JdbcStatementDml(context,
                                        JdbcStatementContext.create(DbStatementType.INSERT, statementName, statement));
        }

        @Override
        public DbStatementDml createNamedUpdate(String statementName, String statement) {
            return new JdbcStatementDml(context,
                                        JdbcStatementContext.create(DbStatementType.UPDATE, statementName, statement));
        }

        @Override
        public DbStatementDml createNamedDelete(String statementName, String statement) {
            return new JdbcStatementDml(context,
                                        JdbcStatementContext.create(DbStatementType.DELETE, statementName, statement));
        }

        @Override
        public DbStatementGeneric createNamedStatement(String statementName, String statement) {
            return new JdbcStatementGeneric(context,
                                            JdbcStatementContext.create(DbStatementType.UNKNOWN, statementName, statement));
        }

        @Override
        public void rollback() {
            setRollbackOnly = true;
        }

        private void doRollback() {
            context.connection()
                    .thenApply(conn -> {
                        try {
                            conn.rollback();
                            conn.close();
                        } catch (SQLException e) {
                            throw new DbClientException("Failed to rollback a transaction, or close a connection", e);
                        }

                        return conn;
                    });
        }

        private void doCommit() {
            if (setRollbackOnly) {
                doRollback();
                return;
            }
            context.connection()
                    .thenApply(conn -> {
                        try {
                            conn.commit();
                            conn.close();
                        } catch (SQLException e) {
                            throw new DbClientException("Failed to commit a transaction, or close a connection", e);
                        }
                        return conn;
                    });
        }

        private void doClose() {
            context.connection()
                    .thenApply(conn -> {
                        try {
                            conn.close();
                        } catch (SQLException e) {
                            throw new DbClientException("Failed to close a connection", e);
                        }
                        return conn;
                    });
        }

        public void whenComplete(Consumer<Boolean> completionConsumer) {
            List<CompletionStage> connectionConsumers = new LinkedList<>();

        }
    }
}
