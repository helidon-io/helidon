/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

import io.helidon.dbclient.DbExecuteContext;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementGet;
import io.helidon.dbclient.DbStatementType;

import com.mongodb.client.MongoDatabase;

/**
 * MongoDB {@link DbStatementGet} implementation.
 */
public class MongoDbStatementGet extends MongoDbStatement<DbStatementGet> implements DbStatementGet {

    private final MongoDbStatementQuery theQuery;

    /**
     * Create a new instance.
     *
     * @param db      MongoDb instance
     * @param context context
     */
    MongoDbStatementGet(MongoDatabase db, DbExecuteContext context) {
        super(db, context);
        this.theQuery = new MongoDbStatementQuery(db, context);
    }

    @Override
    public DbStatementType statementType() {
        return DbStatementType.GET;
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
    public MongoDbStatementGet namedParam(Object parameters) {
        theQuery.namedParam(parameters);
        return this;
    }

    @Override
    public MongoDbStatementGet indexedParam(Object parameters) {
        theQuery.indexedParam(parameters);
        return this;
    }

    @Override
    public MongoDbStatementGet indexedParam(Object parameters, String... names) {
        theQuery.indexedParam(parameters, names);
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
    public Optional<DbRow> execute() {
        return theQuery.execute().findFirst();
    }
}
