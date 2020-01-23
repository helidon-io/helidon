/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
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
    static {
        HelidonFeatures.register(HelidonFlavor.SE, "DbClient", "JDBC");
    }

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(DbClient.class.getName());

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
    @SuppressWarnings("unchecked")
    public <T> CompletionStage<T> inTransaction(Function<DbTransaction, CompletionStage<T>> executor) {

        JdbcTxExecute execute = new JdbcTxExecute(
                statements,
                executorService,
                interceptors,
                connectionPool,
                dbMapperManager,
                mapperManager);
        CompletionStage<T> stage = executor.apply(execute)
                .thenApply(it -> {
                    execute.context().whenComplete()
                            .thenAccept(nothing -> {
                                LOGGER.finest(() -> "Transaction commit");
                                execute.doCommit().exceptionally(RollbackHandler.create(execute, Level.WARNING));
                            }).exceptionally(RollbackHandler.create(execute, Level.WARNING));
                    return it;
                });

        stage.exceptionally(RollbackHandler.create(execute, Level.FINEST));

        return stage;
    }

    /**
     * Functional interface called to rollback failed transaction.
     *
     * @param <T> statement execution result type
     */
    private static final class RollbackHandler<T> implements Function<Throwable, T> {

        private final JdbcTxExecute execute;
        private final Level level;

        private static RollbackHandler create(final JdbcTxExecute execute, final Level level) {
            return new RollbackHandler(execute, level);
        }

        private RollbackHandler(final JdbcTxExecute execute, final Level level) {
            this.execute = execute;
            this.level = level;
        }

        @Override
        public T apply(Throwable t) {
            LOGGER.log(level,
                    String.format("Transaction rollback: %s", t.getMessage()),
                    t);
            execute.doRollback().exceptionally(t2 -> {
                LOGGER.log(level,
                        String.format("Transaction rollback failed: %s", t2.getMessage()),
                        t2);
                return null;
            });
            return null;
        }

    }

    @Override
    public <T extends CompletionStage<?>> T execute(Function<DbExecute, T> executor) {
        JdbcExecute execute = new JdbcExecute(statements,
                                              executorService,
                                              interceptors,
                                              connectionPool,
                                              dbMapperManager,
                                              mapperManager);

        T resultFuture = executor.apply(execute);

        resultFuture.thenApply(it -> {
            execute.context().whenComplete()
                    .thenAccept(nothing -> {
                        LOGGER.finest(() -> "Execution finished, closing connection");
                        execute.close();
                    }).exceptionally(throwable -> {
                LOGGER.log(Level.WARNING,
                        String.format("Execution failed: %s", throwable.getMessage()),
                        throwable);
                execute.close();
                return null;
            });
            return it;
        });

        resultFuture.exceptionally(throwable -> {
            LOGGER.log(Level.FINEST,
                        String.format("Execution failed: %s", throwable.getMessage()),
                        throwable);
            execute.close();
            return null;
        });

        return resultFuture;
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

    private static final class JdbcTxExecute extends JdbcExecute implements DbTransaction {

        private volatile boolean setRollbackOnly = false;

        private JdbcTxExecute(DbStatements statements,
                              ExecutorService executorService,
                              InterceptorSupport interceptors,
                              ConnectionPool connectionPool,
                              DbMapperManager dbMapperManager,
                              MapperManager mapperManager) {
            super(statements, createTxContext(executorService, interceptors, connectionPool, dbMapperManager, mapperManager));
        }

        private static JdbcExecuteContext createTxContext(ExecutorService executorService,
                                                          InterceptorSupport interceptors,
                                                          ConnectionPool connectionPool,
                                                          DbMapperManager dbMapperManager,
                                                          MapperManager mapperManager) {
            CompletionStage<Connection> connection = CompletableFuture.supplyAsync(connectionPool::connection, executorService)
                    .thenApply(conn -> {
                        try {
                            conn.setAutoCommit(false);
                        } catch (SQLException e) {
                            throw new DbClientException("Failed to set autocommit to false", e);
                        }
                        return conn;
                    });

            return JdbcExecuteContext.create(executorService,
                                             interceptors,
                                             connectionPool.dbType(),
                                             connection,
                                             dbMapperManager,
                                             mapperManager);
        }

        @Override
        public void rollback() {
            setRollbackOnly = true;
        }

        private CompletionStage<Connection> doRollback() {
            return context().connection()
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

        private CompletionStage<Connection> doCommit() {
            if (setRollbackOnly) {
                return doRollback();
            }
            return context().connection()
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
    }

    private static class JdbcExecute extends AbstractDbExecute {

        private final JdbcExecuteContext context;

        private JdbcExecute(DbStatements statements, JdbcExecuteContext context) {
            super(statements);

            this.context = context;
        }

        private JdbcExecute(DbStatements statements,
                            ExecutorService executorService,
                            InterceptorSupport interceptors,
                            ConnectionPool connectionPool,
                            DbMapperManager dbMapperManager,
                            MapperManager mapperManager) {
            this(statements, createContext(executorService, interceptors, connectionPool, dbMapperManager, mapperManager));
        }

        private static JdbcExecuteContext createContext(ExecutorService executorService,
                                                        InterceptorSupport interceptors,
                                                        ConnectionPool connectionPool,
                                                        DbMapperManager dbMapperManager,
                                                        MapperManager mapperManager) {
            CompletionStage<Connection> connection = CompletableFuture.supplyAsync(connectionPool::connection, executorService)
                    .thenApply(conn -> {
                        try {
                            conn.setAutoCommit(true);
                        } catch (SQLException e) {
                            throw new DbClientException("Failed to set autocommit to true", e);
                        }
                        return conn;
                    });

            return JdbcExecuteContext.create(executorService,
                                             interceptors,
                                             connectionPool.dbType(),
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

        JdbcExecuteContext context() {
            return context;
        }

        void close() {
            context.connection()
                    .thenAccept(conn -> {
                        try {
                            conn.close();
                        } catch (SQLException e) {
                            LOGGER.log(Level.WARNING, String.format("Could not close connection: %s", e.getMessage()), e);
                        }
                    });
        }
    }

}
