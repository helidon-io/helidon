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
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbStatementDml;
import io.helidon.dbclient.DbStatementGet;
import io.helidon.dbclient.DbStatementQuery;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.common.AbstractDbExecute;
import io.helidon.dbclient.common.DbClientContext;
import io.helidon.dbclient.common.DbStatementContext;

import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * Execute implementation for MongoDB.
 */
public class MongoDbExecute extends AbstractDbExecute implements DbExecute {

    private final MongoDatabase db;
    private final DbClientContext clientContext;

    MongoDbExecute(MongoDatabase db,
                   DbClientContext clientContext) {
        super(clientContext.statements());
        this.db = db;
        this.clientContext = clientContext;
    }

    @Override
    public DbStatementQuery createNamedQuery(String statementName, String statement) {
        return new MongoDbStatementQuery(db,
                                         DbStatementContext.create(clientContext,
                                                                   DbStatementType.QUERY,
                                                                   statementName,
                                                                   statement));
    }

    @Override
    public DbStatementGet createNamedGet(String statementName, String statement) {
        return new MongoDbStatementGet(db,
                                       DbStatementContext.create(clientContext,
                                                                 DbStatementType.GET,
                                                                 statementName,
                                                                 statement));
    }

    @Override
    public DbStatementDml createNamedDmlStatement(String statementName, String statement) {
        return new MongoDbStatementDml(db,
                                       DbStatementContext.create(clientContext,
                                                                 DbStatementType.DML,
                                                                 statementName,
                                                                 statement));
    }

    @Override
    public DbStatementDml createNamedInsert(String statementName, String statement) {
        return new MongoDbStatementDml(db,
                                       DbStatementContext.create(clientContext,
                                                                 DbStatementType.INSERT,
                                                                 statementName,
                                                                 statement));
    }

    @Override
    public DbStatementDml createNamedUpdate(String statementName, String statement) {
        return new MongoDbStatementDml(db,
                                       DbStatementContext.create(clientContext,
                                                                 DbStatementType.UPDATE,
                                                                 statementName,
                                                                 statement));
    }

    @Override
    public DbStatementDml createNamedDelete(String statementName, String statement) {
        return new MongoDbStatementDml(db,
                                       DbStatementContext.create(clientContext,
                                                                 DbStatementType.DELETE,
                                                                 statementName,
                                                                 statement));
    }

    // MongoDB internals are not blocking. Single instance is returned as already completed.
    @Override
    public <C> Single<C> unwrap(Class<C> cls) {
        if (MongoDatabase.class.isAssignableFrom(cls)) {
            final CompletableFuture<MongoDatabase> future = new CompletableFuture<>();
            future.complete(db);
            return Single.create(future).map(cls::cast);
        } else {
            throw new UnsupportedOperationException(String.format("Class %s is not supported for unwrap", cls.getName()));
        }
    }

}
