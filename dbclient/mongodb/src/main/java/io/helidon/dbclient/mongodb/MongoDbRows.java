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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperException;
import io.helidon.common.reactive.Multi;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRows;

import com.mongodb.reactivestreams.client.FindPublisher;
import org.bson.Document;

/**
 * Mongo specific execution result containing result set with multiple rows.
 *
 * @param <T> type of the result, starts as {@link io.helidon.dbclient.DbRow}
 */
public final class MongoDbRows<T> implements DbRows<T> {

    private final AtomicBoolean resultRequested = new AtomicBoolean();
    private final FindPublisher<Document> documentFindPublisher;
    private final MongoDbStatement dbStatement;
    private final CompletableFuture<Long> queryFuture;
    private final GenericType<T> currentType;
    private final Function<?, T> resultMapper;
    private final MongoDbRows<?> parent;
    private final CompletableFuture<Void> statementFuture;

        MongoDbRows(FindPublisher<Document> documentFindPublisher,
                            MongoDbStatement dbStatement,
                            Class<T> initialType,
                            CompletableFuture<Void> statementFuture,
                            CompletableFuture<Long> queryFuture) {
            this.documentFindPublisher = documentFindPublisher;
            this.dbStatement = dbStatement;
            this.statementFuture = statementFuture;
            this.queryFuture = queryFuture;
            this.currentType = GenericType.create(initialType);
            this.resultMapper = Function.identity();
            this.parent = null;
        }

        private MongoDbRows(FindPublisher<Document> documentFindPublisher,
                            MongoDbStatement dbStatement,
                            CompletableFuture<Void> statementFuture,
                            CompletableFuture<Long> queryFuture,
                            GenericType<T> nextType,
                            Function<?, T> resultMapper,
                            MongoDbRows<?> parent) {
            this.documentFindPublisher = documentFindPublisher;
            this.dbStatement = dbStatement;
            this.statementFuture = statementFuture;
            this.queryFuture = queryFuture;
            this.resultMapper = resultMapper;
            this.currentType = nextType;
            this.parent = parent;
        }

        @Override
        public <U> DbRows<U> map(Function<T, U> mapper) {
            return new MongoDbRows<>(
                    documentFindPublisher,
                    dbStatement,
                    statementFuture,
                    queryFuture,
                    null,
                    mapper,
                    this);
        }

        @Override
        public <U> DbRows<U> map(Class<U> type) {
            return map(GenericType.create(type));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> DbRows<U> map(GenericType<U> type) {
            GenericType<T> localCurrentType = this.currentType;

            Function<T, U> theMapper;

            if (null == localCurrentType) {
                theMapper = value -> dbStatement.mapperManager().map(value,
                                                       GenericType.<T>create(value.getClass()),
                                                       type);
            } else if (localCurrentType.equals(DbMapperManager.TYPE_DB_ROW)) {
                // maybe we want the same type
                if (type.equals(DbMapperManager.TYPE_DB_ROW)) {
                    return (DbRows<U>) this;
                }
                // try to find mapper in db mapper manager
                theMapper = value -> {
                    //first try db mapper
                    try {
                        return dbStatement.dbMapperManager().read((DbRow) value, type);
                    } catch (MapperException originalException) {
                        // not found in db mappers, use generic mappers
                        try {
                            return dbStatement.mapperManager().map((DbRow) value,
                                                     DbMapperManager.TYPE_DB_ROW,
                                                     type);
                        } catch (MapperException ignored) {
                            throw originalException;
                        }
                    }
                };
            } else {
                // one type to another
                theMapper = value -> dbStatement.mapperManager().map(value,
                                                       localCurrentType,
                                                       type);
            }
            return new MongoDbRows<>(
                    documentFindPublisher,
                    dbStatement,
                    statementFuture,
                    queryFuture,
                    type,
                    theMapper,
                    this);
        }

        @Override
        public Flow.Publisher<T> publisher() {
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
            return Multi.from(parentPublisher).map(mappingFunction::apply);
        }

        @Override
        public CompletionStage<List<T>> collect() {
            checkResult();

            return Multi.from(toPublisher())
                    .collectList()
                    .toStage();
        }

        private Flow.Publisher<DbRow> toDbPublisher() {
            MongoDbQueryProcessor qp = new MongoDbQueryProcessor(
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
