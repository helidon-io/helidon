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
package io.helidon.dbclient.mongodb;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbInterceptorContext;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementType;

import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.Document;

import static io.helidon.dbclient.mongodb.MongoDbStatement.READER_FACTORY;

/**
 * Executes Mongo specific query and returns result.
 * Utility class with static methods only.
 */
final class MongoDbQueryExecutor {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(MongoDbStatementQuery.class.getName());

    private MongoDbQueryExecutor() {
        throw new UnsupportedOperationException("Utility class MongoDbQueryExecutor instances are not allowed!");
    }

    static Multi<DbRow> executeQuery(MongoDbStatement dbStatement,
                                     CompletionStage<DbInterceptorContext> dbContextFuture,
                                     CompletableFuture<Void> statementFuture,
                                     CompletableFuture<Long> queryFuture) {

        dbContextFuture.exceptionally(throwable -> {
            statementFuture.completeExceptionally(throwable);
            queryFuture.completeExceptionally(throwable);
            return null;
        });

        String statement = dbStatement.build();
        MongoDbStatement.MongoStatement stmt;
        try {
            stmt = new MongoDbStatement.MongoStatement(DbStatementType.QUERY, READER_FACTORY, statement);
        } catch (IllegalStateException e) {
            // maybe this is a command?
            try {
                stmt = new MongoDbStatement.MongoStatement(DbStatementType.COMMAND, READER_FACTORY, statement);
            } catch (IllegalStateException ignored) {
                // we want to report the original exception
                throw e;
            }
        }

        if (stmt.getOperation() != MongoDbStatement.MongoOperation.QUERY) {
            if (stmt.getOperation() == MongoDbStatement.MongoOperation.COMMAND) {
                return MongoDbCommandExecutor.executeCommand(dbStatement,
                                                             dbContextFuture,
                                                             statementFuture,
                                                             queryFuture);
            } else {
                return Multi.error(new UnsupportedOperationException(
                        String.format("Operation %s is not supported by query", stmt.getOperation().toString())));
            }
        }
        // we reach here only for query statements

        // final variable required for lambda
        MongoDbStatement.MongoStatement usedStatement = stmt;
        return Single.from(dbContextFuture)
                .flatMap(it -> callStatement(dbStatement, usedStatement, statementFuture, queryFuture));
    }

    private static Multi<DbRow> callStatement(MongoDbStatement dbStatement,
                                              MongoDbStatement.MongoStatement mongoStmt,
                                              CompletableFuture<Void> statementFuture,
                                              CompletableFuture<Long> queryFuture) {

        final MongoCollection<Document> mc = dbStatement.db()
                .getCollection(mongoStmt.getCollection());
        final Document query = mongoStmt.getQuery();
        final Document projection = mongoStmt.getProjection();
        LOGGER.fine(() -> String.format(
                "Query: %s, Projection: %s", query.toString(), (projection != null ? projection : "N/A")));
        FindPublisher<Document> publisher = dbStatement.noTx()
                ? mc.find(query)
                : mc.find(dbStatement.txManager().tx(), query);
        if (projection != null) {
            publisher = publisher.projection(projection);
        }

        return Multi.from(new MongoDbRows<>(publisher,
                                            dbStatement,
                                            DbRow.class,
                                            statementFuture,
                                            queryFuture)
                                  .publisher());
    }
}
