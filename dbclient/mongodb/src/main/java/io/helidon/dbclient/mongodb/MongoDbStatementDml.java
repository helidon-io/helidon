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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbInterceptorContext;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbStatementDml;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.common.InterceptorSupport;

import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * DML statement for MongoDB.
 */
public class MongoDbStatementDml extends MongoDbStatement<DbStatementDml, Long> implements DbStatementDml {

    private static final Logger LOGGER = Logger.getLogger(MongoDbStatementDml.class.getName());

    private DbStatementType dbStatementType;

    private MongoStatement statement;

    MongoDbStatementDml(
            DbStatementType dbStatementType,
            MongoDatabase db,
            String statementName,
            String statement,
            DbMapperManager dbMapperManager,
            MapperManager mapperManager,
            InterceptorSupport interceptors
    ) {
        super(dbStatementType,
              db,
              statementName,
              statement,
              dbMapperManager,
              mapperManager,
              interceptors);
        this.dbStatementType = dbStatementType;
    }

    @Override
    public CompletionStage<Long> execute() {
        statement = new MongoStatement(dbStatementType, READER_FACTORY, build());
        switch (statement.getOperation()) {
        case INSERT:
            dbStatementType = DbStatementType.INSERT;
            break;
        case UPDATE:
            dbStatementType = DbStatementType.UPDATE;
            break;
        case DELETE:
            dbStatementType = DbStatementType.DELETE;
            break;
        default:
            throw new IllegalStateException(
                    String.format("Unexpected value for DML statement: %s", statement.getOperation()));
        }
        return super.execute();
    }

    @Override
    protected CompletionStage<Long> doExecute(
            CompletionStage<DbInterceptorContext> dbContextFuture,
            CompletableFuture<Void> statementFuture,
            CompletableFuture<Long> queryFuture
    ) {
        return MongoDbDMLExecutor.executeDml(
                this,
                dbStatementType,
                statement,
                dbContextFuture,
                statementFuture,
                queryFuture);
    }

    @Override
    protected DbStatementType statementType() {
        return dbStatementType;
    }

}
