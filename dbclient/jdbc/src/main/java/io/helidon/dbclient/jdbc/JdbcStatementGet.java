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
package io.helidon.dbclient.jdbc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementGet;
import io.helidon.dbclient.common.DbStatementContext;

/**
 * A JDBC get implementation.
 * Delegates to {@link io.helidon.dbclient.jdbc.JdbcStatementQuery} and processes the result using a subscriber
 * to read the first value.
 */
class JdbcStatementGet implements DbStatementGet {

    private final JdbcStatementQuery query;

    JdbcStatementGet(JdbcExecuteContext executeContext,
                     DbStatementContext statementContext) {

        this.query = new JdbcStatementQuery(executeContext,
                                            statementContext);
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
    public JdbcStatementGet namedParam(Object parameters) {
        query.namedParam(parameters);
        return this;
    }

    @Override
    public JdbcStatementGet indexedParam(Object parameters) {
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
    public Single<Optional<DbRow>> execute() {
        return Single.from(query.execute())
                .toOptionalSingle();
    }
}
