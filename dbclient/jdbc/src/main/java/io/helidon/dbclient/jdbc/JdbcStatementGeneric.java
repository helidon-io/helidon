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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbInterceptorContext;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbResult;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRowResult;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.common.InterceptorSupport;

/**
 * Generic statement.
 */
class JdbcStatementGeneric extends JdbcStatement<JdbcStatementGeneric, DbResult> {
    private static final Logger LOGGER = Logger.getLogger(JdbcStatementGeneric.class.getName());

    JdbcStatementGeneric(ConnectionPool connectionPool,
                         ExecutorService executorService,
                         String statementName,
                         String statement,
                         DbMapperManager dbMapperMananger,
                         MapperManager mapperManager,
                         InterceptorSupport interceptors) {
        super(DbStatementType.UNKNOWN,
              connectionPool,
              statementName,
              statement,
              dbMapperMananger,
              mapperManager,
              executorService,
              interceptors);
    }

    @Override
    protected DbResult doExecute(CompletionStage<DbInterceptorContext> dbContextFuture,
                                 CompletableFuture<Void> interceptorStatementFuture,
                                 CompletableFuture<Long> interceptorQueryFuture) {

        CompletableFuture<DbRowResult<DbRow>> queryResultFuture = new CompletableFuture<>();
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
            executorService().submit(() -> {
                Connection conn = null;
                try {
                    conn = connection();
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
                            LOGGER.log(Level.WARNING, "Failed to close connection", ex);
                        }
                    }
                    resultSetFuture.completeExceptionally(e);
                }
            });
        });


        /*
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
            DbRowResult<DbRow> dbRowResult = JdbcStatementQuery.processResultSet(interceptorQueryFuture,
                                                                                 resultSetFuture,
                                                                                 executorService(),
                                                                                 dbMapperManager(),
                                                                                 mapperManager());
            interceptorQueryFuture.exceptionally(throwable -> {
                exceptionFuture.complete(throwable);
                return null;
            });
            queryResultFuture.complete(dbRowResult);
        }).exceptionally(throwable -> {
            interceptorQueryFuture.completeExceptionally(throwable);
            queryResultFuture.completeExceptionally(throwable);
            exceptionFuture.complete(throwable);
            return null;
        });

        return new DbResult() {
            @Override
            public DbResult whenDml(Consumer<Long> consumer) {
                dmlResultFuture.thenAccept(consumer);
                return this;
            }

            @Override
            public DbResult whenRs(Consumer<DbRowResult<DbRow>> consumer) {
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
            public CompletionStage<DbRowResult<DbRow>> rsFuture() {
                return queryResultFuture;
            }

            @Override
            public CompletionStage<Throwable> exceptionFuture() {
                return exceptionFuture;
            }
        };
    }
}
