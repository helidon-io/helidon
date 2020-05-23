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
package io.helidon.dbclient.common;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbInterceptor;
import io.helidon.dbclient.DbInterceptorContext;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbStatement;
import io.helidon.dbclient.DbStatementType;

/**
 * Common statement methods and fields.
 *
 * @param <S> type of a subclass
 * @param <R> the result type of the statement as returned by {@link #execute()}
 */
public abstract class AbstractStatement<S extends DbStatement<S, R>, R> implements DbStatement<S, R> {

    private ParamType paramType = ParamType.UNKNOWN;
    private StatementParameters parameters;
    private final DbStatementType dbStatementType;
    private final String statementName;
    private final String statement;
    private final DbMapperManager dbMapperManager;
    private final MapperManager mapperManager;
    private final InterceptorSupport interceptors;

    /**
     * Statement that handles parameters.
     *
     * @param dbStatementType   type of this statement
     * @param statementName   name of this statement
     * @param statement       text of this statement
     * @param dbMapperManager db mapper manager to use when mapping types to parameters
     * @param mapperManager   mapper manager to use when mapping results
     * @param interceptors    interceptors to be executed
     */
    protected AbstractStatement(DbStatementType dbStatementType,
                                String statementName,
                                String statement,
                                DbMapperManager dbMapperManager,
                                MapperManager mapperManager,
                                InterceptorSupport interceptors) {
        this.dbStatementType = dbStatementType;
        this.statementName = statementName;
        this.statement = statement;
        this.dbMapperManager = dbMapperManager;
        this.mapperManager = mapperManager;
        this.interceptors = interceptors;
    }

    @Override
    public R execute() {
        CompletableFuture<Long> queryFuture = new CompletableFuture<>();
        CompletableFuture<Void> statementFuture = new CompletableFuture<>();
        DbInterceptorContext dbContext = DbInterceptorContext.create(dbType())
                .resultFuture(queryFuture)
                .statementFuture(statementFuture);

        update(dbContext);
        CompletionStage<DbInterceptorContext> dbContextFuture = invokeInterceptors(dbContext);
        return doExecute(dbContextFuture, statementFuture, queryFuture);
    }

    /**
     * Invoke all interceptors.
     *
     * @param dbContext initial interceptor context
     * @return future with the result of interceptors processing
     */
    CompletionStage<DbInterceptorContext> invokeInterceptors(DbInterceptorContext dbContext) {
        CompletableFuture<DbInterceptorContext> result = CompletableFuture.completedFuture(dbContext);

        dbContext.context(Contexts.context().orElseGet(Context::create));

        for (DbInterceptor interceptor : interceptors.interceptors(statementType(), statementName())) {
            result = result.thenCompose(interceptor::statement);
        }

        return result;
    }

    /**
     * Type of this statement.
     *
     * @return statement type
     */
    protected DbStatementType statementType() {
        return dbStatementType;
    }

    /**
     * Execute the statement against the database.
     *
     * @param dbContext future that completes after all interceptors are invoked
     * @param statementFuture future that should complete when the statement finishes execution
     * @param queryFuture future that should complete when the result set is fully read (if one exists),
     *                      otherwise complete same as statementFuture
     * @return result of this db statement.
     */
    protected abstract R doExecute(CompletionStage<DbInterceptorContext> dbContext,
                                   CompletableFuture<Void> statementFuture,
                                   CompletableFuture<Long> queryFuture);

    /**
     * Type of this database to use in interceptor context.
     *
     * @return type of this db
     */
    protected abstract String dbType();

    @Override
    public S params(List<?> parameters) {
        Objects.requireNonNull(parameters, "Parameters cannot be null (may be an empty list)");

        initParameters(ParamType.INDEXED);
        this.parameters.params(parameters);

        return me();
    }

    @Override
    public S params(Map<String, ?> parameters) {
        initParameters(ParamType.NAMED);
        this.parameters.params(parameters);
        return me();
    }

    @Override
    public S namedParam(Object parameters) {
        initParameters(ParamType.NAMED);
        this.parameters.namedParam(parameters);
        return me();
    }

    @Override
    public S indexedParam(Object parameters) {
        initParameters(ParamType.INDEXED);
        this.parameters.indexedParam(parameters);
        return me();
    }

    @Override
    public S addParam(Object parameter) {
        initParameters(ParamType.INDEXED);
        this.parameters.addParam(parameter);
        return me();
    }

    @Override
    public S addParam(String name, Object parameter) {
        initParameters(ParamType.NAMED);
        this.parameters.addParam(name, parameter);
        return me();
    }

    /**
     * Type of parameters of this statement.
     *
     * @return indexed or named, or unknown in case it could not be yet defined
     */
    protected ParamType paramType() {
        return paramType;
    }

    /**
     * Db mapper manager.
     *
     * @return mapper manager for DB types
     */
    protected DbMapperManager dbMapperManager() {
        return dbMapperManager;
    }

    /**
     * Mapper manager.
     *
     * @return generic mapper manager
     */
    protected MapperManager mapperManager() {
        return mapperManager;
    }

    /**
     * Get the named parameters of this statement.
     *
     * @return name parameter map
     * @throws java.lang.IllegalStateException in case this statement is using indexed parameters
     */
    protected Map<String, Object> namedParams() {
        initParameters(ParamType.NAMED);
        return parameters.namedParams();
    }

    /**
     * Get the indexed parameters of this statement.
     *
     * @return parameter list
     * @throws java.lang.IllegalStateException in case this statement is using named parameters
     */
    protected List<Object> indexedParams() {
        initParameters(ParamType.INDEXED);
        return parameters.indexedParams();
    }

    /**
     * Statement name.
     *
     * @return name of this statement (never null, may be generated)
     */
    protected String statementName() {
        return statementName;
    }

    /**
     * Statement text.
     *
     * @return text of this statement
     */
    protected String statement() {
        return statement;
    }

    /**
     * Update the interceptor context with the statement name, statement and
     * statement parameters.
     *
     * @param dbContext interceptor context
     */
    protected void update(DbInterceptorContext dbContext) {
        dbContext.statementName(statementName);
        initParameters(ParamType.INDEXED);

        if (paramType == ParamType.NAMED) {
            dbContext.statement(statement, parameters.namedParams());
        } else {
            dbContext.statement(statement, parameters.indexedParams());
        }
        dbContext.statementType(statementType());
    }

    /**
     * Returns this builder cast to the correct type.
     *
     * @return this as type extending this class
     */
    @SuppressWarnings("unchecked")
    protected S me() {
        return (S) this;
    }

    private void initParameters(ParamType type) {
        if (this.paramType != ParamType.UNKNOWN) {
            // already initialized
            return;
        }
        switch (type) {
        case NAMED:
            this.paramType = ParamType.NAMED;
            this.parameters = new NamedStatementParameters(dbMapperManager);
            break;
        case INDEXED:
        case UNKNOWN:
        default:
            this.paramType = ParamType.INDEXED;
            this.parameters = new IndexedStatementParameters(dbMapperManager);
            break;
        }
    }
}
