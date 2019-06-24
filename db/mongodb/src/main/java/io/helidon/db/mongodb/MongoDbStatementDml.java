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
package io.helidon.db.mongodb;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.mapper.MapperManager;
import io.helidon.db.DbInterceptorContext;
import io.helidon.db.DbMapperManager;
import io.helidon.db.StatementType;
import io.helidon.db.common.InterceptorSupport;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.mongodb.reactivestreams.client.Success;
import org.bson.Document;
import org.reactivestreams.Subscription;

/**
 * DML statement for mongoDB.
 */
public class MongoDbStatementDml extends MongoDbStatement<MongoDbStatementDml, CompletionStage<Long>> {

    private static final Logger LOGGER = Logger.getLogger(MongoDbStatementDml.class.getName());
    private StatementType statementType;
    private MongoStatement statement;

    MongoDbStatementDml(StatementType statementType, MongoDatabase db,
                        String statementName,
                        String statement,
                        DbMapperManager dbMapperManager,
                        MapperManager mapperManager,
                        InterceptorSupport interceptors) {
        super(statementType,
              db,
              statementName,
              statement,
              dbMapperManager,
              mapperManager,
              interceptors);
        this.statementType = statementType;
    }

    @Override
    public CompletionStage<Long> execute() {
        statement = new MongoStatement(statementType, READER_FACTORY, build());
        switch (statement.getOperation()) {
        case INSERT:
            statementType = StatementType.INSERT;
            break;
        case UPDATE:
            statementType = StatementType.UPDATE;
            break;
        case DELETE:
            statementType = StatementType.DELETE;
            break;
        default:
            throw new IllegalStateException("Unexpected value for DML statement: " + statement.getOperation());
        }
        return super.execute();
    }

    @Override
    protected CompletionStage<Long> doExecute(DbInterceptorContext dbContext,
                                              CompletableFuture<Void> statementFuture,
                                              CompletableFuture<Long> queryFuture) {

        if (!dbContext.statement().equals(statement())) {
            // TODO rebuild the statement from the context
        }
        switch (statement.getOperation()) {
        case INSERT:
            return executeInsert(statement, statementFuture, queryFuture);
        case UPDATE:
            return executeUpdate(statement, statementFuture, queryFuture);
        case DELETE:
            return executeDelete(statement, statementFuture, queryFuture);
        default:
            throw new UnsupportedOperationException("Statement operation not yet supported: " + statement.getOperation());

        }
    }

    @Override
    protected StatementType statementType() {
        return statementType;
    }

    @SuppressWarnings("SubscriberImplementation")
    private CompletionStage<Long> executeDelete(MongoStatement stmt,
                                                CompletableFuture<Void> statementFuture,
                                                CompletableFuture<Long> queryFuture) {

        MongoCollection<Document> mc = db().getCollection(stmt.getCollection());
        Document query = stmt.getQuery();

        mc.deleteMany((query != null) ? query : EMPTY)
                .subscribe(new org.reactivestreams.Subscriber<DeleteResult>() {
                    private final LongAdder count = new LongAdder();

                    @Override
                    public void onSubscribe(Subscription s) {
                        // no need for flow control, we only add the result
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(DeleteResult r) {
                        count.add(r.getDeletedCount());
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

                        LOGGER.log(Level.INFO, "DELETE succeeded");
                    }
                });
        return queryFuture;
    }

    @SuppressWarnings("SubscriberImplementation")
    private CompletionStage<Long> executeUpdate(MongoStatement stmt,
                                                CompletableFuture<Void> statementFuture,
                                                CompletableFuture<Long> queryFuture) {

        MongoCollection<Document> mc = db().getCollection(stmt.getCollection());
        Document query = stmt.getQuery();
        Document value = stmt.getValue();
        mc.updateMany((query != null) ? query : EMPTY, stmt.getValue())
                .subscribe(new org.reactivestreams.Subscriber<UpdateResult>() {
                    private final LongAdder count = new LongAdder();

                    @Override
                    public void onSubscribe(Subscription s) {
                        // no need for flow control, we only add the result
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(UpdateResult r) {
                        count.add(r.getModifiedCount());
                    }

                    @Override
                    public void onError(Throwable t) {
                        statementFuture.completeExceptionally(t);
                        queryFuture.completeExceptionally(t);
                    }

                    @Override
                    public void onComplete() {
                        LOGGER.finest(() -> "Update completed");
                        statementFuture.complete(null);
                        queryFuture.complete(count.sum());
                    }
                });
        return queryFuture;
    }

    private CompletionStage<Long> executeInsert(MongoStatement stmt,
                                                CompletableFuture<Void> statementFuture,
                                                CompletableFuture<Long> queryFuture) {

        MongoCollection<Document> mc = db().getCollection(stmt.getCollection());
        mc.insertOne(stmt.getValue())
                .subscribe(new org.reactivestreams.Subscriber<Success>() {
                    private final LongAdder count = new LongAdder();

                    @Override
                    public void onSubscribe(Subscription s) {
                        // no need for flow control
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(Success t) {
                        count.increment();
                    }

                    @Override
                    public void onError(Throwable t) {
                        statementFuture.completeExceptionally(t);
                        queryFuture.completeExceptionally(t);
                    }

                    @Override
                    public void onComplete() {
                        LOGGER.log(Level.INFO, "INSERT succeeded");
                        statementFuture.complete(null);
                        queryFuture.complete(count.sum());
                    }
                });
        return queryFuture;
    }

}
