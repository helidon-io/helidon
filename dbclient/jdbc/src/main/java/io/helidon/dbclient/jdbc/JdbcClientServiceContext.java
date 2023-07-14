/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.util.concurrent.CompletionStage;

import io.helidon.common.context.Context;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbExecuteContext;
import io.helidon.dbclient.DbStatementType;

abstract class JdbcClientServiceContext implements DbClientServiceContext {

    private final DbExecuteContext execContext;
    private final DbStatementType stmtType;
    private final CompletionStage<Void> stmtFuture;
    private final CompletionStage<Long> queryFuture;
    private Context context;
    private String statementName;

    JdbcClientServiceContext(Context context,
                             DbExecuteContext execContext,
                             DbStatementType stmtType,
                             CompletionStage<Void> stmtFuture,
                             CompletionStage<Long> queryFuture) {

        this.context = context;
        this.execContext = execContext;
        this.stmtType = stmtType;
        this.stmtFuture = stmtFuture;
        this.queryFuture = queryFuture;
        this.statementName = execContext.statementName();
    }

    @Override
    public Context context() {
        return context;
    }

    /**
     * Get the execution context.
     *
     * @return execution context
     */
    public DbExecuteContext execContext() {
        return execContext;
    }

    @Override
    public String statement() {
        return execContext.statement();
    }

    @Override
    public String statementName() {
        return statementName;
    }

    @Override
    public DbStatementType statementType() {
        return stmtType;
    }

    @Override
    public String dbType() {
        return execContext.dbType();
    }

    @Override
    public JdbcClientServiceContext context(Context context) {
        this.context = context;
        return this;
    }

    @Override
    public JdbcClientServiceContext statementName(String name) {
        this.statementName = name;
        return this;
    }

    @Override
    public CompletionStage<Void> statementFuture() {
        return stmtFuture;
    }

    @Override
    public CompletionStage<Long> resultFuture() {
        return queryFuture;
    }
}
