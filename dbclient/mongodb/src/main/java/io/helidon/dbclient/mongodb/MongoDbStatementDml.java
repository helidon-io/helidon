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

import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbStatementDml;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.common.DbStatementContext;

import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * DML statement for MongoDB.
 */
public class MongoDbStatementDml extends MongoDbStatement<DbStatementDml, Single<Long>> implements DbStatementDml {

    private DbStatementType dbStatementType;
    private MongoStatement statement;

    MongoDbStatementDml(MongoDatabase db, DbStatementContext statementContext) {
        super(db, statementContext);
        this.dbStatementType = statementContext.statementType();
    }

    @Override
    public Single<Long> execute() {
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
    protected Single<Long> doExecute(Single<DbClientServiceContext> dbContext,
                                     CompletableFuture<Void> statementFuture,
                                     CompletableFuture<Long> queryFuture) {

        return Single.from(MongoDbDMLExecutor.executeDml(
                this,
                dbStatementType,
                statement,
                dbContext,
                statementFuture,
                queryFuture));
    }

    @Override
    protected DbStatementType statementType() {
        return dbStatementType;
    }

}
