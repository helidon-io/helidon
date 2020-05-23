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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.mapper.MapperManager;
import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementGet;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.common.InterceptorSupport;
import io.helidon.dbclient.mongodb.MongoDbTransaction.TransactionManager;

import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * Statement for GET operation in mongoDB.
 */
public class MongoDbStatementGet implements DbStatementGet {

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
    public Single<Optional<DbRow>> execute() {
        return Single.from(theQuery.execute())
                .toOptionalSingle();
    }

    /**
     * Set target transaction for this statement.
     *
     * @param tx MongoDB transaction session
     * @return MongoDB statement builder
     */
    MongoDbStatementGet inTransaction(TransactionManager tx) {
        theQuery.inTransaction(tx);
        return this;
    }

}
