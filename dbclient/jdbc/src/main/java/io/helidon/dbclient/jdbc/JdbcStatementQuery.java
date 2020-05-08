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

package io.helidon.dbclient.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperException;
import io.helidon.common.mapper.MapperManager;
import io.helidon.common.reactive.Multi;
import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbColumn;
import io.helidon.dbclient.DbInterceptorContext;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRows;
import io.helidon.dbclient.DbStatementQuery;

/**
 * Implementation of query.
 */
class JdbcStatementQuery extends JdbcStatement<DbStatementQuery, DbRows<DbRow>> implements DbStatementQuery {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(JdbcStatementQuery.class.getName());

    JdbcStatementQuery(JdbcExecuteContext executeContext,
                       JdbcStatementContext statementContext) {
        super(executeContext, statementContext);
    }

    @Override
    protected CompletionStage<DbRows<DbRow>> doExecute(CompletionStage<DbInterceptorContext> dbContextFuture,
                                                       CompletableFuture<Void> statementFuture,
                                                       CompletableFuture<Long> queryFuture) {

        executeContext().addFuture(queryFuture);

        CompletionStage<DbRows<DbRow>> result = dbContextFuture.thenCompose(interceptorContext -> {
            return connection().thenApply(conn -> {
                PreparedStatement statement = super.build(conn, interceptorContext);
                try {
                    ResultSet rs = statement.executeQuery();
                    // at this moment we have a DbRows
                    statementFuture.complete(null);
                    return processResultSet(executorService(),
                                            dbMapperManager(),
                                            mapperManager(),
                                            queryFuture,
                                            rs);
                } catch (SQLException e) {
                    LOGGER.log(Level.FINEST,
                            String.format("Failed to execute query %s: %s", statement.toString(), e.getMessage()),
                            e);
                    throw new DbClientException("Failed to execute query", e);
                }
            });
        });

        result.exceptionally(throwable -> {
            statementFuture.completeExceptionally(throwable);
            return null;
        });

        return result;
    }

    static DbRows<DbRow> processResultSet(
            ExecutorService executorService,
            DbMapperManager dbMapperManager,
            MapperManager mapperManager,
            CompletableFuture<Long> queryFuture,
            ResultSet resultSet) {

        return new JdbcDbRows<>(
                                resultSet,
                                executorService,
                                dbMapperManager,
                                mapperManager,
                                queryFuture,
                                DbRow.class);
    }

    static Map<Long, DbColumn> createMetadata(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        Map<Long, DbColumn> byNumbers = new HashMap<>();

        for (int i = 1; i <= columnCount; i++) {
            String name = metaData.getColumnLabel(i);
            String sqlType = metaData.getColumnTypeName(i);
            Class<?> javaClass = classByName(metaData.getColumnClassName(i));
            DbColumn column = new DbColumn() {
                @Override
                public <T> T as(Class<T> type) {
                    return null;
                }

                @Override
                public <T> T as(GenericType<T> type) {
                    return null;
                }

                @Override
                public Class<?> javaType() {
                    return javaClass;
                }

                @Override
                public String dbType() {
                    return sqlType;
                }

                @Override
                public String name() {
                    return name;
                }
            };
            byNumbers.put((long) i, column);
        }
        return byNumbers;
    }

    private static Class<?> classByName(String columnClassName) {
        if (columnClassName == null) {
            return null;
        }
        try {
            return Class.forName(columnClassName);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    String name() {
        return statementName();
    }

    private static final class JdbcDbRows<T> implements DbRows<T> {
        private final AtomicBoolean resultRequested = new AtomicBoolean();
        private final ExecutorService executorService;
        private final DbMapperManager dbMapperManager;
        private final MapperManager mapperManager;
        private final CompletableFuture<Long> queryFuture;
        private final ResultSet resultSet;
        private final JdbcDbRows<?> parent;
        private final GenericType<T> currentType;
        private final Function<?, T> resultMapper;

        private JdbcDbRows(ResultSet resultSet,
                           ExecutorService executorService,
                           DbMapperManager dbMapperManager,
                           MapperManager mapperManager,
                           CompletableFuture<Long> queryFuture,
                           Class<T> initialType) {

            this(resultSet,
                 executorService,
                 dbMapperManager,
                 mapperManager,
                 queryFuture,
                 GenericType.create(initialType),
                 Function.identity(),
                 null);
        }

        private JdbcDbRows(ResultSet resultSet,
                           ExecutorService executorService,
                           DbMapperManager dbMapperManager,
                           MapperManager mapperManager,
                           CompletableFuture<Long> queryFuture,
                           GenericType<T> nextType,
                           Function<?, T> resultMapper,
                           JdbcDbRows<?> parent) {

            this.executorService = executorService;
            this.dbMapperManager = dbMapperManager;
            this.mapperManager = mapperManager;
            this.queryFuture = queryFuture;
            this.resultSet = resultSet;
            this.currentType = nextType;
            this.resultMapper = resultMapper;
            this.parent = parent;
        }

        @Override
        public <U> DbRows<U> map(Function<T, U> mapper) {
            return new JdbcDbRows<>(resultSet,
                                    executorService,
                                    dbMapperManager,
                                    mapperManager,
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
            GenericType<T> currentType = this.currentType;

            Function<T, U> theMapper;

            if (null == currentType) {
                theMapper = value -> mapperManager.map(value,
                                                       GenericType.<T>create(value.getClass()),
                                                       type);
            } else if (currentType.equals(DbMapperManager.TYPE_DB_ROW)) {
                // maybe we want the same type
                if (type.equals(DbMapperManager.TYPE_DB_ROW)) {
                    return (DbRows<U>) this;
                }
                // try to find mapper in db mapper manager
                theMapper = value -> {
                    //first try db mapper
                    try {
                        return dbMapperManager.read((DbRow) value, type);
                    } catch (MapperException originalException) {
                        // not found in db mappers, use generic mappers
                        try {
                            return mapperManager.map((DbRow) value,
                                                     DbMapperManager.TYPE_DB_ROW,
                                                     type);
                        } catch (MapperException ignored) {
                            throw originalException;
                        }
                    }
                };
            } else {
                // one type to another
                theMapper = value -> mapperManager.map(value,
                                                       currentType,
                                                       type);
            }
            return new JdbcDbRows<>(resultSet,
                                    executorService,
                                    dbMapperManager,
                                    mapperManager,
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

        @Override
        public CompletionStage<List<T>> collect() {
            checkResult();
            return toFuture();
        }

        @SuppressWarnings("unchecked")
        private Flow.Publisher<T> toPublisher() {
            if (null == parent) {
                // this is DbRow type
                return (Flow.Publisher<T>) new RowPublisher(executorService,
                                                            resultSet,
                                                            queryFuture,
                                                            dbMapperManager,
                                                            mapperManager);
            }

            Function<Object, T> mappingFunction = (Function<Object, T>) resultMapper;
            Multi<Object> parentMulti = (Multi<Object>) parent.multi();

            return parentMulti.map(mappingFunction::apply);
        }

        private Multi<T> multi() {
            return Multi.from(publisher());
        }

        private CompletionStage<List<T>> toFuture() {

            return Multi.from(toPublisher())
                    .collectList()
                    .toStage();
        }

        private void checkResult() {
            if (resultRequested.get()) {
                throw new IllegalStateException("Result has already been requested");
            }
            resultRequested.set(true);
        }
    }


    private static final class RowPublisher implements Flow.Publisher<DbRow> {
        private final ExecutorService executorService;
        private final ResultSet rs;
        private final CompletableFuture<Long> queryFuture;
        private final DbMapperManager dbMapperManager;
        private final MapperManager mapperManager;

        private RowPublisher(ExecutorService executorService,
                             ResultSet rs,
                             CompletableFuture<Long> queryFuture,
                             DbMapperManager dbMapperManager,
                             MapperManager mapperManager) {

            this.executorService = executorService;
            this.rs = rs;
            this.queryFuture = queryFuture;
            this.dbMapperManager = dbMapperManager;
            this.mapperManager = mapperManager;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super DbRow> subscriber) {
            LinkedBlockingQueue<Long> requestQueue = new LinkedBlockingQueue<>();
            AtomicBoolean cancelled = new AtomicBoolean();

            // we have executed the statement, we can correctly subscribe
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    // add the requested number to the queue
                    requestQueue.add(n);
                }

                @Override
                public void cancel() {
                    cancelled.set(true);
                    requestQueue.clear();
                }
            });

            // TODO
            // we should only use a thread to read data that was actually requested
            // I would prefer to use the same thread to process a single query (to make sure we honor thread locals
            // that may be used by the database)

            // and now we can process the data from the database
            executorService.submit(() -> {
                //now we have a subscriber, we can handle the processing of result set
                try (ResultSet rs = this.rs) {
                    Map<Long, DbColumn> metadata = createMetadata(rs);
                    long count = 0;

                    // now we only want to process next record if it was requested
                    while (!cancelled.get()) {
                        Long nextElement;
                        try {
                            nextElement = requestQueue.poll(10, TimeUnit.MINUTES);
                        } catch (InterruptedException e) {
                            LOGGER.finest("Interrupted while polling for requests, terminating DB read");
                            subscriber.onError(e);
                            break;
                        }
                        if (nextElement == null) {
                            LOGGER.finest("No data requested for 10 minutes, terminating DB read");
                            subscriber.onError(new TimeoutException("No data requested in 10 minutes"));
                            break;
                        }
                        for (long i = 0; i < nextElement; i++) {
                            if (rs.next()) {
                                DbRow dbRow = createDbRow(rs, metadata, dbMapperManager, mapperManager);
                                subscriber.onNext(dbRow);
                                count++;
                            } else {
                                queryFuture.complete(count);
                                subscriber.onComplete();
                                return;
                            }
                        }
                    }

                    if (cancelled.get()) {
                        queryFuture
                                .completeExceptionally(new CancellationException("Processing cancelled by subscriber"));
                    }
                } catch (SQLException e) {
                    queryFuture.completeExceptionally(e);
                    subscriber.onError(e);
                }
            });
        }

        private DbRow createDbRow(ResultSet rs,
                                  Map<Long, DbColumn> metadata,
                                  DbMapperManager dbMapperManager,
                                  MapperManager mapperManager) throws SQLException {
            // read whole row
            // for each column
            Map<String, DbColumn> byStringsWithValues = new HashMap<>();
            Map<Integer, DbColumn> byNumbersWithValues = new HashMap<>();

            for (int i = 1; i <= metadata.size(); i++) {
                DbColumn meta = metadata.get((long) i);
                Object value = rs.getObject(i);
                DbColumn withValue = new DbColumn() {
                    @Override
                    public <T> T as(Class<T> type) {
                        if (null == value) {
                            return null;
                        }
                        if (type.isAssignableFrom(value.getClass())) {
                            return type.cast(value);
                        }
                        return map(value, type);
                    }

                    @SuppressWarnings("unchecked")
                    <SRC, T> T map(SRC value, Class<T> type) {
                        Class<SRC> theClass = (Class<SRC>) value.getClass();

                        try {
                            return mapperManager.map(value, theClass, type);
                        } catch (MapperException e) {
                            if (type.equals(String.class)) {
                                return (T) String.valueOf(value);
                            }
                            throw e;
                        }
                    }

                    @SuppressWarnings("unchecked")
                    <SRC, T> T map(SRC value, GenericType<T> type) {
                        Class<SRC> theClass = (Class<SRC>) value.getClass();
                        return mapperManager.map(value, GenericType.create(theClass), type);
                    }

                    @Override
                    public <T> T as(GenericType<T> type) {
                        if (null == value) {
                            return null;
                        }
                        if (type.isClass()) {
                            Class<?> theClass = type.rawType();
                            if (theClass.isAssignableFrom(value.getClass())) {
                                return type.cast(value);
                            }
                        }
                        return map(value, type);
                    }

                    @Override
                    public Class<?> javaType() {
                        if (null == meta.javaType()) {
                            if (null == value) {
                                return null;
                            }
                            return value.getClass();
                        } else {
                            return meta.javaType();
                        }
                    }

                    @Override
                    public String dbType() {
                        return meta.dbType();
                    }

                    @Override
                    public String name() {
                        return meta.name();
                    }
                };
                byStringsWithValues.put(meta.name(), withValue);
                byNumbersWithValues.put(i, withValue);
            }

            return new DbRow() {
                @Override
                public DbColumn column(String name) {
                    return byStringsWithValues.get(name);
                }

                @Override
                public DbColumn column(int index) {
                    return byNumbersWithValues.get(index);
                }

                @Override
                public void forEach(Consumer<? super DbColumn> columnAction) {
                    byStringsWithValues.values()
                            .forEach(columnAction);
                }

                @Override
                public <T> T as(Class<T> type) {
                    return dbMapperManager.read(this, type);
                }

                @Override
                public <T> T as(GenericType<T> type) {
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
                    for (DbColumn col : byStringsWithValues.values()) {
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
            };
        }
    }

    static final class ResultWithConn {
        private final ResultSet resultSet;
        private final Connection connection;

        ResultWithConn(ResultSet resultSet, Connection connection) {
            this.resultSet = resultSet;
            this.connection = connection;
        }

        public ResultSet resultSet() {
            return resultSet;
        }

        public Connection connection() {
            return connection;
        }
    }

}
