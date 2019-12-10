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
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import io.helidon.common.reactive.Flow;
import io.helidon.dbclient.DbRow;

import org.bson.Document;
import org.reactivestreams.Subscription;

/**
 * Mongo specific query asynchronous processor.
 */
final class MongoDbQueryProcessor implements org.reactivestreams.Subscriber<Document>, Flow.Publisher<DbRow> {

    private static final Logger LOGGER = Logger.getLogger(MongoDbQueryProcessor.class.getName());

    private final AtomicLong count = new AtomicLong();
    private final CompletableFuture<Long> queryFuture;
    private final MongoDbStatement dbStatement;
    private final CompletableFuture<Void> statementFuture;
    private Flow.Subscriber<? super DbRow> subscriber;
    private Subscription subscription;

    MongoDbQueryProcessor(
            MongoDbStatement dbStatement,
            CompletableFuture<Void> statementFuture,
            CompletableFuture<Long> queryFuture
    ) {
        this.statementFuture = statementFuture;
        this.queryFuture = queryFuture;
        this.dbStatement = dbStatement;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
    }

    @Override
    public void onNext(Document doc) {
        MongoDbRow dbRow = new MongoDbRow(dbStatement.dbMapperManager(), dbStatement.mapperManager(), doc.size());
        doc.forEach((name, value) -> {
            LOGGER.fine(() -> String
                    .format("Column name = %s, value = %s", name, (value != null ? value.toString() : "N/A")));
            dbRow.add(name, new MongoDbColumn(dbStatement.dbMapperManager(), dbStatement.mapperManager(), name, value));
        });
        count.incrementAndGet();
        subscriber.onNext(dbRow);
    }

    @Override
    public void onError(Throwable t) {
        LOGGER.warning(() -> String.format("Query error: %s", t.getMessage()));
        statementFuture.completeExceptionally(t);
        queryFuture.completeExceptionally(t);
        if (dbStatement.txManager() != null) {
            dbStatement.txManager().stmtFailed(dbStatement);
        }
        subscriber.onError(t);
        LOGGER.fine(() -> String.format("Query %s execution failed", dbStatement.statementName()));
    }

    @Override
    public void onComplete() {
        LOGGER.fine(() -> "Query finished");
        statementFuture.complete(null);
        queryFuture.complete(count.get());
        if (dbStatement.txManager() != null) {
            dbStatement.txManager().stmtFinished(dbStatement);
        }
        subscriber.onComplete();
        LOGGER.fine(() -> String.format("Query %s execution succeeded", dbStatement.statementName()));
    }

    @Override
    public void subscribe(Flow.Subscriber<? super DbRow> subscriber) {
        this.subscriber = subscriber;
//        subscriber.onSubscribe(\);
        //TODO this MUST use flow control (e.g. only request what our subscriber requested)
        this.subscription.request(Long.MAX_VALUE);
    }

}
