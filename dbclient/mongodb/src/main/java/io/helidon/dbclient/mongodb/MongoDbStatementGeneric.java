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
package io.helidon.dbclient.mongodb;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.logging.Logger;

import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbInterceptorContext;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbResult;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRows;
import io.helidon.dbclient.DbStatementGeneric;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.common.InterceptorSupport;

import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * Generic statement for mongoDB.
 */
public class MongoDbStatementGeneric extends MongoDbStatement<DbStatementGeneric, DbResult> implements DbStatementGeneric {

    private abstract static class MongoDbAbstractResult<T> implements DbResult {

        private final CompletionStage<T> resultFuture;
        private final CompletableFuture<Throwable> throwableFuture;

        private MongoDbAbstractResult(CompletionStage<T> resultFuture) {
            this.resultFuture = resultFuture;
            throwableFuture = new CompletableFuture<>();
            resultFuture.exceptionally(throwable -> {
                throwableFuture.complete(throwable);
                return null;
            });
        }

        @Override
        public DbResult exceptionally(Consumer<Throwable> exceptionHandler) {
            resultFuture.exceptionally(throwable -> {
                exceptionHandler.accept(throwable);
                return null;
            });
            return this;
        }

        @Override
        public CompletionStage<Throwable> exceptionFuture() {
            return throwableFuture;
        }

        CompletionStage<T> resultFuture() {
            return resultFuture;
        }

    }

    private static final class MongoDbQueryResult extends MongoDbAbstractResult<DbRows<DbRow>> {

        private MongoDbQueryResult(CompletionStage<DbRows<DbRow>> dbRowsFuture) {
            super(dbRowsFuture);
        }

        @Override
        public DbResult whenDml(Consumer<Long> consumer) {
            throw new IllegalStateException("Statement is not DML.");
        }

        @Override
        public DbResult whenRs(Consumer<DbRows<DbRow>> consumer) {
            resultFuture().thenAccept(consumer);
            return this;
        }

        @Override
        public CompletionStage<Long> dmlFuture() {
            throw new IllegalStateException("Statement is not DML.");
        }

        @Override
        public CompletionStage<DbRows<DbRow>> rsFuture() {
            return resultFuture();
        }

    }

    private static final class MongoDbDmlResult extends MongoDbAbstractResult<Long> {

        private MongoDbDmlResult(CompletionStage<Long> dmlResultFuture) {
            super(dmlResultFuture);
        }

        @Override
        public DbResult whenDml(Consumer<Long> consumer) {
            resultFuture().thenAccept(consumer);
            return this;
        }

        @Override
        public DbResult whenRs(Consumer<DbRows<DbRow>> consumer) {
            throw new IllegalStateException("Statement is not query.");
        }

        @Override
        public CompletionStage<Long> dmlFuture() {
            return resultFuture();
        }

        @Override
        public CompletionStage<DbRows<DbRow>> rsFuture() {
            throw new IllegalStateException("Statement is not query.");
        }

    }

    private static final Logger LOGGER = Logger.getLogger(MongoDbStatementGeneric.class.getName());

    private DbStatementType dbStatementType;

    private MongoStatement statement;

    MongoDbStatementGeneric(
            DbStatementType dbStatementType,
            MongoDatabase db,
            String statementName,
            String statement,
            DbMapperManager dbMapperManager,
            MapperManager mapperManager,
            InterceptorSupport interceptors
    ) {
        super(dbStatementType,
              db,
              statementName,
              statement,
              dbMapperManager,
              mapperManager,
              interceptors);
        this.dbStatementType = dbStatementType;
    }

    @Override
    public CompletionStage<DbResult> execute() {
        statement = new MongoStatement(dbStatementType, READER_FACTORY, build());
        switch (statement.getOperation()) {
        case QUERY:
            dbStatementType = DbStatementType.QUERY;
            break;
        case INSERT:
            dbStatementType = DbStatementType.INSERT;
            break;
        case UPDATE:
            dbStatementType = DbStatementType.UPDATE;
            break;
        case DELETE:
            dbStatementType = DbStatementType.DELETE;
            break;
        // Command not related to a specific collection can be executed only as generic statement.
        case COMMAND:
            dbStatementType = DbStatementType.COMMAND;
            break;
        default:
            throw new IllegalStateException(
                    String.format("Unexpected value for generic statement: %s", statement.getOperation()));
        }
        return super.execute();
    }

    @Override
    protected CompletionStage<DbResult> doExecute(
            CompletionStage<DbInterceptorContext> dbContextFuture,
            CompletableFuture<Void> statementFuture,
            CompletableFuture<Long> queryFuture
    ) {
        switch (dbStatementType) {
            case QUERY:
                return CompletableFuture.completedFuture(new MongoDbQueryResult(
                        MongoDbQueryExecutor.executeQuery(
                                this,
                                dbContextFuture,
                                statementFuture,
                                queryFuture)
                ));
            case INSERT:
            case UPDATE:
            case DELETE:
                return CompletableFuture.completedFuture(new MongoDbDmlResult(
                        MongoDbDMLExecutor.executeDml(
                                this,
                                dbStatementType,
                                statement,
                                dbContextFuture,
                                statementFuture,
                                queryFuture)
                ));
            case COMMAND:
                return CompletableFuture.completedFuture(new MongoDbQueryResult(
                        MongoDbCommandExecutor.executeCommand(
                                this,
                                dbContextFuture,
                                statementFuture,
                                queryFuture)
                ));
            default:
                throw new UnsupportedOperationException(
                        String.format("Operation %s is not supported.", dbStatementType.name()));
        }
    }

}
