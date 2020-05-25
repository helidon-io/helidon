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

import io.helidon.dbclient.common.DbClientContext;

/**
 * Stuff needed by each and every statement.
 */
final class JdbcExecuteContext extends DbClientContext {

    private final ConcurrentHashMap.KeySetView<CompletableFuture<Long>, Boolean> futures = ConcurrentHashMap.newKeySet();
    private final ExecutorService executorService;
    private final String dbType;
    private final CompletionStage<Connection> connection;

    private JdbcExecuteContext(Builder builder) {
        super(builder);
        this.executorService = builder.executorService;
        this.dbType = builder.dbType;
        this.connection = builder.connection;
    }

    /**
     * Builder to create new instances.
     * @return a new builder instance
     */
    static Builder jdbcBuilder() {
        return new Builder();
    }

    ExecutorService executorService() {
        return executorService;
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

    CompletionStage<Void> whenComplete() {
        CompletionStage<?> overallStage = CompletableFuture.completedFuture(null);

        for (CompletableFuture<Long> future : futures) {
            overallStage = overallStage.thenCompose(o -> future);
        }

        return overallStage.thenAccept(it -> {
        });
    }

    static class Builder extends BuilderBase<Builder> implements io.helidon.common.Builder<JdbcExecuteContext> {
        private ExecutorService executorService;
        private String dbType;
        private CompletionStage<Connection> connection;

        @Override
        public JdbcExecuteContext build() {
            return new JdbcExecuteContext(this);
        }

        Builder executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        Builder dbType(String dbType) {
            this.dbType = dbType;
            return this;
        }

        Builder connection(CompletionStage<Connection> connection) {
            this.connection = connection;
            return this;
        }
    }
}
