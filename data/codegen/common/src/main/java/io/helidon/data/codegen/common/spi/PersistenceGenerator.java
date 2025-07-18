/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data.codegen.common.spi;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.data.codegen.common.MethodParams;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.query.DataQuery;
import io.helidon.data.codegen.query.QueryParameters;

/**
 * Specific persistence provider (e.g. Jakarta Persistence, EclipseLink native, ...) generator.
 * Defines target persistence provider API and query language.
 */
public interface PersistenceGenerator {

    /**
     * Generate persistence provider specific code.
     * There is always just one data repository provider bound to single data repository interface
     * and its implementing class.
     *
     * @param codegenContext      code processing and generation context
     * @param roundContext        codegen round context
     * @param repository          data repository interface info
     * @param repositoryGenerator specific data repository code generator
     */
    void generate(CodegenContext codegenContext,
                  RoundContext roundContext,
                  TypeInfo repository,
                  RepositoryGenerator repositoryGenerator);

    /**
     * Provider specific data query code builder.
     *
     * @param repositoryInfo {@link io.helidon.data.codegen.common.RepositoryInfo} with repository information
     * @return data query code builder
     */
    QueryBuilder queryBuilder(RepositoryInfo repositoryInfo);

    /**
     * Provider specific persistence code snippets generator.
     *
     * @return code snippets generator
     */
    StatementGenerator statementGenerator();

    // Abstract BaseQuery common ancestor is separated to be prepared for query with dynamic parts

    /**
     * Query return type.
     * Depends on statement type (query or DML) and query projection.
     */
    enum QueryReturnType {
        /**
         * Numeric value for {@code Count}, {@code Min}, {@code Max}, {@code Sum} and {@code Avg}.
         */
        NUMBER,
        /**
         * Boolean value for {@code Exists}.
         */
        BOOLEAN,
        /**
         * Entity or its attribute for entity query.
         */
        ENTITY,
        /**
         * DML statement return type (numeric value or boolean).
         */
        DML;
    }

    /**
     * Defines which query parts shall be dynamic.
     */
    enum DynamicQueryParts {

        /**
         * Criteria part of the query is dynamic.
         */
        CRITERIA,
        /**
         * Ordering part of the query is dynamic.
         */
        ORDER;

        /**
         * Length of {@link DynamicQueryParts}.
         */
        public static final int LENGTH = DynamicQueryParts.values().length;
    }

    /**
     * Base query generated code.
     */
    interface BaseQuery {

        /**
         * Generated query settings code.
         *
         * @return {@link List} of query settings code snippets
         */
        List<QuerySettings> settings();

        /**
         * Query return type as {@link QueryReturnType}.
         *
         * @return query return type
         */
        QueryReturnType returnType();

        /**
         * Whether query is DML (Data Manipulation Language).
         *
         * @return value of {@code true} when query is DML or {@code false} otherwise
         */
        boolean isDml();

    }

    /**
     * Query generated code without any dynamic parts.
     */
    interface Query extends BaseQuery {

        /**
         * Generated data query {@link String}.
         *
         * @return data query {@link String}
         */
        String query();
    }

    // Currently used in codegen code. May be also part of query with dynamic parts interface.

    /**
     * Generated query settings code snippet.
     */
    interface QuerySettings {

        /**
         * Generate the code.
         *
         * @return generated code
         */
        CharSequence code();

    }

    /**
     * Data query code builder.
     */
    interface QueryBuilder {

        /**
         * Build simple data query code from {@link DataQuery}.
         * Simple builder for query without any parameters.
         * Will fail on any criteria expression which requires at least one external parameter.
         *
         * @param query source {@link DataQuery}
         * @return query {@link String} without settings.
         */
        String buildSimpleQuery(DataQuery query);

        /**
         * Build simple provider specific {@link Query} from {@link DataQuery}.
         * Simple builder for query without any parameters.
         * Will fail on any criteria expression which requires at least one external parameter.
         *
         * @param query source {@link DataQuery}
         * @return query with settings.
         */
        Query buildQuery(DataQuery query);

        /**
         * Build provider specific {@link Query} from {@link DataQuery}.
         *
         * @param query  source {@link DataQuery}
         * @param params query parameters
         * @return query with settings.
         */
        Query buildQuery(DataQuery query, List<CharSequence> params);

        /**
         * Build provider specific COUNT {@link Query} from {@link DataQuery}.
         * Only works for query with projection action set to {@link io.helidon.data.codegen.query.ProjectionAction#Select}
         * and with no projection expression (entity or entity property is returned).
         *
         * @param query  source {@link DataQuery}
         * @param params query parameters
         * @return query with settings.
         */
        Query buildCountQuery(DataQuery query, List<CharSequence> params);

        /**
         * Build provider specific {@link Query} from query {@link String} and {@link List} of {@link MethodParameter}.
         *
         * @param query            source query {@link String}
         * @param queryParameters  query parameters
         * @param methodParameters method parameters {@link List}
         * @return query with settings
         */
        Query buildQuery(String query, QueryParameters queryParameters, List<MethodParameter> methodParameters);

        /**
         * Retrieve return type of the provided {@link DataQuery}.
         *
         * @param query query to check for return type
         * @return return type of the query
         */
        QueryReturnType queryReturntype(DataQuery query);

        /**
         * Method parameter including name alias.
         * Name alias is used to link method parameter with query named parameter.
         * Name alias must always be set. It contains name when no alias is defined.
         */
        final class MethodParameter {
            private final CharSequence name;
            private final CharSequence alias;

            private MethodParameter(CharSequence name, CharSequence alias) {
                Objects.requireNonNull(name, "Missing method parameter name");
                Objects.requireNonNull(alias, "Missing method parameter alias");
                this.name = name;
                this.alias = alias;
            }

            /**
             * Creates an instance of method parameter.
             *
             * @param name  parameter name
             * @param alias parameter alias
             * @return new instance of {@link MethodParameter}
             */
            public static MethodParameter create(CharSequence name, CharSequence alias) {
                return new MethodParameter(name, alias);
            }

            /**
             * Parameter name.
             *
             * @return the parameter name
             */
            public CharSequence name() {
                return name;
            }

            /**
             * Parameter alias.
             *
             * @return the parameter alias
             */
            public CharSequence alias() {
                return alias;
            }

        }

    }

    /**
     * Persistence code snippets generator.
     */
    interface StatementGenerator {

        /**
         * Repository executor type.
         *
         * @return the type of the executor
         */
        TypeName executorType();

        /**
         * Add code to persist single entity.
         *
         * @param builder    method builder
         * @param identifier entity identifier
         */
        void addPersist(Method.Builder builder, String identifier);

        /**
         * Add code to merge single entity.
         *
         * @param builder    method builder
         * @param identifier entity identifier
         */
        void addMerge(Method.Builder builder, String identifier);

        /**
         * Add code to persist entities {@code Collection}.
         *
         * @param builder    method builder
         * @param identifier entity identifier
         */
        void addPersistCollection(Method.Builder builder, String identifier);

        /**
         * Add code to merge entities {@code Collection}.
         *
         * @param builder    method builder
         * @param identifier entity identifier
         * @param merged     merged collection identifier
         */
        void addMergeCollection(Method.Builder builder, String identifier, String merged);

        /**
         * Add code to remove single entity.
         *
         * @param builder    method builder
         * @param identifier entity identifier
         */
        void addRemove(Method.Builder builder, String identifier);

        /**
         * Add code to remove entities {@code Collection}.
         *
         * @param builder    method builder
         * @param identifier entity identifier
         */
        void addRemoveCollection(Method.Builder builder, String identifier);

        /**
         * Add code to find entity by primary key.
         *
         * @param builder    method builder
         * @param identifier primary key identitifer
         * @param entity     entity class name
         */
        void addFind(Method.Builder builder, String identifier, TypeName entity);

        /**
         * Add code to update entity.
         *
         * @param builder    method builder
         * @param executor   executor identifier
         * @param identifier entity identifier
         * @param entity     entity class name
         */
        void addUpdate(Method.Builder builder,
                       String executor,
                       String identifier,
                       TypeName entity);

        /**
         * Add code to update entity.
         *
         * @param builder         method builder
         * @param executor        executor identifier
         * @param srcEntities     source entities collection identifier
         * @param updatedEntities updated entities collection identifier
         * @param entity          entity class name
         */
        void addUpdateAll(Method.Builder builder,
                          String executor,
                          String srcEntities,
                          String updatedEntities,
                          TypeName entity);

        /**
         * Add code to execute query on entity and return single {@code returnType} instance.
         *
         * @param builder    method builder
         * @param query      query string
         * @param returnType query result type
         */
        void addExecuteSimpleQueryItem(Method.Builder builder, String query, TypeName returnType);

        /**
         * Add code to execute query on entity and return {@link java.util.List} of entity instances.
         *
         * @param builder method builder
         * @param query   query string
         * @param entity  entity class name
         */
        void addExecuteSimpleQueryList(Method.Builder builder, String query, TypeName entity);

        /**
         * Add code to execute query on entity and return {@link java.util.stream.Stream} of entity instances.
         *
         * @param builder method builder
         * @param query   query string
         * @param entity  entity class name
         */
        void addExecuteSimpleQueryStream(Method.Builder builder, String query, TypeName entity);

        /**
         * Add code to execute query and return single {@code returnType} instance.
         *
         * @param builder    method builder
         * @param query      query generated code
         * @param returnType query result type
         */
        void addExecuteQueryItem(Method.Builder builder,
                                 PersistenceGenerator.Query query,
                                 TypeName returnType);

        /**
         * Add code to execute query and return single {@code returnType} instance.
         *
         * @param builder        method builder
         * @param repositoryInfo data repository interface info
         * @param methodInfo     method descriptor
         * @param methodParams   method parameters
         * @param dataQuery      query abstract model
         * @param returnType     query result type
         */
        void addExecuteDynamicQueryItem(Method.Builder builder,
                                        RepositoryInfo repositoryInfo,
                                        TypedElementInfo methodInfo,
                                        MethodParams methodParams,
                                        DataQuery dataQuery,
                                        TypeName returnType);

        /**
         * Add code to execute query and return single {@code returnType} instance or {@code null}.
         * Requires Jakarta Persistence 3.2.
         *
         * @param builder    method builder
         * @param query      query generated code
         * @param returnType query result type
         */
        void addExecuteQueryItemOrNull(Method.Builder builder,
                                       PersistenceGenerator.Query query,
                                       TypeName returnType);

        /**
         * Add code to execute query and return single {@code returnType} instance or {@code null}.
         * Requires Jakarta Persistence 3.2.
         *
         * @param builder        method builder
         * @param repositoryInfo data repository interface info
         * @param methodInfo     method descriptor
         * @param methodParams   method parameters
         * @param dataQuery      query abstract model
         * @param returnType     query result type
         */
        void addExecuteDynamicQueryItemOrNull(Method.Builder builder,
                                              RepositoryInfo repositoryInfo,
                                              TypedElementInfo methodInfo,
                                              MethodParams methodParams,
                                              DataQuery dataQuery,
                                              TypeName returnType);

        /**
         * Add code to execute query and return {@link java.util.List} of {@code returnType} instances.
         *
         * @param builder    method builder
         * @param query      query generated code
         * @param returnType query result type
         */
        void addExecuteQueryList(Method.Builder builder,
                                 PersistenceGenerator.Query query,
                                 TypeName returnType);

        /**
         * Add code to execute dynamic query and return {@link java.util.List} of {@code returnType} instances.
         *
         * @param builder        method builder
         * @param repositoryInfo data repository interface info
         * @param methodInfo     method descriptor
         * @param methodParams   method parameters
         * @param dataQuery      query abstract model
         * @param returnType     query result type
         */
        void addExecuteDynamicQueryList(Method.Builder builder,
                                        RepositoryInfo repositoryInfo,
                                        TypedElementInfo methodInfo,
                                        MethodParams methodParams,
                                        DataQuery dataQuery,
                                        TypeName returnType);

        /**
         * Add code to execute query and return {@link java.util.stream.Stream} of {@code returnType} instances.
         *
         * @param builder    method builder
         * @param query      query generated code
         * @param returnType query result type
         */
        void addExecuteQueryStream(Method.Builder builder,
                                   PersistenceGenerator.Query query,
                                   TypeName returnType);

        /**
         * Add code to execute dynamic query and return {@link java.util.stream.Stream} of {@code returnType} instances.
         *
         * @param builder        method builder
         * @param repositoryInfo data repository interface info
         * @param methodInfo     method descriptor
         * @param methodParams   method parameters
         * @param dataQuery      query abstract model
         * @param returnType     query result type
         */
        void addExecuteDynamicQueryStream(Method.Builder builder,
                                          RepositoryInfo repositoryInfo,
                                          TypedElementInfo methodInfo,
                                          MethodParams methodParams,
                                          DataQuery dataQuery,
                                          TypeName returnType);

        /**
         * Add query from provided query.
         *
         * @param builder    method builder
         * @param query      query generated code
         * @param returnType query result type
         */
        void addQueryItem(Method.Builder builder, PersistenceGenerator.Query query, TypeName returnType);

        /**
         * Add code to create query and return {@link java.util.List} of {@code returnType} instances
         * with pagination applied.
         *
         * @param builder     method builder
         * @param query       query generated code
         * @param returnType  query result type
         * @param firstResult position of the first result to retrieve
         * @param maxResults  maximum number of results to retrieve
         */
        void addQueryPage(Method.Builder builder,
                          PersistenceGenerator.Query query,
                          TypeName returnType,
                          String firstResult,
                          String maxResults);

        /**
         * Add code to create query and return {@link java.util.List} of {@code returnType} instances
         * with pagination applied.
         *
         * @param builder      method builder
         * @param queryContent query parameter content
         * @param settings     query settings
         * @param returnType   query result type
         * @param firstResult  position of the first result to retrieve
         * @param maxResults   maximum number of results to retrieve
         */
        void addQueryPage(Method.Builder builder,
                          Consumer<Method.Builder> queryContent,
                          List<PersistenceGenerator.QuerySettings> settings,
                          TypeName returnType,
                          String firstResult,
                          String maxResults);

        /**
         * Add {@code COUNT} query from provided query.
         *
         * @param builder method builder
         * @param query   query generated code
         */
        void addQueryCount(Method.Builder builder, PersistenceGenerator.Query query);

        /**
         * Add {@code COUNT} query from provided query.
         *
         * @param builder      method builder
         * @param queryContent query parameter content
         * @param settings     query settings
         * @param returnType   query result type
         */
        void addQueryCount(Method.Builder builder,
                           Consumer<Method.Builder> queryContent,
                           List<PersistenceGenerator.QuerySettings> settings,
                           TypeName returnType);

        // Pageable support with dynamic queries

        /**
         * Add code to create dynamic query for {@code Slice}.
         * Slice requires data query to return {@link java.util.List} of {@code returnType} instances.
         *
         * @param builder            method builder
         * @param repositoryInfo     data repository interface info
         * @param methodInfo         method descriptor
         * @param methodParams       method parameters
         * @param dataQuery          query abstract model
         * @param dataQueryStatement name of the data query statement (used in following code to create {@code Page})
         * @param returnType         query result type
         * @return settings query settings
         */
        List<PersistenceGenerator.QuerySettings> addDynamicSliceQuery(Method.Builder builder,
                                                                      RepositoryInfo repositoryInfo,
                                                                      TypedElementInfo methodInfo,
                                                                      MethodParams methodParams,
                                                                      DataQuery dataQuery,
                                                                      String dataQueryStatement,
                                                                      TypeName returnType);

        /**
         * Add code to create dynamic queries for {@code Page}.
         * Page requires data query to return {@link java.util.List} of {@code returnType} instances
         * and additional query to count size of the query result across all pages.
         *
         * @param builder             method builder
         * @param repositoryInfo      data repository interface info
         * @param methodInfo          method descriptor
         * @param methodParams        method parameters
         * @param dataQuery           query abstract model
         * @param dataQueryStatement  name of the data query statement (used in following code to create {@code Page})
         * @param countQueryStatement name of the count query statement (used in following code to create {@code Page})
         * @param returnType          query result type
         * @return settings query settings
         */
        // NEXT VERSION: Try to reduce number of parameters to remove checkstyle suppression
        @SuppressWarnings("checkstyle:ParameterNumber")
        List<PersistenceGenerator.QuerySettings> addDynamicPageQueries(Method.Builder builder,
                                                                       RepositoryInfo repositoryInfo,
                                                                       TypedElementInfo methodInfo,
                                                                       MethodParams methodParams,
                                                                       DataQuery dataQuery,
                                                                       String dataQueryStatement,
                                                                       String countQueryStatement,
                                                                       TypeName returnType);

        /**
         * Add code to execute DML statement with no parameters.
         *
         * @param builder method builder
         * @param dml     DML statement string
         */
        void addExecuteSimpleDml(Method.Builder builder, String dml);

        /**
         * Add code to execute DML statement.
         *
         * @param builder method builder
         * @param dml     DML statement string
         */
        void addExecuteDml(Method.Builder builder, PersistenceGenerator.Query dml);

        /**
         * Add code to execute dynamic DML statement.
         *
         * @param builder        method builder
         * @param repositoryInfo data repository interface info
         * @param methodInfo     method descriptor
         * @param methodParams   method parameters
         * @param dataQuery      query abstract model
         * @param returnType     query result type
         */
        void addDynamicDml(Method.Builder builder,
                           RepositoryInfo repositoryInfo,
                           TypedElementInfo methodInfo,
                           MethodParams methodParams,
                           DataQuery dataQuery,
                           TypeName returnType);

        /**
         * Add code with persistence session lambda expression.
         *
         * @param builder method builder
         * @param content additional statement content
         */
        void addSessionLambda(Method.Builder builder, Consumer<Method.Builder> content);

        /**
         * Add code with persistence session lambda block.
         *
         * @param builder method builder
         * @param content additional statement content
         */
        void addSessionLambdaBlock(Method.Builder builder, Consumer<Method.Builder> content);

    }

}
