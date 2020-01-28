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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.dbclient.DbInterceptorContext;
import io.helidon.dbclient.DbResult;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRows;
import io.helidon.dbclient.DbStatementGeneric;

/**
 * Generic statement.
 */
class JdbcStatementGeneric extends JdbcStatement<DbStatementGeneric, DbResult> implements DbStatementGeneric {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(JdbcStatementGeneric.class.getName());

    private static final class GenericDbResult implements DbResult {

        private final CompletableFuture<DbRows<DbRow>> queryResultFuture;
        private final CompletableFuture<Long> dmlResultFuture;
        private final CompletableFuture<Throwable> exceptionFuture;

        GenericDbResult(
                final CompletableFuture<DbRows<DbRow>> queryResultFuture,
                final CompletableFuture<Long> dmlResultFuture,
                final CompletableFuture<Throwable> exceptionFuture
        ) {
            this.queryResultFuture = queryResultFuture;
            this.dmlResultFuture = dmlResultFuture;
            this.exceptionFuture = exceptionFuture;
        }

        @Override
        public DbResult whenDml(Consumer<Long> consumer) {
            dmlResultFuture.thenAccept(consumer);
            return this;
        }

        @Override
        public DbResult whenRs(Consumer<DbRows<DbRow>> consumer) {
            queryResultFuture.thenAccept(consumer);
            return this;
        }

        @Override
        public DbResult exceptionally(Consumer<Throwable> exceptionHandler) {
            exceptionFuture.thenAccept(exceptionHandler);
            return this;
        }

        @Override
        public CompletionStage<Long> dmlFuture() {
            return dmlResultFuture;
        }

        @Override
        public CompletionStage<DbRows<DbRow>> rsFuture() {
            return queryResultFuture;
        }

        @Override
        public CompletionStage<Throwable> exceptionFuture() {
            return exceptionFuture;
        }
    }

    JdbcStatementGeneric(JdbcExecuteContext executeContext,
                         JdbcStatementContext statementContext) {
        super(executeContext, statementContext);
    }

    @Override
    protected CompletionStage<DbResult> doExecute(CompletionStage<DbInterceptorContext> dbContextFuture,
                                                  CompletableFuture<Void> interceptorStatementFuture,
                                                  CompletableFuture<Long> interceptorQueryFuture) {

        executeContext().addFuture(interceptorQueryFuture);

        CompletableFuture<DbRows<DbRow>> queryResultFuture = new CompletableFuture<>();
        CompletableFuture<Long> dmlResultFuture = new CompletableFuture<>();
        CompletableFuture<Throwable> exceptionFuture = new CompletableFuture<>();
        CompletableFuture<JdbcStatementQuery.ResultWithConn> resultSetFuture = new CompletableFuture<>();

        dbContextFuture.exceptionally(throwable -> {
            resultSetFuture.completeExceptionally(throwable);

            return null;
        });
        // this is completed on execution of statement
        resultSetFuture.exceptionally(throwable -> {
            interceptorStatementFuture.completeExceptionally(throwable);
            queryResultFuture.completeExceptionally(throwable);
            dmlResultFuture.completeExceptionally(throwable);
            exceptionFuture.completeExceptionally(throwable);
            return null;
        });

        dbContextFuture.thenAccept(dbContext -> {
            // now let's execute the statement
            connection().thenAccept(conn -> {
                executorService().submit(() -> {
                    try {
                        PreparedStatement statement = super.build(conn, dbContext);

                        boolean isQuery = statement.execute();
                        // statement is executed, we can finish the statement future
                        interceptorStatementFuture.complete(null);

                        if (isQuery) {
                            ResultSet resultSet = statement.getResultSet();
                            // at this moment we have a DbRowResult
                            resultSetFuture.complete(new JdbcStatementQuery.ResultWithConn(resultSet, conn));
                        } else {
                            try {
                                long update = statement.getLargeUpdateCount();
                                interceptorQueryFuture.complete(update);
                                dmlResultFuture.complete(update);
                                statement.close();
                            } finally {
                                conn.close();
                            }
                        }
                    } catch (Exception e) {
                        if (null != conn) {
                            try {
                                // we would not close the connection in the resultSetFuture, so we have to close it here
                                conn.close();
                            } catch (SQLException ex) {
                                LOGGER.log(Level.WARNING,
                                        String.format("Failed to close connection: %s", ex.getMessage()),
                                        ex);
                            }
                        }
                        resultSetFuture.completeExceptionally(e);
                    }
                });
            });
        });


        /*
        TODO Too many futures
        Futures:
        interceptorStatementFuture - completed once we call the statement
        interceptorQueryFuture - completed once we read all records from a query (or finish a DML) - requires count
        queryResultFuture - completed once we know this is a result set and we have prepared the DbRowResult
        dmlResultFuture - completed once we know this is a DML statement
        exceptionFuture - completes in case of any exception
        resultSetFuture - completes if this is a result set and has the connection & result set objects
         */
        // for DML - everything is finished and done
        /*
          For Query
            Ignored:
                dmlResultFuture
            Completed:
                interceptorStatementFuture
                resultSetFuture
            Open:
                interceptorQueryFuture
                queryResultFuture
                exceptionFuture
         */

        // and now, let's construct the DbRowResult
        resultSetFuture.thenAccept(rsAndConn -> {
            DbRows<DbRow> dbRows = JdbcStatementQuery.processResultSet(
                    executorService(),
                    dbMapperManager(),
                    mapperManager(),
                    interceptorQueryFuture,
                    rsAndConn.resultSet());

            interceptorQueryFuture.exceptionally(throwable -> {
                exceptionFuture.complete(throwable);
                return null;
            });
            queryResultFuture.complete(dbRows);
        }).exceptionally(throwable -> {
            interceptorQueryFuture.completeExceptionally(throwable);
            queryResultFuture.completeExceptionally(throwable);
            exceptionFuture.complete(throwable);
            return null;
        });

        return interceptorStatementFuture.thenApply(nothing -> {
            return new GenericDbResult(queryResultFuture, dmlResultFuture, exceptionFuture);
        });
    }

}
