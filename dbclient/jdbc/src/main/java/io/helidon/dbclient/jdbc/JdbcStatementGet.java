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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.mapper.MapperManager;
import io.helidon.common.reactive.Flow;
import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatement;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.common.InterceptorSupport;

/**
 * A JDBC get implementation.
 */
class JdbcStatementGet implements DbStatement<JdbcStatementGet, CompletionStage<Optional<DbRow>>> {
    private final JdbcStatementQuery query;

    JdbcStatementGet(ConnectionPool connectionPool,
                     ExecutorService executorService,
                     String statementName,
                     String statement,
                     DbMapperManager dbMapperMananger,
                     MapperManager mapperManager,
                     InterceptorSupport interceptors) {

        this.query = new JdbcStatementQuery(DbStatementType.GET,
                                            connectionPool,
                                            executorService,
                                            statementName,
                                            statement,
                                            dbMapperMananger,
                                            mapperManager,
                                            interceptors);
    }

    @Override
    public JdbcStatementGet params(List<?> parameters) {
        query.params(parameters);
        return this;
    }

    @Override
    public JdbcStatementGet params(Map<String, ?> parameters) {
        query.params(parameters);
        return this;
    }

    @Override
    public <T> JdbcStatementGet namedParam(T parameters) {
        query.namedParam(parameters);
        return this;
    }

    @Override
    public <T> JdbcStatementGet indexedParam(T parameters) {
        query.indexedParam(parameters);
        return this;
    }

    @Override
    public JdbcStatementGet addParam(Object parameter) {
        query.addParam(parameter);
        return this;
    }

    @Override
    public JdbcStatementGet addParam(String name, Object parameter) {
        query.addParam(name, parameter);
        return this;
    }

    @Override
    public CompletionStage<Optional<DbRow>> execute() {
        CompletableFuture<Optional<DbRow>> result = new CompletableFuture<>();

        query.execute()
                .publisher()
                .subscribe(new Flow.Subscriber<DbRow>() {
                    private Flow.Subscription subscription;
                    private final AtomicBoolean done = new AtomicBoolean(false);
                    // defense against bad publisher - if I receive complete after cancelled...
                    private final AtomicBoolean cancelled = new AtomicBoolean(false);
                    private final AtomicReference<DbRow> theRow = new AtomicReference<>();

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        this.subscription = subscription;
                        subscription.request(2);
                    }

                    @Override
                    public void onNext(DbRow dbRow) {
                        if (done.get()) {
                            subscription.cancel();
                            result.completeExceptionally(new DbClientException("Result of get statement " + query
                                    .name() + " returned "
                                                                                       + "more than one row."));
                            cancelled.set(true);
                        } else {
                            theRow.set(dbRow);
                            done.set(true);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        if (cancelled.get()) {
                            return;
                        }
                        result.completeExceptionally(throwable);
                    }

                    @Override
                    public void onComplete() {
                        if (cancelled.get()) {
                            return;
                        }
                        result.complete(Optional.ofNullable(theRow.get()));
                    }
                });
        return result;
    }
}
