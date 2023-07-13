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

/**
 * Interceptor context to get (and possibly manipulate) database operations.
 * <p>
 * This is a mutable object - acts as a builder during the invocation of {@link DbClientService}.
 * The interceptors are executed sequentially, so there is no need for synchronization.
 */
public interface DbClientServiceContext {

    /**
     * Context with parameters passed from the caller, such as {@code SpanContext} for tracing.
     *
     * @return context associated with this request
     */
    Context context();

    /**
     * Text of the statement to be executed.
     *
     * @return statement text
     */
    String statement();

    /**
     * Name of a statement to be executed.
     * Ad hoc statements have names generated.
     *
     * @return name of the statement
     */
    String statementName();

    /**
     * Type of the statement being executed.
     *
     * @return statement type
     */
    DbStatementType statementType();

    /**
     * Get the statement parameters.
     *
     * @return statement parameters
     */
    DbStatementParameters statementParameters();

    /**
     * Type of this database (usually the same string used by the {@link io.helidon.dbclient.spi.DbClientProvider#name()}).
     *
     * @return type of database
     */
    String dbType();

    /**
     * A stage that is completed once the statement finishes execution.
     *
     * @return statement future
     */
    CompletionStage<Void> statementFuture();

    /**
     * A stage that is completed once the results were fully read. The number returns either the number of modified
     * records or the number of records actually read.
     *
     * @return stage that completes once all query results were processed.
     */
    CompletionStage<Long> resultFuture();

    /**
     * Set a new statement with indexed parameters to be used.
     *
     * @param stmt       statement text
     * @param parameters indexed parameters
     * @return updated interceptor context
     */
    DbClientServiceContext statement(String stmt, List<Object> parameters);

    /**
     * Set a new statement with named parameters to be used.
     *
     * @param stmt       statement text
     * @param parameters named parameters
     * @return updated interceptor context
     */
    DbClientServiceContext statement(String stmt, Map<String, Object> parameters);

    /**
     * Set new indexed parameters to be used.
     *
     * @param parameters parameters
     * @return updated interceptor context
     * @throws IllegalArgumentException in case the statement is using named parameters
     */
    DbClientServiceContext parameters(List<Object> parameters);

    /**
     * Set new named parameters to be used.
     *
     * @param parameters parameters
     * @return updated interceptor context
     * @throws IllegalArgumentException in case the statement is using indexed parameters
     */
    DbClientServiceContext parameters(Map<String, Object> parameters);

    /**
     * Set a new context to be used by other interceptors and when executing the statement.
     *
     * @param context context to use
     * @return updated interceptor context
     */
    DbClientServiceContext context(Context context);

    /**
     * Set a new statement to be used.
     *
     * @param name statement text to use
     * @return updated interceptor context
     */
    DbClientServiceContext statement(String name);

    /**
     * Set a new statement name to be used.
     *
     * @param name statement name to use
     * @return updated interceptor context
     */
    DbClientServiceContext statementName(String name);

    /**
     * Create a new client service context.
     *
     * @param execContext execution context
     * @param stmtType    statement type
     * @param stmtFuture  statement future
     * @param queryFuture query future
     * @param stmtParams  statement parameters
     * @return new client service context
     */
    static DbClientServiceContext create(DbExecuteContext execContext,
                                         DbStatementType stmtType,
                                         CompletionStage<Void> stmtFuture,
                                         CompletionStage<Long> queryFuture,
                                         DbStatementParameters stmtParams) {
        return new DbClientServiceContextImpl(execContext, stmtType, stmtFuture, queryFuture, stmtParams);
    }
}
