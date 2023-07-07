/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import io.helidon.dbclient.DbClientContext;
import io.helidon.dbclient.DbExecuteBase;
import io.helidon.dbclient.DbExecuteContext;
import io.helidon.dbclient.DbStatementDml;
import io.helidon.dbclient.DbStatementGet;
import io.helidon.dbclient.DbStatementQuery;

import com.mongodb.client.MongoDatabase;

import static io.helidon.dbclient.DbStatementType.DELETE;
import static io.helidon.dbclient.DbStatementType.DML;
import static io.helidon.dbclient.DbStatementType.INSERT;
import static io.helidon.dbclient.DbStatementType.UPDATE;

/**
 * Execute implementation for MongoDB.
 */
public class MongoDbExecute extends DbExecuteBase {

    private final MongoDatabase db;

    MongoDbExecute(DbClientContext ctx, MongoDatabase db) {
        super(ctx);
        this.db = db;
    }

    @Override
    public DbStatementQuery createNamedQuery(String name, String stmt) {
        return new MongoDbStatementQuery(db, DbExecuteContext.create(name, stmt, context()));
    }

    @Override
    public DbStatementGet createNamedGet(String name, String stmt) {
        return new MongoDbStatementGet(db, DbExecuteContext.create(name, stmt, context()));
    }

    @Override
    public DbStatementDml createNamedDmlStatement(String name, String stmt) {
        return new MongoDbStatementDml(db, DML, DbExecuteContext.create(name, stmt, context()));
    }

    @Override
    public DbStatementDml createNamedInsert(String name, String stmt) {
        return new MongoDbStatementDml(db, INSERT, DbExecuteContext.create(name, stmt, context()));
    }

    @Override
    public DbStatementDml createNamedUpdate(String name, String stmt) {
        return new MongoDbStatementDml(db, UPDATE, DbExecuteContext.create(name, stmt, context()));
    }

    @Override
    public DbStatementDml createNamedDelete(String name, String stmt) {
        return new MongoDbStatementDml(db, DELETE, DbExecuteContext.create(name, stmt, context()));
    }

    @Override
    public <C> C unwrap(Class<C> cls) {
        if (MongoDatabase.class.isAssignableFrom(cls)) {
            return cls.cast(db);
        }
        throw new UnsupportedOperationException(String.format(
                "Class %s is not supported for unwrap",
                cls.getName()));
    }
}
