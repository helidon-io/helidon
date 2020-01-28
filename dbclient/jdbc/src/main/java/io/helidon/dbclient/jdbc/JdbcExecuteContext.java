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
package io.helidon.dbclient.jdbc;

import java.sql.Connection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.common.InterceptorSupport;

/**
 * Stuff needed by each and every statement.
 */
final class JdbcExecuteContext {

    private final ExecutorService executorService;
    private final InterceptorSupport interceptors;
    private final DbMapperManager dbMapperManager;
    private final MapperManager mapperManager;
    private final String dbType;
    private final CompletionStage<Connection> connection;
    private final ConcurrentHashMap.KeySetView<CompletableFuture<Long>, Boolean> futures = ConcurrentHashMap.newKeySet();

    private JdbcExecuteContext(ExecutorService executorService,
                               InterceptorSupport interceptors,
                               DbMapperManager dbMapperManager,
                               MapperManager mapperManager,
                               String dbType,
                               CompletionStage<Connection> connection) {
        this.executorService = executorService;
        this.interceptors = interceptors;
        this.dbMapperManager = dbMapperManager;
        this.mapperManager = mapperManager;
        this.dbType = dbType;
        this.connection = connection;
    }

    static JdbcExecuteContext create(ExecutorService executorService,
                                     InterceptorSupport interceptors,
                                     String dbType,
                                     CompletionStage<Connection> connection,
                                     DbMapperManager dbMapperManager,
                                     MapperManager mapperManager) {
        return new JdbcExecuteContext(executorService,
                                      interceptors,
                                      dbMapperManager,
                                      mapperManager,
                                      dbType,
                                      connection);
    }

    ExecutorService executorService() {
        return executorService;
    }

    InterceptorSupport interceptors() {
        return interceptors;
    }

    DbMapperManager dbMapperManager() {
        return dbMapperManager;
    }

    MapperManager mapperManager() {
        return mapperManager;
    }

    String dbType() {
        return dbType;
    }

    CompletionStage<Connection> connection() {
        return connection;
    }

    void addFuture(CompletableFuture<Long> queryFuture) {
        this.futures.add(queryFuture);
    }

    public CompletionStage<Void> whenComplete() {
        CompletionStage<?> overallStage = CompletableFuture.completedFuture(null);

        for (CompletableFuture<Long> future : futures) {
            overallStage = overallStage.thenCompose(o -> future);
        }

        return overallStage.thenAccept(it -> {
        });
    }

}
