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
package io.helidon.dbclient.mongodb;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

import io.helidon.dbclient.DbInterceptorContext;
import io.helidon.dbclient.DbStatementType;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.Success;
import org.bson.Document;
import org.reactivestreams.Subscription;

import static io.helidon.dbclient.mongodb.MongoDbStatement.EMPTY;

/**
 * Executes Mongo specific DML statement and returns result.
 * Utility class with static methods only.
 */
final class MongoDbDMLExecutor {

    private static final Logger LOGGER = Logger.getLogger(MongoDbDMLExecutor.class.getName());

    private MongoDbDMLExecutor() {
        throw new UnsupportedOperationException("Utility class MongoDbDMLExecutor instances are not allowed!");
    }

    static CompletionStage<Long> executeDml(
            MongoDbStatement dbStatement,
            DbStatementType dbStatementType,
            MongoDbStatement.MongoStatement mongoStatement,
            CompletionStage<DbInterceptorContext> dbContextFuture,
            CompletableFuture<Void> statementFuture,
            CompletableFuture<Long> queryFuture
    ) {

        // if the iterceptors fail with exception, we must fail as well
        dbContextFuture.exceptionally(throwable -> {
            statementFuture.completeExceptionally(throwable);
            queryFuture.completeExceptionally(throwable);
            return null;
        });

        return dbContextFuture.thenCompose(dbContext -> {
            switch (dbStatementType) {
            case INSERT:
                return executeInsert(dbStatement, dbStatementType, mongoStatement, statementFuture, queryFuture);
            case UPDATE:
                return executeUpdate(dbStatement, dbStatementType, mongoStatement, statementFuture, queryFuture);
            case DELETE:
                return executeDelete(dbStatement, dbStatementType, mongoStatement, statementFuture, queryFuture);
            default:
                CompletableFuture<Long> result = new CompletableFuture<>();
                Throwable failure = new UnsupportedOperationException(
                        "Statement operation not yet supported: " + dbStatementType.name());
                result.completeExceptionally(failure);
                statementFuture.completeExceptionally(failure);
                queryFuture.completeExceptionally(failure);
                return result;
            }
        });
    }

    private abstract static class DmlResultSubscriber<T> implements org.reactivestreams.Subscriber<T> {

        private final DbStatementType dbStatementType;
        private final CompletableFuture<Void> statementFuture;
        private final CompletableFuture<Long> queryFuture;
        private final LongAdder count;

        private DmlResultSubscriber(
                DbStatementType dbStatementType,
                CompletableFuture<Long> queryFuture,
                CompletableFuture<Void> statementFuture
        ) {
            this.dbStatementType = dbStatementType;
            this.statementFuture = statementFuture;
            this.queryFuture = queryFuture;
            this.count = new LongAdder();
        }

        @Override
        public void onSubscribe(Subscription s) {
            // no need for flow control, we only add the result
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onError(Throwable t) {
            statementFuture.completeExceptionally(t);
            queryFuture.completeExceptionally(t);
        }

        @Override
        public void onComplete() {
            statementFuture.complete(null);
            queryFuture.complete(count.sum());
            LOGGER.fine(() -> String.format("%s DML execution succeeded", dbStatementType.name()));
        }

        LongAdder count() {
            return count;
        }

    }

    private static final class InsertResultSubscriber extends DmlResultSubscriber<Success> {

        private InsertResultSubscriber(
                DbStatementType dbStatementType,
                CompletableFuture<Long> queryFuture,
                CompletableFuture<Void> statementFuture
        ) {
            super(dbStatementType, queryFuture, statementFuture);
        }

        @Override
        public void onNext(Success r) {
            count().increment();
        }

    }

    private static final class UpdateResultSubscriber extends DmlResultSubscriber<UpdateResult> {

        private UpdateResultSubscriber(
                DbStatementType dbStatementType,
                CompletableFuture<Long> queryFuture,
                CompletableFuture<Void> statementFuture
        ) {
            super(dbStatementType, queryFuture, statementFuture);
        }

        @Override
        public void onNext(UpdateResult r) {
            count().add(r.getModifiedCount());
        }

    }

    private static final class DeleteResultSubscriber extends DmlResultSubscriber<DeleteResult> {

        private DeleteResultSubscriber(
                DbStatementType dbStatementType,
                CompletableFuture<Long> queryFuture,
                CompletableFuture<Void> statementFuture
        ) {
            super(dbStatementType, queryFuture, statementFuture);
        }

        @Override
        public void onNext(DeleteResult r) {
            count().add(r.getDeletedCount());
        }

    }

    private static CompletionStage<Long> executeInsert(
            MongoDbStatement dbStatement,
            DbStatementType dbStatementType,
            MongoDbStatement.MongoStatement mongoStatement,
            CompletableFuture<Void> statementFuture,
            CompletableFuture<Long> queryFuture
    ) {
        MongoCollection<Document> mc = dbStatement.db().getCollection(mongoStatement.getCollection());
        mc.insertOne(mongoStatement.getValue())
                .subscribe(new InsertResultSubscriber(dbStatementType, queryFuture, statementFuture));
        return queryFuture;
    }

    @SuppressWarnings("SubscriberImplementation")
    private static CompletionStage<Long> executeUpdate(
            MongoDbStatement dbStatement,
            DbStatementType dbStatementType,
            MongoDbStatement.MongoStatement mongoStatement,
            CompletableFuture<Void> statementFuture,
            CompletableFuture<Long> queryFuture
    ) {
        MongoCollection<Document> mc = dbStatement.db().getCollection(mongoStatement.getCollection());
        Document query = mongoStatement.getQuery();
        // TODO should the next line be used?
        //Document value = mongoStatement.getValue();
        mc.updateMany((query != null) ? query : EMPTY, mongoStatement.getValue())
                .subscribe(new UpdateResultSubscriber(dbStatementType, queryFuture, statementFuture));
        return queryFuture;
    }

    @SuppressWarnings("SubscriberImplementation")
    private static CompletionStage<Long> executeDelete(
            MongoDbStatement dbStatement,
            DbStatementType dbStatementType,
            MongoDbStatement.MongoStatement mongoStatement,
            CompletableFuture<Void> statementFuture,
            CompletableFuture<Long> queryFuture
    ) {
        MongoCollection<Document> mc = dbStatement.db().getCollection(mongoStatement.getCollection());
        Document query = mongoStatement.getQuery();

        mc.deleteMany((query != null) ? query : EMPTY)
                .subscribe(new DeleteResultSubscriber(dbStatementType, queryFuture, statementFuture));
        return queryFuture;
    }





}
