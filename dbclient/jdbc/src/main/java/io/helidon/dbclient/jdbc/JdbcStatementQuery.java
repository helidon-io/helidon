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
package io.helidon.dbclient.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperManager;
import io.helidon.common.reactive.Flow;
import io.helidon.dbclient.DbColumn;
import io.helidon.dbclient.DbInterceptorContext;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRowResult;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.common.InterceptorSupport;

/**
 * Implementation of query.
 */
class JdbcStatementQuery extends JdbcStatement<JdbcStatementQuery, DbRowResult<DbRow>> {
    private static final Logger LOGGER = Logger.getLogger(JdbcStatementQuery.class.getName());

    JdbcStatementQuery(DbStatementType dbStatementType,
                       ConnectionPool connectionPool,
                       ExecutorService executorService,
                       String statementName,
                       String statement,
                       DbMapperManager dbMapperMananger,
                       MapperManager mapperManager,
                       InterceptorSupport interceptors) {
        super(dbStatementType,
              connectionPool,
              statementName,
              statement,
              dbMapperMananger,
              mapperManager,
              executorService,
              interceptors);
    }

    @Override
    protected DbRowResult<DbRow> doExecute(CompletionStage<DbInterceptorContext> dbContextFuture,
                                           CompletableFuture<Void> statementFuture,
                                           CompletableFuture<Long> queryFuture) {
        CompletableFuture<ResultWithConn> resultSetFuture = new CompletableFuture<>();
        resultSetFuture.thenAccept(rs -> statementFuture.complete(null))
                .exceptionally(throwable -> {
                    statementFuture.completeExceptionally(throwable);
                    return null;
                });

        dbContextFuture.thenAccept(dbContext -> {
            executorService().submit(() -> {
                Connection conn = null;
                try {
                    conn = connection();
                    PreparedStatement statement = super.build(conn, dbContext);
                    ResultSet rs = statement.executeQuery();
                    // at this moment we have a DbRowResult
                    resultSetFuture.complete(new ResultWithConn(rs, conn));
                } catch (Exception e) {
                    try {
                        if (null != conn) {
                            conn.close();
                        }
                    } catch (SQLException ex) {
                        LOGGER.log(Level.WARNING, "Failed to close a connection", ex);
                    }
                    resultSetFuture.completeExceptionally(e);
                }
            });
        }).exceptionally(throwable -> {
            resultSetFuture.completeExceptionally(throwable);
            return null;
        });

        return processResultSet(queryFuture,
                                resultSetFuture,
                                executorService(),
                                dbMapperManager(),
                                mapperManager());
    }

    static DbRowResult<DbRow> processResultSet(
            CompletableFuture<Long> queryFuture,
            CompletableFuture<ResultWithConn> resultSetFuture,
            ExecutorService executorService,
            DbMapperManager dbMapperManager,
            MapperManager mapperManager) {

        return new DbRowResult<DbRow>() {
            private final AtomicBoolean resultRequested = new AtomicBoolean();

            @Override
            public <U> DbRowResult<U> map(Function<DbRow, U> mapper) {
                throw new UnsupportedOperationException("TODO in later release");
            }

            @Override
            public <U> DbRowResult<U> map(Class<U> type) {
                throw new UnsupportedOperationException("TODO in later release");
            }

            @Override
            public <U> DbRowResult<U> map(GenericType<U> type) {
                throw new UnsupportedOperationException("TODO in later release");
            }

            @Override
            public Flow.Publisher<DbRow> publisher() {
                checkResult();
                return toPublisher();
            }

            @Override
            public CompletionStage<List<DbRow>> collect() {
                checkResult();
                return toFuture();
            }

            private Flow.Publisher<DbRow> toPublisher() {
                return new RowPublisher(executorService,
                                        resultSetFuture,
                                        queryFuture,
                                        dbMapperManager,
                                        mapperManager);
            }

            private CompletionStage<List<DbRow>> toFuture() {
                CompletableFuture<List<DbRow>> result = new CompletableFuture<>();
                toPublisher().subscribe(new Flow.Subscriber<DbRow>() {
                    private final List<DbRow> allRows = new LinkedList<>();

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(DbRow dbRow) {
                        allRows.add(dbRow);
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

            private void checkResult() {
                if (resultRequested.get()) {
                    throw new IllegalStateException("Result has already been requested");
                }
                resultRequested.set(true);
            }

            @Override
            public CompletionStage<Void> consume(Consumer<DbRowResult<DbRow>> consumer) {
                consumer.accept(this);
                return queryFuture.thenRun(() -> {
                });
            }
        };
    }

    static Map<Long, DbColumn> createMetadata(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        Map<Long, DbColumn> byNumbers = new HashMap<>();

        for (int i = 1; i <= columnCount; i++) {
            String name = metaData.getColumnName(i);
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

    private static final class RowPublisher implements Flow.Publisher<DbRow> {
        private final ExecutorService executorService;
        private final CompletableFuture<ResultWithConn> resultSetFuture;
        private final CompletableFuture<Long> queryFuture;
        private final DbMapperManager dbMapperManager;
        private final MapperManager mapperManager;

        private RowPublisher(ExecutorService executorService,
                             CompletableFuture<ResultWithConn> resultSetFuture,
                             CompletableFuture<Long> queryFuture,
                             DbMapperManager dbMapperManager,
                             MapperManager mapperManager) {

            this.executorService = executorService;
            this.resultSetFuture = resultSetFuture;
            this.queryFuture = queryFuture;
            this.dbMapperManager = dbMapperManager;
            this.mapperManager = mapperManager;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super DbRow> subscriber) {
            LinkedBlockingQueue<Long> requestQueue = new LinkedBlockingQueue<>();
            AtomicBoolean cancelled = new AtomicBoolean();

            resultSetFuture.thenAccept(resultWithConn -> {
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

                // and now we can process the data from the database
                executorService.submit(() -> {
                    //now we have a subscriber, we can handle the processing of result set
                    try (Connection conn = resultWithConn.connection) {
                        try (ResultSet rs = resultWithConn.resultSet) {
                            Map<Long, DbColumn> metadata = createMetadata(rs);
                            long count = 0;

                            // now we only want to process next record if it was requested
                            while (!cancelled.get()) {
                                Long nextElement;
                                try {
                                    nextElement = requestQueue.poll(10, TimeUnit.MINUTES);
                                } catch (InterruptedException e) {
                                    LOGGER.severe("Interrupted while polling for requests, terminating DB read");
                                    subscriber.onError(e);
                                    break;
                                }
                                if (nextElement == null) {
                                    LOGGER.severe("No data requested for 10 minutes, terminating DB read");
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
                        }
                    } catch (Exception e) {
                        // if anything fails, just fail
                        queryFuture.completeExceptionally(e);
                        subscriber.onError(e);
                    }
                });
            }).exceptionally(throwable -> {
                queryFuture.completeExceptionally(throwable);
                executorService.submit(() -> subscriber.onError(throwable));
                return null;
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
                        return mapperManager.map(value, theClass, type);
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
