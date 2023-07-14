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
package io.helidon.dbclient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;

import static io.helidon.dbclient.DbStatementParameters.UNDEFINED;

/**
 * Interceptor context to get (and possibly manipulate) database operations.
 * <p>
 * This is a mutable object - acts as a builder during the invocation of {@link DbClientService}.
 * The interceptors are executed sequentially, so there is no need for synchronization.
 */
public class DbClientServiceContextImpl implements DbClientServiceContext {

    private final DbExecuteContext execContext;
    private final DbStatementType stmtType;
    private final CompletionStage<Void> stmtFuture;
    private final CompletionStage<Long> queryFuture;
    private DbStatementParameters stmtParams;
    private Context context;
    private String stmt;
    private String stmtName;

    /**
     * Create a new instance.
     *
     * @param execContext execution context
     * @param stmtType    statement type
     * @param stmtFuture  statement future
     * @param queryFuture query future
     * @param stmtParams  statement parameters
     */
    DbClientServiceContextImpl(DbExecuteContext execContext,
                               DbStatementType stmtType,
                               CompletionStage<Void> stmtFuture,
                               CompletionStage<Long> queryFuture,
                               DbStatementParameters stmtParams) {

        this.execContext = execContext;
        this.stmtType = stmtType;
        this.stmtFuture = stmtFuture;
        this.queryFuture = queryFuture;
        this.stmt = execContext.statement();
        this.stmtName = execContext.statementName();
        this.stmtParams = stmtParams;
    }

    @Override
    public Context context() {
        return context != null ? context : Contexts.context().orElseGet(Contexts::globalContext);
    }

    @Override
    public String statement() {
        return stmt;
    }

    @Override
    public String statementName() {
        return stmtName;
    }

    @Override
    public DbStatementType statementType() {
        return stmtType;
    }

    @Override
    public DbStatementParameters statementParameters() {
        return stmtParams;
    }

    @Override
    public String dbType() {
        return execContext.dbType();
    }

    @Override
    public CompletionStage<Void> statementFuture() {
        return stmtFuture;
    }

    @Override
    public CompletionStage<Long> resultFuture() {
        return queryFuture;
    }

    @Override
    public DbClientServiceContextImpl statement(String stmt, List<Object> parameters) {
        if (stmtParams == UNDEFINED) {
            stmtParams = new DbIndexedStatementParameters();
        }
        parameters.forEach(stmtParams::addParam);
        this.stmt = stmt;
        return this;
    }

    @Override
    public DbClientServiceContextImpl statement(String stmt, Map<String, Object> parameters) {
        if (stmtParams == UNDEFINED) {
            stmtParams = new DbNamedStatementParameters();
        }
        parameters.forEach(stmtParams::addParam);
        this.stmt = stmt;
        return this;
    }

    @Override
    public DbClientServiceContextImpl parameters(List<Object> parameters) {
        parameters.forEach(stmtParams::addParam);
        return this;
    }

    @Override
    public DbClientServiceContextImpl parameters(Map<String, Object> parameters) {
        parameters.forEach(stmtParams::addParam);
        return this;
    }

    @Override
    public DbClientServiceContextImpl context(Context context) {
        this.context = context;
        return this;
    }

    @Override
    public DbClientServiceContextImpl statement(String name) {
        this.stmtName = name;
        return this;
    }

    @Override
    public DbClientServiceContextImpl statementName(String name) {
        this.stmtName = name;
        return this;
    }
}
