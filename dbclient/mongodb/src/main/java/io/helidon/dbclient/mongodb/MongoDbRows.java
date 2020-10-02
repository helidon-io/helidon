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
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.reactive.Multi;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.common.DbClientContext;

import com.mongodb.reactivestreams.client.FindPublisher;
import org.bson.Document;

/**
 * Mongo specific execution result containing result set with multiple rows.
 *
 * @param <T> type of the result, starts as {@link io.helidon.dbclient.DbRow}
 */
public final class MongoDbRows<T> {

    private final AtomicBoolean resultRequested = new AtomicBoolean();
    private DbClientContext clientContext;
    private final FindPublisher<Document> documentFindPublisher;
    private final MongoDbStatement dbStatement;
    private final CompletableFuture<Long> queryFuture;
    private final GenericType<T> currentType;
    private final Function<?, T> resultMapper;
    private final MongoDbRows<?> parent;
    private final CompletableFuture<Void> statementFuture;

    MongoDbRows(DbClientContext clientContext,
                FindPublisher<Document> documentFindPublisher,
                MongoDbStatement dbStatement,
                Class<T> initialType,
                CompletableFuture<Void> statementFuture,
                CompletableFuture<Long> queryFuture) {

        this.clientContext = clientContext;
        this.documentFindPublisher = documentFindPublisher;
        this.dbStatement = dbStatement;
        this.statementFuture = statementFuture;
        this.queryFuture = queryFuture;
        this.currentType = GenericType.create(initialType);
        this.resultMapper = Function.identity();
        this.parent = null;
    }

    Flow.Publisher<T> publisher() {
        checkResult();

        return toPublisher();
    }

    @SuppressWarnings("unchecked")
    private Flow.Publisher<T> toPublisher() {
        // if parent is null, this is the DbRow type
        if (null == parent) {
            return (Flow.Publisher<T>) toDbPublisher();
        }

        Flow.Publisher<?> parentPublisher = parent.publisher();
        Function<Object, T> mappingFunction = (Function<Object, T>) resultMapper;
        // otherwise we must apply mapping
        return Multi.create(parentPublisher).map(mappingFunction::apply);
    }

    private Flow.Publisher<DbRow> toDbPublisher() {
        MongoDbQueryProcessor qp = new MongoDbQueryProcessor(clientContext,
                                                             dbStatement,
                                                             statementFuture,
                                                             queryFuture);
        documentFindPublisher.subscribe(qp);

        return qp;
    }

    private void checkResult() {
        if (resultRequested.get()) {
            throw new IllegalStateException("Result has already been requested");
        }
        resultRequested.set(true);
    }

}
