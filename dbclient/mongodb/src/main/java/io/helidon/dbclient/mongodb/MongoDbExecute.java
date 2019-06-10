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

import java.util.concurrent.CompletionStage;

import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbResult;
import io.helidon.dbclient.DbStatement;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.DbStatements;
import io.helidon.dbclient.common.AbstractDbExecute;
import io.helidon.dbclient.common.InterceptorSupport;

import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * Execute implementation for MongoDB.
 */
public class MongoDbExecute extends AbstractDbExecute implements DbExecute {

    private final DbMapperManager dbMapperManager;
    private final MapperManager mapperManager;
    private final InterceptorSupport interceptors;
    private final MongoDatabase db;

    MongoDbExecute(MongoDatabase db,
                   DbStatements statements,
                   DbMapperManager dbMapperManager,
                   MapperManager mapperManager,
                   InterceptorSupport interceptors) {
        super(statements);
        this.db = db;
        this.dbMapperManager = dbMapperManager;
        this.mapperManager = mapperManager;
        this.interceptors = interceptors;
    }

    @Override
    public MongoDbStatementQuery createNamedQuery(String statementName, String statement) {
        return new MongoDbStatementQuery(DbStatementType.QUERY,
                                         db,
                                         statementName,
                                         statement,
                                         dbMapperManager,
                                         mapperManager,
                                         interceptors);
    }

    @Override
    public MongoDbStatementGet createNamedGet(String statementName, String statement) {
        return new MongoDbStatementGet(db, statementName, statement, dbMapperManager, mapperManager, interceptors);
    }

    @Override
    public MongoDbStatementDml createNamedDmlStatement(String statementName, String statement) {
        return new MongoDbStatementDml(DbStatementType.DML, db, statementName, statement, dbMapperManager, mapperManager,
                                       interceptors);
    }

    @Override
    public DbStatement<?, CompletionStage<Long>> createNamedInsert(String statementName, String statement) {
        return new MongoDbStatementDml(DbStatementType.INSERT, db, statementName, statement, dbMapperManager, mapperManager,
                                       interceptors);
    }

    @Override
    public DbStatement<?, CompletionStage<Long>> createNamedUpdate(String statementName, String statement) {
        return new MongoDbStatementDml(DbStatementType.UPDATE, db, statementName, statement, dbMapperManager, mapperManager,
                                       interceptors);
    }

    @Override
    public DbStatement<?, CompletionStage<Long>> createNamedDelete(String statementName, String statement) {
        return new MongoDbStatementDml(DbStatementType.DELETE, db, statementName, statement, dbMapperManager, mapperManager,
                                       interceptors);
    }

    @Override
    public DbStatement<?, DbResult> createNamedStatement(String statementName, String statement) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
