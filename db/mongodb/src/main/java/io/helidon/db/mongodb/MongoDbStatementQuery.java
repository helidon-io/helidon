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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperException;
import io.helidon.common.mapper.MapperManager;
import io.helidon.common.reactive.Flow;
import io.helidon.db.DbColumn;
import io.helidon.db.DbInterceptorContext;
import io.helidon.db.DbMapperManager;
import io.helidon.db.DbRow;
import io.helidon.db.DbRowResult;
import io.helidon.db.StatementType;
import io.helidon.db.common.InterceptorSupport;

import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.bson.Document;
import org.reactivestreams.Subscription;

/**
 * Implementation of a query.
 */
class MongoDbStatementQuery extends MongoDbStatement<MongoDbStatementQuery, DbRowResult<DbRow>> {
    private static final Logger LOGGER = Logger.getLogger(MongoDbStatementQuery.class.getName());

    MongoDbStatementQuery(StatementType statementType,
                          MongoDatabase db,
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
    }

    @Override
    protected DbRowResult<DbRow> doExecute(DbInterceptorContext dbContext,
                                           CompletableFuture<Void> statementFuture,
                                           CompletableFuture<Long> queryFuture) {

        MongoStatement stmt = new MongoStatement(StatementType.QUERY, READER_FACTORY, build());
        if (stmt.getOperation() == MongoOperation.QUERY) {
            return executeQuery(stmt, statementFuture, queryFuture);
        }
        throw new UnsupportedOperationException(String.format("Operation %s is not supported",
                                                              stmt.getOperation().toString()));
    }

    public DbRowResult<DbRow> executeQuery(MongoStatement stmt,
                                           CompletableFuture<Void> statementFuture,
                                           CompletableFuture<Long> queryFuture) {

        MongoCollection<Document> mc = db().getCollection(stmt.getCollection());
        Document query = stmt.getQuery();
        FindPublisher<Document> thePublisher = mc.find((query != null) ? query : EMPTY);
        return new MongoDbRowResult<>(thePublisher,
                                      dbMapperManager(),
                                      mapperManager(),
                                      DbRow.class,
                                      statementFuture,
                                      queryFuture);
    }




    private static class Row implements DbRow {

        private final Map<String, DbColumn> columnsByName = new HashMap<>();
        private final List<DbColumn> columnsList = new ArrayList<>();

        private final DbMapperManager dbMapperManager;
        private final MapperManager mapperManager;

        Row(DbMapperManager dbMapperManager, MapperManager mapperManager) {
            this.dbMapperManager = dbMapperManager;
            this.mapperManager = mapperManager;
        }

        private void add(String name, DbColumn column) {
            columnsByName.put(name, column);
            columnsList.add(column);
        }

        @Override
        public DbColumn column(String name) {
            return columnsByName.get(name);
        }

        @Override
        public DbColumn column(int index) {
            return columnsList.get(index);
        }

        @Override
        public void forEach(Consumer<? super DbColumn> columnAction) {
            columnsByName.values().forEach(columnAction);
        }

        @Override
        public <T> T as(Class<T> type) {
            return dbMapperManager.read(this, type);
        }

        @Override
        public <T> T as(GenericType<T> type) throws MapperException {
            return dbMapperManager.read(this, type);
        }

        @Override
        public <T> T as(Function<DbRow, T> mapper) {
            return mapper.apply(this);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            sb.append('{');
            for (DbColumn col : columnsByName.values()) {
                if (first) {
                    first = false;
                } else {
                    sb.append(',');
                }
                sb.append(col.name());
                sb.append(':');
                sb.append(col.value().toString());
            }
            sb.append('}');
            return sb.toString();
        }
    }

    public static class Column implements DbColumn {
        private final MapperManager mapperManager;
        private final String name;
        private final Object value;

        Column(DbMapperManager dbMapperManager, MapperManager mapperManager, String name, Object value) {
            this.mapperManager = mapperManager;
            this.name = name;
            this.value = value;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T as(Class<T> type) throws MapperException {
            if (type.equals(javaType())) {
                return (T) value;
            }

            return map(value, type);
        }

        @Override
        public <T> T as(GenericType<T> type) throws MapperException {
            return map(value, type);
        }

        @SuppressWarnings("unchecked")
        private <S, T> T map(S value, Class<T> targetType) {
            Class<S> sourceType = (Class<S>) javaType();

            return mapperManager.map(value, sourceType, targetType);
        }

        @SuppressWarnings("unchecked")
        private <S, T> T map(S value, GenericType<T> targetType) {
            Class<S> sourceClass = (Class<S>) javaType();
            GenericType<S> sourceType = GenericType.create(sourceClass);

            return mapperManager.map(value, sourceType, targetType);
        }

        @Override
        public Class<?> javaType() {
            return (null == value) ? String.class : value.getClass();
        }

        @Override
        public String dbType() {
            throw new UnsupportedOperationException("sqlType() is not supported yet.");
        }

        @Override
        public String name() {
            return name;
        }

    }

    private static final class QueryProcessor
            implements org.reactivestreams.Subscriber<Document>, io.helidon.common.reactive.Flow.Publisher<DbRow> {

        private final AtomicLong count = new AtomicLong();
        private final CompletableFuture<Long> queryFuture;
        private final DbMapperManager dbMapperManager;
        private final MapperManager mapperManager;
        private final CompletableFuture<Void> statementFuture;
        private io.helidon.common.reactive.Flow.Subscriber<? super DbRow> subscriber;
        private Subscription subscription;

        private QueryProcessor(DbMapperManager dbMapperManager,
                               MapperManager mapperManager,
                               CompletableFuture<Void> statementFuture,
                               CompletableFuture<Long> queryFuture) {

            this.statementFuture = statementFuture;
            this.queryFuture = queryFuture;
            this.dbMapperManager = dbMapperManager;
            this.mapperManager = mapperManager;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onNext(Document doc) {
            Row dbRow = new Row(dbMapperManager, mapperManager);
            doc.forEach((name, value) -> {
                LOGGER.finest(() -> String
                        .format("Column name = %s, value = %s", name, (value != null) ? value.toString() : "NULL"));
                dbRow.add(name, new Column(dbMapperManager, mapperManager, name, value));
            });
            count.incrementAndGet();
            subscriber.onNext(dbRow);
        }

        @Override
        public void onError(Throwable t) {
            LOGGER.warning(String.format("Query error: %s", t.getMessage()));
            statementFuture.completeExceptionally(t);
            queryFuture.completeExceptionally(t);
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            LOGGER.fine(() -> "Query finished");
            statementFuture.complete(null);
            queryFuture.complete(count.get());
            subscriber.onComplete();
        }

        @Override
        public void subscribe(io.helidon.common.reactive.Flow.Subscriber<? super DbRow> subscriber) {
            this.subscriber = subscriber;
            //TODO this MUST use flow control (e.g. only request what our subscriber requested)
            this.subscription.request(Long.MAX_VALUE);
        }
    }

    private static final class MongoDbRowResult<T> implements DbRowResult<T> {
        private final AtomicBoolean resultRequested = new AtomicBoolean();
        private final FindPublisher<Document> documentFindPublisher;
        private final DbMapperManager dbMapperManager;
        private final MapperManager mapperManager;
        private final CompletableFuture<Long> queryFuture;
        private final GenericType<T> currentType;
        private final Function<?, T> resultMapper;
        private final MongoDbRowResult<?> parent;
        private final CompletableFuture<Void> statementFuture;

        private MongoDbRowResult(FindPublisher<Document> documentFindPublisher,
                                 DbMapperManager dbMapperManager,
                                 MapperManager mapperManager,
                                 Class<T> initialType,
                                 CompletableFuture<Void> statementFuture,
                                 CompletableFuture<Long> queryFuture) {

            this.documentFindPublisher = documentFindPublisher;
            this.dbMapperManager = dbMapperManager;
            this.mapperManager = mapperManager;
            this.statementFuture = statementFuture;
            this.queryFuture = queryFuture;
            this.currentType = GenericType.create(initialType);
            this.resultMapper = null;
            this.parent = null;
        }

        private MongoDbRowResult(FindPublisher<Document> documentFindPublisher,
                                 DbMapperManager dbMapperManager,
                                 MapperManager mapperManager,
                                 CompletableFuture<Void> statementFuture,
                                 CompletableFuture<Long> queryFuture,
                                 GenericType<T> nextType,
                                 Function<?, T> resultMapper,
                                 MongoDbRowResult<?> parent) {
            this.documentFindPublisher = documentFindPublisher;
            this.dbMapperManager = dbMapperManager;
            this.mapperManager = mapperManager;
            this.statementFuture = statementFuture;
            this.queryFuture = queryFuture;
            this.resultMapper = resultMapper;
            this.currentType = nextType;
            this.parent = parent;
        }

        @Override
        public <U> DbRowResult<U> map(Function<T, U> mapper) {
            return new MongoDbRowResult<>(documentFindPublisher,
                                          dbMapperManager,
                                          mapperManager,
                                          statementFuture,
                                          queryFuture,
                                          null,
                                          mapper,
                                          this);
        }

        @Override
        public <U> DbRowResult<U> map(Class<U> type) {
            return map(GenericType.create(type));
        }

        @Override
        public <U> DbRowResult<U> map(GenericType<U> type) {
            GenericType<T> currentType = this.currentType;

            Function<T, U> theMapper;

            if (null == currentType) {
                theMapper = value -> mapperManager.map(value,
                                                       GenericType.create(value.getClass()),
                                                       type);
            } else if (currentType.equals(DbMapperManager.TYPE_DB_ROW)) {
                // try to find mapper in db mapper manager
                theMapper = value -> {
                    //first try db mapper
                    try {
                        return dbMapperManager.read((DbRow) value, type);
                    } catch (MapperException e) {
                        // not found in db mappers, use generic mappers
                        return mapperManager.map(value,
                                                 DbMapperManager.TYPE_DB_ROW,
                                                 type);
                    }
                };
            } else {
                // one type to another
                theMapper = value -> mapperManager.map(value,
                                                       currentType,
                                                       type);
            }
            return new MongoDbRowResult<>(documentFindPublisher,
                                          dbMapperManager,
                                          mapperManager,
                                          statementFuture,
                                          queryFuture,
                                          type,
                                          theMapper,
                                          this);
        }

        @Override
        public io.helidon.common.reactive.Flow.Publisher<T> publisher() {
            checkResult();

            return toPublisher();
        }

        @SuppressWarnings("unchecked")
        private io.helidon.common.reactive.Flow.Publisher<T> toPublisher() {
            // if parent is null, this is the DbRow type
            if (null == parent) {
                return (Flow.Publisher<T>) toDbPublisher();
            }

            Flow.Publisher<?> parentPublisher = parent.publisher();
            Function<Object, T> mappingFunction = (Function<Object, T>) resultMapper;
            // otherwise we must apply mapping
            return new MappingPublisher<>(parentPublisher, mappingFunction);
        }

        @Override
        public CompletionStage<List<T>> collect() {
            checkResult();

            CompletableFuture<List<T>> result = new CompletableFuture<>();

            // this is a simple subscriber - I want all the records
            // and as fast as possible
            toPublisher().subscribe(new Flow.Subscriber<T>() {
                private final List<T> allRows = new LinkedList<>();

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(T rowType) {
                    allRows.add(rowType);
                }

                @Override
                public void onError(Throwable throwable) {
                    result.completeExceptionally(throwable);
                }

                @Override
                public void onComplete() {
                    result.complete(allRows);
                }
            });
            return result;
        }

        private io.helidon.common.reactive.Flow.Publisher<DbRow> toDbPublisher() {
            QueryProcessor qp = new QueryProcessor(dbMapperManager,
                                                   mapperManager,
                                                   statementFuture,
                                                   queryFuture);
            documentFindPublisher.subscribe(qp);
            return qp;
        }

        @Override
        public CompletionStage<Void> consume(Consumer<DbRowResult<T>> consumer) {
            consumer.accept(this);
            return queryFuture.thenRun(() -> {
            });
        }

        private void checkResult() {
            if (resultRequested.get()) {
                throw new IllegalStateException("Result has already been requested");
            }
            resultRequested.set(true);
        }

    }

    private static final class MappingPublisher<T> implements Flow.Publisher<T> {
        private final Flow.Publisher<?> publisher;
        private final Function<Object, T> resultMapper;
        private Flow.Subscription subscription;

        private MappingPublisher(Flow.Publisher<?> publisher,
                                 Function<Object, T> resultMapper) {

            this.publisher = publisher;
            this.resultMapper = resultMapper;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super T> subscriber) {
            publisher.subscribe(new Flow.Subscriber<Object>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    MappingPublisher.this.subscription = subscription;
                }

                @Override
                public void onNext(Object item) {
                    subscriber.onNext(resultMapper.apply(item));
                }

                @Override
                public void onError(Throwable throwable) {
                    subscriber.onError(throwable);
                }

                @Override
                public void onComplete() {
                    subscriber.onComplete();
                }
            });
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    subscription.request(n);
                }

                @Override
                public void cancel() {
                    subscription.cancel();
                }
            });
        }
    }
}
