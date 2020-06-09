/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.mapper.MapperManager;
import io.helidon.common.reactive.CompletionAwaitable;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.common.reactive.Subscribable;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbClientService;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbStatementDml;
import io.helidon.dbclient.DbStatementGet;
import io.helidon.dbclient.DbStatementQuery;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.DbStatements;
import io.helidon.dbclient.DbTransaction;
import io.helidon.dbclient.common.AbstractDbExecute;
import io.helidon.dbclient.common.DbStatementContext;

/**
 * Helidon DB implementation for JDBC drivers.
 */
class JdbcDbClient implements DbClient {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(DbClient.class.getName());

    private final ExecutorService executorService;
    private final ConnectionPool connectionPool;
    private final DbStatements statements;
    private final DbMapperManager dbMapperManager;
    private final MapperManager mapperManager;
    private final List<DbClientService> clientServices;

    JdbcDbClient(JdbcDbClientProviderBuilder builder) {
        this.executorService = builder.executorService();
        this.connectionPool = builder.connectionPool();
        this.statements = builder.statements();
        this.dbMapperManager = builder.dbMapperManager();
        this.mapperManager = builder.mapperManager();
        this.clientServices = builder.clientServices();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U, T extends Subscribable<U>> T inTransaction(Function<DbTransaction, T> executor) {

        JdbcTxExecute execute = new JdbcTxExecute(
                statements,
                executorService,
                clientServices,
                connectionPool,
                dbMapperManager,
                mapperManager);

        T result = executor.apply(execute);

        if (result instanceof Multi) {
            Multi<U> multi = (Multi<U>) result;

            CompletableFuture<Multi<U>> commitFuture = new CompletableFuture<>();
            multi = multi.onComplete(() -> {
                // if result completes without an exception, we attempt a commit
                execute.doCommit()
                        .thenAccept(it -> commitFuture.complete(Multi.empty()))
                        .exceptionally(it -> {
                            commitFuture.complete(Multi.error(it));
                            return null;
                        });
            });

            /*
                This does the following:
                - when the db statement finishes, we get a result from the commitFuture
                - we then want to amend the multi with the result of commit (only interested in error)
                - so here we resume with the commit future that provides us with either empty multi, or error multi
                - the flatMap just returns the multi result of the completion stage
            */
            multi = multi.onCompleteResumeWith(Single.create(commitFuture).flatMap(Function.identity()));

            // if result completes with an exception, or commit failed, we attempt a rollback
            multi = multi.onError(throwable -> {
                RollbackHandler.create(execute, Level.WARNING).apply(throwable);
            });

            return (T) multi;
        } else if (result instanceof Single) {
            Single<U> single = (Single<U>) result;

            CompletionAwaitable<U> future = single.thenCompose(it -> execute.doCommit()
                    .thenApply(conn -> it));

            future = future.exceptionally(RollbackHandler.create(execute, Level.WARNING));

            return (T) Single.create(future);
        } else {
            execute.doRollback();
            throw new IllegalStateException("You must return a Single or Multi instance to inTransaction, yet "
                                                    + "you provided: " + result.getClass().getName());
        }
    }

    /**
     * Functional interface called to rollback failed transaction.
     *
     * @param <T> statement execution result type
     */
    private static final class RollbackHandler<T> implements Function<Throwable, T> {

        private final JdbcTxExecute execute;
        private final Level level;

        private static <T> RollbackHandler<T> create(final JdbcTxExecute execute, final Level level) {
            return new RollbackHandler<>(execute, level);
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

    @SuppressWarnings("unchecked")
    @Override
    public <U, T extends Subscribable<U>> T execute(Function<DbExecute, T> executor) {

        JdbcExecute execute = new JdbcExecute(statements,
                                              JdbcExecute.createContext(statements,
                                                                        executorService,
                                                                        clientServices,
                                                                        connectionPool,
                                                                        dbMapperManager,
                                                                        mapperManager));

        Subscribable<U> result;

        try {
            result = executor.apply(execute);
        } catch (RuntimeException e) {
            execute.close();
            throw e;
        }

        result = result.onComplete(() -> {
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
        });

        result = result.onError(throwable -> {
            LOGGER.log(Level.FINEST,
                       String.format("Execution failed: %s", throwable.getMessage()),
                       throwable);
            execute.close();
        });

        return (T) result;
    }

    @Override
    public Single<Void> ping() {
        return execute(exec -> exec.namedUpdate("ping"))
                .flatMapSingle(it -> Single.empty());
    }

    @Override
    public String dbType() {
        return connectionPool.dbType();
    }

    private static final class JdbcTxExecute extends JdbcExecute implements DbTransaction {

        private volatile boolean setRollbackOnly = false;

        private JdbcTxExecute(DbStatements statements,
                              ExecutorService executorService,
                              List<DbClientService> clientServices,
                              ConnectionPool connectionPool,
                              DbMapperManager dbMapperManager,
                              MapperManager mapperManager) {
            super(statements, JdbcExecuteContext.jdbcBuilder()
                    .statements(statements)
                    .clientServices(clientServices)
                    .dbType(connectionPool.dbType())
                    .connection(createConnection(executorService, connectionPool))
                    .dbMapperManager(dbMapperManager)
                    .mapperManager(mapperManager)
                    .executorService(executorService)
                    .build());
        }

        private static CompletionStage<Connection> createConnection(ExecutorService executorService,
                                                                    ConnectionPool connectionPool) {
            return CompletableFuture.supplyAsync(connectionPool::connection, executorService)
                    .thenApply(conn -> {
                        try {
                            conn.setAutoCommit(false);
                        } catch (SQLException e) {
                            throw new DbClientException("Failed to set autocommit to false", e);
                        }
                        return conn;
                    });
        }

        @Override
        public void rollback() {
            setRollbackOnly = true;
        }

        private CompletionStage<Void> doRollback() {
            return context().connection()
                    .thenApply(conn -> {
                        try {
                            conn.rollback();
                            conn.close();
                        } catch (SQLException e) {
                            throw new DbClientException("Failed to rollback a transaction, or close a connection", e);
                        }

                        return null;
                    });
        }

        private CompletionStage<Void> doCommit() {
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
                        return null;
                    });
        }
    }

    private static class JdbcExecute extends AbstractDbExecute {

        private final JdbcExecuteContext context;

        private JdbcExecute(DbStatements statements, JdbcExecuteContext context) {
            super(statements);

            this.context = context;
        }

        private static JdbcExecuteContext createContext(DbStatements statements,
                                                        ExecutorService executorService,
                                                        List<DbClientService> clientServices,
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

            return JdbcExecuteContext.jdbcBuilder()
                    .statements(statements)
                    .executorService(executorService)
                    .connection(connection)
                    .clientServices(clientServices)
                    .dbMapperManager(dbMapperManager)
                    .mapperManager(mapperManager)
                    .dbType(connectionPool.dbType())
                    .build();
        }

        @Override
        public DbStatementQuery createNamedQuery(String statementName, String statement) {
            return new JdbcStatementQuery(context,
                                          DbStatementContext.create(context, DbStatementType.QUERY, statementName, statement));

        }

        @Override
        public DbStatementGet createNamedGet(String statementName, String statement) {
            return new JdbcStatementGet(context,
                                        DbStatementContext.create(context, DbStatementType.GET, statementName, statement));
        }

        @Override
        public DbStatementDml createNamedDmlStatement(String statementName, String statement) {
            return new JdbcStatementDml(context,
                                        DbStatementContext.create(context, DbStatementType.DML, statementName, statement));
        }

        @Override
        public DbStatementDml createNamedInsert(String statementName, String statement) {
            return new JdbcStatementDml(context,
                                        DbStatementContext.create(context, DbStatementType.INSERT, statementName, statement));
        }

        @Override
        public DbStatementDml createNamedUpdate(String statementName, String statement) {
            return new JdbcStatementDml(context,
                                        DbStatementContext.create(context, DbStatementType.UPDATE, statementName, statement));
        }

        @Override
        public DbStatementDml createNamedDelete(String statementName, String statement) {
            return new JdbcStatementDml(context,
                                        DbStatementContext.create(context, DbStatementType.DELETE, statementName, statement));
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
