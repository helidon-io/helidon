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
package io.helidon.db.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Logger;

import io.helidon.common.mapper.MapperManager;
import io.helidon.common.reactive.Flow;
import io.helidon.db.DbException;
import io.helidon.db.DbInterceptor;
import io.helidon.db.DbMapperManager;
import io.helidon.db.DbResult;
import io.helidon.db.DbRow;
import io.helidon.db.DbRowResult;
import io.helidon.db.DbStatement;
import io.helidon.db.DbStatements;
import io.helidon.db.HelidonDb;
import io.helidon.db.HelidonDbExecute;

/**
 * Helidon DB implementation for JDBC drivers.
 */
class JdbcDb implements HelidonDb {

    /**
     * Local logger instance.
     */
    private static final Logger LOGGER = Logger.getLogger(JdbcDb.class.getName());

    private final ExecutorService executorService;
    private final ConnectionPool connectionPool;
    private final DbStatements statements;
    private final DbMapperManager dbMapperMananger;
    private final MapperManager mapperManager;
    private final List<DbInterceptor> interceptors = new LinkedList<>();

    JdbcDb(JdbcDbProviderBuilder builder) {
        this.executorService = builder.executorService();
        this.connectionPool = builder.connectionPool();
        this.statements = builder.statements();
        this.dbMapperMananger = builder.dbMapperMananger();
        this.mapperManager = builder.mapperManager();
        this.interceptors.addAll(builder.interceptors());
    }

    @Override
    public <T> T inTransaction(Function<HelidonDbExecute, T> executor) {
        return executor.apply(new JdbcExecute(executorService, statements, interceptors));
    }

    @Override
    public <T> T execute(Function<HelidonDbExecute, T> executor) {
        return executor.apply(new JdbcExecute(executorService, statements, interceptors));
    }

    @Override
    public CompletionStage<Void> ping() {
        return execute(exec -> exec.namedUpdate("ping"))
                // need to get from Long to Void
                .thenRun(() -> {
                });
    }

    private class JdbcExecute implements HelidonDbExecute {

        private final ExecutorService executorService;
        private final DbStatements statements;
        private final List<DbInterceptor> interceptors;

        JdbcExecute(ExecutorService executorService,
                    DbStatements statements,
                    List<DbInterceptor> interceptors) {
            this.executorService = executorService;
            this.statements = statements;
            this.interceptors = interceptors;
        }

        @Override
        public DbRowResult<DbRow> namedQuery(String statementName, Object... parameters) {
            return createNamedQuery(statementName)
                    .params(parameters)
                    .execute();
        }

        @Override
        public CompletionStage<Long> namedInsert(String statementName, Object... parameters) {
            return doExecuteUpdate(statements.statement(statementName),
                                   parameters,
                                   executorService);
        }

        @Override
        public CompletionStage<Optional<DbRow>> namedGet(String statementName, Object... parameters) {
            return doExecuteGet(
                    statementName,
                    statements.statement(statementName),
                    parameters,
                    executorService);
        }

        private void prepareStatement(PreparedStatement st, Object[] parameters) throws SQLException {
            for (int i = 0; i < parameters.length; i++) {
                Object parameter = parameters[i];
                st.setObject(i + 1, parameter);
            }
        }

        private CompletionStage<Long> doExecuteUpdate(String statement, Object[] parameters, ExecutorService executorService) {
            CompletableFuture<Long> result = new CompletableFuture<>();

            executorService.submit(() -> {
                try (Connection jdbcConnection = connectionPool.connection()) {
                    PreparedStatement preparedStatement = jdbcConnection.prepareStatement(statement);
                    prepareStatement(preparedStatement, parameters);
                    result.complete(preparedStatement.executeLargeUpdate());
                } catch (Exception e) {
                    result.completeExceptionally(e);
                }
            });

            return result;
        }

        private CompletionStage<Optional<DbRow>> doExecuteGet(String statementName,
                                                              String statement,
                                                              Object[] parameters,
                                                              ExecutorService executorService) {

            CompletableFuture<Optional<DbRow>> result = new CompletableFuture<>();

            createNamedQuery(statementName)
                    .params(parameters)
                    .execute()
                    .publisher()
                    .subscribe(new Flow.Subscriber<DbRow>() {
                        private Flow.Subscription subscription;
                        private final AtomicBoolean done = new AtomicBoolean(false);
                        // defense against bad publisher - if I receive complete after cancelled...
                        private final AtomicBoolean cancelled = new AtomicBoolean(false);
                        private final AtomicReference<DbRow> theRow = new AtomicReference<>();

                        @Override
                        public void onSubscribe(Flow.Subscription subscription) {
                            this.subscription = subscription;
                            subscription.request(2);
                        }

                        @Override
                        public void onNext(DbRow dbRow) {
                            if (done.get()) {
                                subscription.cancel();
                                result.completeExceptionally(new DbException("Result of get statement " + statement + " returned "
                                                                                     + "more than one row."));
                                cancelled.set(true);
                            } else {
                                theRow.set(dbRow);
                                done.set(true);
                            }
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            if (cancelled.get()) {
                                return;
                            }
                            result.completeExceptionally(throwable);
                        }

                        @Override
                        public void onComplete() {
                            if (cancelled.get()) {
                                return;
                            }
                            result.complete(Optional.of(theRow.get()));
                        }
                    });

            return result;
        }

        @Override
        public DbStatement<CompletionStage<Optional<DbRow>>> createNamedGet(String statementName) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools
        }

        @Override
        public DbStatement<CompletionStage<Optional<DbRow>>> createGet(String statement) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools
        }

        @Override
        public DbStatement<DbRowResult<DbRow>> createNamedQuery(String statementName) {
            return new JdbcStatementQuery(connectionPool,
                                          executorService,
                                          statementName,
                                          statements.statement(statementName),
                                          dbMapperMananger,
                                          mapperManager,
                                          interceptors);
        }

        @Override
        public DbStatement<DbRowResult<DbRow>> createQuery(String statement) {
            return new JdbcStatementQuery(connectionPool,
                                          executorService,
                                          generateName("q", statement),
                                          statement,
                                          dbMapperMananger,
                                          mapperManager,
                                          interceptors);
        }

        @Override
        public DbStatement<DbResult> createNamedStatement(String statementName) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools
            // | Templates.
        }

        @Override
        public DbStatement<DbResult> createStatement(String statement) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools
            // | Templates.
        }

        @Override
        public DbStatement<CompletionStage<Long>> createNamedDmlStatement(String statementName) {
            return new JdbcStatementDml(connectionPool,
                                        executorService,
                                        statementName,
                                        statements.statement(statementName),
                                        dbMapperMananger,
                                        mapperManager);
        }

        @Override
        public DbStatement<CompletionStage<Long>> createDmlStatement(String statement) {
            return new JdbcStatementDml(connectionPool,
                                        executorService,
                                        generateName("dml", statement),
                                        statement,
                                        dbMapperMananger,
                                        mapperManager);
        }

    }

    private String generateName(String type, String statement) {
        return type + "_" + UUID.randomUUID();
    }
}
