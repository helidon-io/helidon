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
package io.helidon.dbclient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import io.helidon.common.context.Context;

/**
 * Interceptor is a mutable object that is sent to {@link io.helidon.dbclient.DbInterceptor}.
 */
class DbInterceptorContextImpl implements DbInterceptorContext {
    private final String dbType;
    private DbStatementType dbStatementType;
    private Context context;
    private String statementName;
    private String statement;
    private CompletionStage<Void> statementFuture;
    private CompletionStage<Long> queryFuture;
    private List<Object> indexedParams;
    private Map<String, Object> namedParams;
    private boolean indexed;

    DbInterceptorContextImpl(String dbType) {
        this.dbType = dbType;
    }

    @Override
    public String dbType() {
        return dbType;
    }

    @Override
    public Context context() {
        return context;
    }

    @Override
    public String statementName() {
        return statementName;
    }

    @Override
    public String statement() {
        return statement;
    }

    @Override
    public CompletionStage<Void> statementFuture() {
        return statementFuture;
    }

    @Override
    public Optional<List<Object>> indexedParameters() {
        if (indexed) {
            return Optional.of(indexedParams);
        }
        throw new IllegalStateException("Indexed parameters are not available for statement with named parameters");
    }

    @Override
    public Optional<Map<String, Object>> namedParameters() {
        if (indexed) {
            throw new IllegalStateException("Named parameters are not available for statement with indexed parameters");
        }
        return Optional.of(namedParams);
    }

    @Override
    public boolean isIndexed() {
        return indexed;
    }

    @Override
    public boolean isNamed() {
        return !indexed;
    }

    @Override
    public DbInterceptorContext context(Context context) {
        this.context = context;
        return this;
    }

    @Override
    public DbInterceptorContext statementName(String newName) {
        this.statementName = newName;
        return this;
    }

    @Override
    public DbInterceptorContext statementFuture(CompletionStage<Void> statementFuture) {
        this.statementFuture = statementFuture;
        return this;
    }

    @Override
    public CompletionStage<Long> resultFuture() {
        return queryFuture;
    }

    @Override
    public DbInterceptorContext resultFuture(CompletionStage<Long> resultFuture) {
        this.queryFuture = resultFuture;
        return this;
    }

    @Override
    public DbInterceptorContext statement(String statement, List<Object> indexedParams) {
        this.statement = statement;
        this.indexedParams = indexedParams;
        this.indexed = true;
        return this;
    }

    @Override
    public DbInterceptorContext statement(String statement, Map<String, Object> namedParams) {
        this.statement = statement;
        this.namedParams = namedParams;
        this.indexed = false;
        return this;
    }

    @Override
    public DbInterceptorContext parameters(List<Object> indexedParameters) {
        if (indexed) {
            this.indexedParams = indexedParameters;
        } else {
            throw new IllegalStateException("Cannot configure indexed parameters for a statement that expects named "
                                                    + "parameters");
        }
        return this;
    }

    @Override
    public DbInterceptorContext parameters(Map<String, Object> namedParameters) {
        if (indexed) {
            throw new IllegalStateException("Cannot configure named parameters for a statement that expects indexed "
                                                    + "parameters");
        }

        this.namedParams = namedParameters;
        return this;
    }

    @Override
    public DbStatementType statementType() {
        return dbStatementType;
    }

    @Override
    public DbInterceptorContext statementType(DbStatementType type) {
        this.dbStatementType = type;
        return this;
    }
}
