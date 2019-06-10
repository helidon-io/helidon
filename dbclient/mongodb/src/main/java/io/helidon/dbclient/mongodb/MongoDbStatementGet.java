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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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

import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * Statement for GET operation in mongoDB.
 */
public class MongoDbStatementGet implements DbStatement<MongoDbStatementGet, CompletionStage<Optional<DbRow>>> {
    private final MongoDbStatementQuery theQuery;

    MongoDbStatementGet(MongoDatabase db,
                        String statementName,
                        String statement,
                        DbMapperManager dbMapperManager,
                        MapperManager mapperManager,
                        InterceptorSupport interceptors) {
        this.theQuery = new MongoDbStatementQuery(DbStatementType.GET,
                                                  db,
                                                  statementName,
                                                  statement,
                                                  dbMapperManager,
                                                  mapperManager,
                                                  interceptors);
    }

    @Override
    public MongoDbStatementGet params(List<?> parameters) {
        theQuery.params(parameters);
        return this;
    }

    @Override
    public MongoDbStatementGet params(Map<String, ?> parameters) {
        theQuery.params(parameters);
        return this;
    }

    @Override
    public <T> MongoDbStatementGet namedParam(T parameters) {
        theQuery.namedParam(parameters);
        return this;
    }

    @Override
    public <T> MongoDbStatementGet indexedParam(T parameters) {
        theQuery.indexedParam(parameters);
        return this;
    }

    @Override
    public MongoDbStatementGet addParam(Object parameter) {
        theQuery.addParam(parameter);
        return this;
    }

    @Override
    public MongoDbStatementGet addParam(String name, Object parameter) {
        theQuery.addParam(name, parameter);
        return this;
    }

    @Override
    public CompletionStage<Optional<DbRow>> execute() {
        CompletableFuture<Optional<DbRow>> result = new CompletableFuture<>();

        theQuery.execute()
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
                            result.completeExceptionally(new DbClientException("Result of get statement "
                                                                                 + theQuery.statementName()
                                                                                 + " returned more than one row."));
                            cancelled.set(true);
                        } else {
                            theRow.set(dbRow);
                            done.set(true);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
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
