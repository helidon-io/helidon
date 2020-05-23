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
import java.util.concurrent.CompletionStage;

import io.helidon.common.mapper.MapperManager;
import io.helidon.common.reactive.Multi;
import io.helidon.dbclient.DbInterceptorContext;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementQuery;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.common.InterceptorSupport;

import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * Implementation of a query for MongoDB.
 */
class MongoDbStatementQuery extends MongoDbStatement<DbStatementQuery, Multi<DbRow>> implements DbStatementQuery {

    MongoDbStatementQuery(DbStatementType dbStatementType,
                          MongoDatabase db,
                          String statementName,
                          String statement,
                          DbMapperManager dbMapperManager,
                          MapperManager mapperManager,
                          InterceptorSupport interceptors) {

        super(dbStatementType,
              db,
              statementName,
              statement,
              dbMapperManager,
              mapperManager,
              interceptors);
    }

    @Override
    protected Multi<DbRow> doExecute(CompletionStage<DbInterceptorContext> dbContextFuture,
                                     CompletableFuture<Void> statementFuture,
                                     CompletableFuture<Long> queryFuture) {

        return MongoDbQueryExecutor.executeQuery(
                this,
                dbContextFuture,
                statementFuture,
                queryFuture);
    }

}
