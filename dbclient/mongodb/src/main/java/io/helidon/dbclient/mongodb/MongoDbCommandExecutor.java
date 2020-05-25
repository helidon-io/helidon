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
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.common.DbClientContext;

import com.mongodb.reactivestreams.client.MongoDatabase;
import org.bson.Document;
import org.reactivestreams.Publisher;

import static io.helidon.dbclient.mongodb.MongoDbStatement.READER_FACTORY;

/**
 * Executes Mongo specific database command and returns result.
 * Utility class with static methods only.
 */
final class MongoDbCommandExecutor {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(MongoDbCommandExecutor.class.getName());

    private MongoDbCommandExecutor() {
        throw new UnsupportedOperationException("Utility class MongoDbCommandExecutor instances are not allowed!");
    }

    static Multi<DbRow> executeCommand(MongoDbStatement dbStatement,
                                       CompletionStage<DbClientServiceContext> dbContextFuture,
                                       CompletableFuture<Void> statementFuture,
                                       CompletableFuture<Long> commandFuture) {

        dbContextFuture.exceptionally(throwable -> {
            statementFuture.completeExceptionally(throwable);
            commandFuture.completeExceptionally(throwable);
            return null;
        });

        CompletionStage<MongoDbStatement.MongoStatement> mongoStmtFuture = dbContextFuture.thenApply(dbContext -> {
            MongoDbStatement.MongoStatement stmt
                    = new MongoDbStatement.MongoStatement(DbStatementType.COMMAND, READER_FACTORY, dbStatement.build());
            if (stmt.getOperation() == MongoDbStatement.MongoOperation.COMMAND) {
                return stmt;
            } else {
                throw new UnsupportedOperationException(
                        String.format("Operation %s is not supported", stmt.getOperation().toString()));
            }
        });

        return executeCommandInMongoDB(dbStatement, mongoStmtFuture, statementFuture, commandFuture);
    }

    private static Multi<DbRow> executeCommandInMongoDB(MongoDbStatement dbStatement,
                                                        CompletionStage<MongoDbStatement.MongoStatement> stmtFuture,
                                                        CompletableFuture<Void> statementFuture,
                                                        CompletableFuture<Long> commandFuture) {

        return Single.from(stmtFuture)
                .flatMap(mongoStmt -> callStatement(dbStatement, mongoStmt, statementFuture, commandFuture));
    }

    private static Flow.Publisher<DbRow> callStatement(MongoDbStatement dbStatement,
                                                       MongoDbStatement.MongoStatement mongoStmt,
                                                       CompletableFuture<Void> statementFuture,
                                                       CompletableFuture<Long> commandFuture) {

        MongoDatabase db = dbStatement.db();
        Document command = mongoStmt.getQuery();
        LOGGER.fine(() -> String.format("Command: %s", command.toString()));
        Publisher<Document> publisher = dbStatement.noTx()
                ? db.runCommand(command)
                : db.runCommand(dbStatement.txManager().tx(), command);

        return new CommandRows(publisher,
                               dbStatement,
                               statementFuture,
                               commandFuture)
                .publisher();
    }

    static final class CommandRows {

        private final AtomicBoolean resultRequested = new AtomicBoolean(false);
        private final Publisher<Document> publisher;
        private final DbClientContext clientContext;
        private final MongoDbStatement dbStatement;
        private final CompletableFuture<Void> statementFuture;
        private final CompletableFuture<Long> commandFuture;

        CommandRows(Publisher<Document> publisher,
                    MongoDbStatement dbStatement,
                    CompletableFuture<Void> statementFuture,
                    CompletableFuture<Long> commandFuture) {
            this.clientContext = dbStatement.clientContext();
            this.publisher = publisher;
            this.dbStatement = dbStatement;
            this.statementFuture = statementFuture;
            this.commandFuture = commandFuture;
        }

        public Flow.Publisher<DbRow> publisher() {
            checkResult();
            return toDbPublisher();
        }

        private Flow.Publisher<DbRow> toDbPublisher() {
            MongoDbQueryProcessor qp = new MongoDbQueryProcessor(clientContext,
                                                                 dbStatement,
                                                                 statementFuture,
                                                                 commandFuture);
            publisher.subscribe(qp);
            return qp;
        }

        private void checkResult() {
            if (resultRequested.get()) {
                throw new IllegalStateException("Result has already been requested");
            }
            resultRequested.set(true);
        }

    }

}
