/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
 * Interceptor context to get (and possibly manipulate) database operations.
 * <p>
 * This is a mutable object - acts as a builder during the invocation of {@link DbClientService}.
 * The interceptors are executed sequentially, so there is no need for synchronization.
 */
public interface DbClientServiceContext {
    /**
     * Create a new interceptor context for a database provider.
     *
     * @param dbType a short name of the db type (such as jdbc:mysql)
     * @return a new interceptor context ready to be configured
     */
    static DbClientServiceContext create(String dbType) {
        return new DbClientServiceContextImpl(dbType);
    }

    /**
     * Type of this database (usually the same string used by the {@link io.helidon.dbclient.spi.DbClientProvider#name()}).
     *
     * @return type of database
     */
    String dbType();

    /**
     * Context with parameters passed from the caller, such as {@code SpanContext} for tracing.
     *
     * @return context associated with this request
     */
    Context context();

    /**
     * Name of a statement to be executed.
     * Ad hoc statements have names generated.
     *
     * @return name of the statement
     */
    String statementName();

    /**
     * Text of the statement to be executed.
     *
     * @return statement text
     */
    String statement();

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
     * Indexed parameters (if used).
     *
     * @return indexed parameters (empty if this statement parameters are not indexed)
     */
    Optional<List<Object>> indexedParameters();

    /**
     * Named parameters (if used).
     *
     * @return named parameters (empty if this statement parameters are not named)
     */
    Optional<Map<String, Object>> namedParameters();

    /**
     * Whether this is a statement with indexed parameters.
     *
     * @return Whether this statement has indexed parameters ({@code true}) or named parameters {@code false}.
     */
    boolean isIndexed();

    /**
     * Whether this is a statement with named parameters.
     *
     * @return Whether this statement has named parameters ({@code true}) or indexed parameters {@code false}.
     */
    boolean isNamed();

    /**
     * Type of the statement being executed.
     * @return statement type
     */
    DbStatementType statementType();

    /**
     * Set a new context to be used by other interceptors and when executing the statement.
     *
     * @param context context to use
     * @return updated interceptor context
     */
    DbClientServiceContext context(Context context);

    /**
     * Set a new statement name to be used.
     *
     * @param newName statement name to use
     * @return updated interceptor context
     */
    DbClientServiceContext statementName(String newName);

    /**
     * Set a new future to mark completion of the statement.
     *
     * @param statementFuture future
     * @return updated interceptor context
     */
    DbClientServiceContext statementFuture(CompletionStage<Void> statementFuture);

    /**
     * Set a new future to mark completion of the result (e.g. query or number of modified records).
     *
     * @param queryFuture future
     * @return updated interceptor context
     */
    DbClientServiceContext resultFuture(CompletionStage<Long> queryFuture);

    /**
     * Set a new statement with indexed parameters to be used.
     *
     * @param statement     statement text
     * @param indexedParams indexed parameters
     * @return updated interceptor context
     */
    DbClientServiceContext statement(String statement, List<Object> indexedParams);

    /**
     * Set a new statement with named parameters to be used.
     *
     * @param statement   statement text
     * @param namedParams named parameters
     * @return updated interceptor context
     */
    DbClientServiceContext statement(String statement, Map<String, Object> namedParams);

    /**
     * Set new indexed parameters to be used.
     *
     * @param indexedParameters parameters
     * @return updated interceptor context
     * @throws IllegalArgumentException in case the statement is using named parameters
     */
    DbClientServiceContext parameters(List<Object> indexedParameters);

    /**
     * Set new named parameters to be used.
     *
     * @param namedParameters parameters
     * @return updated interceptor context
     * @throws IllegalArgumentException in case the statement is using indexed parameters
     */
    DbClientServiceContext parameters(Map<String, Object> namedParameters);

    /**
     * Set the type of the statement.
     *
     * @param type statement type
     * @return updated interceptor context
     */
    DbClientServiceContext statementType(DbStatementType type);
}
