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
package io.helidon.data.jakarta.persistence.codegen;

import java.util.List;
import java.util.function.Consumer;

import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.data.codegen.common.BaseGenerator;
import io.helidon.data.codegen.common.MethodParams;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.query.DataQuery;

final class JakartaStatementGenerator
        extends BaseGenerator implements PersistenceGenerator.StatementGenerator {

    @Override
    public TypeName executorType() {
        return JakartaPersistenceTypes.EXECUTOR;
    }

    @Override
    public void addPersist(Method.Builder builder, String identifier) {
        addEmLambda(builder, b -> addEmPersist(b, identifier));
    }

    @Override
    public void addMerge(Method.Builder builder, String identifier) {
        addEmLambda(builder, b -> addEmMerge(b, identifier));
    }

    @Override
    public void addPersistCollection(Method.Builder builder, String identifier) {
        addEmLambda(builder, b -> addEmPersistCollection(b, identifier));
    }

    @Override
    public void addMergeCollection(Method.Builder builder, String identifier, String merged) {
        addEmLambda(builder, b -> addEmMergeCollection(b, identifier, merged));
    }

    @Override
    public void addRemove(Method.Builder builder, String identifier) {
        addEmLambda(builder,
                    b1 -> addEmRemove(b1,
                                      b2 -> {
                                          b2.addContent("em.contains(")
                                                  .addContent(identifier)
                                                  .addContent(") ? ")
                                                  .addContent(identifier)
                                                  .addContent(" : ");
                                          addEmMerge(b2, identifier);
                                      }));
    }

    @Override
    public void addRemoveCollection(Method.Builder builder, String identifier) {
        addEmLambda(builder, b -> addEmRemoveCollection(b, identifier));
    }

    @Override
    public void addFind(Method.Builder builder, String identifier, TypeName entity) {
        addEmLambda(builder, b -> addEmFind(b, identifier, entity));
    }

    @Override
    public void addExecuteSimpleQueryItem(Method.Builder builder, String query, TypeName returnType) {
        addEmLambda(builder, b -> {
            addCreateQuery(builder, query, returnType);
            builder.addContentLine("");
            builder.padContent(2);
            addGetSingleResult(builder);
        });
    }

    @Override
    public void addExecuteSimpleQueryList(Method.Builder builder, String query, TypeName entity) {
        addEmLambda(builder, b -> {
            addCreateQuery(builder, query, entity);
            builder.addContentLine("");
            builder.padContent(2);
            addGetResultList(builder);
        });
    }

    @Override
    public void addExecuteSimpleQueryStream(Method.Builder builder, String query, TypeName entity) {
        addEmLambda(builder, b -> {
            addCreateQuery(builder, query, entity);
            builder.addContentLine("");
            builder.padContent(2);
            addGetResultStream(builder);
        });
    }

    @Override
    public void addExecuteQueryItem(Method.Builder builder,
                                    PersistenceGenerator.Query query,
                                    TypeName returnType) {
        addEmLambda(builder, b -> {
            addCreateQuery(builder, query.query(), returnType);
            builder.addContentLine("");
            increasePadding(builder, 2);
            addSettings(builder, query.settings());
            addGetSingleResult(builder);
            decreasePadding(builder, 2);
        });
    }

    @Override
    public void addExecuteDynamicQueryItem(Method.Builder builder,
                                           RepositoryInfo repositoryInfo,
                                           TypedElementInfo methodInfo,
                                           MethodParams methodParams,
                                           DataQuery dataQuery,
                                           TypeName returnType) {
        JakartaPersistenceCriteriaQueryGenerator criteriaQueryGenerator
                = JakartaPersistenceCriteriaQueryGenerator.create(repositoryInfo,
                                                                  methodParams,
                                                                  dataQuery,
                                                                  returnType);
        addEmLambdaBlock(builder, b -> {
            criteriaQueryGenerator.criteriaQuery(builder);
            increasePadding(builder, 2);
            addSettings(builder, criteriaQueryGenerator.settings());
            addGetSingleResult(builder);
            builder.addContentLine(";");
            decreasePadding(builder, 2);
        });
    }

    @Override
    public void addExecuteQueryItemOrNull(Method.Builder builder, PersistenceGenerator.Query query, TypeName returnType) {
        addEmLambda(builder, b -> {
            addCreateQuery(builder, query.query(), returnType);
            builder.addContentLine("");
            increasePadding(builder, 2);
            addSettings(builder, query.settings());
            addGetSingleResultOrNull(builder);
            decreasePadding(builder, 2);
        });
    }

    @Override
    public void addExecuteDynamicQueryItemOrNull(Method.Builder builder,
                                                 RepositoryInfo repositoryInfo,
                                                 TypedElementInfo methodInfo,
                                                 MethodParams methodParams,
                                                 DataQuery dataQuery,
                                                 TypeName returnType) {
        JakartaPersistenceCriteriaQueryGenerator criteriaQueryGenerator
                = JakartaPersistenceCriteriaQueryGenerator.create(repositoryInfo,
                                                                  methodParams,
                                                                  dataQuery,
                                                                  returnType);
        addEmLambdaBlock(builder, b -> {
            criteriaQueryGenerator.criteriaQuery(builder);
            increasePadding(builder, 2);
            addSettings(builder, criteriaQueryGenerator.settings());
            addGetSingleResultOrNull(builder);
            builder.addContentLine(";");
            decreasePadding(builder, 2);
        });
    }

    @Override
    public void addExecuteQueryList(Method.Builder builder, PersistenceGenerator.Query query, TypeName returnType) {
        addEmLambda(builder, b -> {
            addCreateQuery(builder, query.query(), returnType);
            builder.addContentLine("");
            increasePadding(builder, 2);
            addSettings(builder, query.settings());
            addGetResultList(builder);
            decreasePadding(builder, 2);
        });
    }

    @Override
    public void addExecuteQueryStream(Method.Builder builder, PersistenceGenerator.Query query, TypeName returnType) {
        addEmLambda(builder, b -> {
            addCreateQuery(builder, query.query(), returnType);
            builder.addContentLine("");
            increasePadding(builder, 2);
            addSettings(builder, query.settings());
            addGetResultStream(builder);
            decreasePadding(builder, 2);
        });
    }

    @Override
    public void addQueryItem(Method.Builder builder, PersistenceGenerator.Query query, TypeName returnType) {
        addCreateQuery(builder, query.query(), returnType);
        builder.addContentLine("");
        increasePadding(builder, 2);
        addSettings(builder, query.settings());
        addGetSingleResult(builder);
        decreasePadding(builder, 2);
    }

    @Override
    public void addQueryPage(Method.Builder builder,
                             PersistenceGenerator.Query query,
                             TypeName returnType,
                             String firstResult,
                             String maxResults) {
        addCreateQuery(builder, query.query(), returnType);
        builder.addContentLine("");
        increasePadding(builder, 2);
        addSettings(builder, query.settings());
        addFirstResult(builder, firstResult);
        addMaxResults(builder, maxResults);
        addGetResultList(builder);
        decreasePadding(builder, 2);
    }

    @Override
    public void addQueryPage(Method.Builder builder,
                             Consumer<Method.Builder> queryContent,
                             List<PersistenceGenerator.QuerySettings> settings,
                             TypeName returnType,
                             String firstResult,
                             String maxResults) {
        addCreateQuery(builder, queryContent, returnType);
        builder.addContentLine("");
        increasePadding(builder, 2);
        addSettings(builder, settings);
        addFirstResult(builder, firstResult);
        addMaxResults(builder, maxResults);
        addGetResultList(builder);
        decreasePadding(builder, 2);
    }

    @Override
    public void addQueryCount(Method.Builder builder, PersistenceGenerator.Query query) {
        addCreateQuery(builder,
                       "SELECT COUNT(*) FROM (" + query.query() + ")",
                       BaseGenerator.NUMBER);
        builder.addContentLine("");
        increasePadding(builder, 2);
        addSettings(builder, query.settings());
        addGetSingleResult(builder);
        builder.addContentLine("")
                .addContent(".intValue()");
        decreasePadding(builder, 2);
    }

    @Override
    public void addQueryCount(Method.Builder builder,
                              Consumer<Method.Builder> queryContent,
                              List<PersistenceGenerator.QuerySettings> settings,
                              TypeName returnType) {
        addCreateQuery(builder, queryContent, returnType);
        builder.addContentLine("");
        increasePadding(builder, 2);
        addSettings(builder, settings);
        addGetSingleResult(builder);
        builder.addContentLine("")
                .addContent(".intValue()");
        decreasePadding(builder, 2);
    }

    @Override
    public List<PersistenceGenerator.QuerySettings> addDynamicSliceQuery(Method.Builder builder,
                                                                         RepositoryInfo repositoryInfo,
                                                                         TypedElementInfo methodInfo,
                                                                         MethodParams methodParams,
                                                                         DataQuery dataQuery,
                                                                         String dataQueryStatement,
                                                                         TypeName returnType) {
        JakartaPersistenceCriteriaQueryGenerator criteriaQueryGenerator
                = JakartaPersistenceCriteriaQueryGenerator.create(repositoryInfo,
                                                                  methodParams,
                                                                  dataQuery,
                                                                  returnType);
        return criteriaQueryGenerator.dynamicSliceQuery(builder, dataQueryStatement);
    }

    @Override
    public List<PersistenceGenerator.QuerySettings> addDynamicPageQueries(Method.Builder builder,
                                                                          RepositoryInfo repositoryInfo,
                                                                          TypedElementInfo methodInfo,
                                                                          MethodParams methodParams,
                                                                          DataQuery dataQuery,
                                                                          String dataQueryStatement,
                                                                          String countQueryStatement,
                                                                          TypeName returnType) {
        JakartaPersistenceCriteriaQueryGenerator criteriaQueryGenerator
                = JakartaPersistenceCriteriaQueryGenerator.create(repositoryInfo,
                                                                  methodParams,
                                                                  dataQuery,
                                                                  returnType);
        return criteriaQueryGenerator.dynamicPageQueries(builder,
                                                         dataQueryStatement,
                                                         countQueryStatement);
    }

    @Override
    public void addExecuteSimpleDml(Method.Builder builder, String dml) {
        addEmLambda(builder, b -> {
            addCreateQuery(builder, dml);
            builder.addContentLine("");
            increasePadding(builder, 2);
            addExecuteUpdate(builder);
            decreasePadding(builder, 2);
        });
    }

    @Override
    public void addExecuteDml(Method.Builder builder, PersistenceGenerator.Query dml) {
        addEmLambda(builder, b -> {
            addCreateQuery(builder, dml.query());
            builder.addContentLine("");
            increasePadding(builder, 2);
            addSettings(builder, dml.settings());
            addExecuteUpdate(builder);
            decreasePadding(builder, 2);
        });
    }

    @Override
    public void addExecuteDynamicQueryList(Method.Builder builder,
                                           RepositoryInfo repositoryInfo,
                                           TypedElementInfo methodInfo,
                                           MethodParams methodParams,
                                           DataQuery dataQuery,
                                           TypeName returnType) {
        JakartaPersistenceCriteriaQueryGenerator criteriaQueryGenerator
                = JakartaPersistenceCriteriaQueryGenerator.create(repositoryInfo,
                                                                  methodParams,
                                                                  dataQuery,
                                                                  returnType);
        addEmLambdaBlock(builder, b -> {
            criteriaQueryGenerator.criteriaQuery(builder);
            increasePadding(builder, 2);
            addSettings(builder, criteriaQueryGenerator.settings());
            addGetResultList(builder);
            builder.addContentLine(";");
            decreasePadding(builder, 2);
        });

    }

    @Override
    public void addExecuteDynamicQueryStream(Method.Builder builder,
                                             RepositoryInfo repositoryInfo,
                                             TypedElementInfo methodInfo,
                                             MethodParams methodParams,
                                             DataQuery dataQuery,
                                             TypeName returnType) {
        JakartaPersistenceCriteriaQueryGenerator criteriaQueryGenerator
                = JakartaPersistenceCriteriaQueryGenerator.create(repositoryInfo,
                                                                  methodParams,
                                                                  dataQuery,
                                                                  returnType);
        addEmLambdaBlock(builder, b -> {
            criteriaQueryGenerator.criteriaQuery(builder);
            increasePadding(builder, 2);
            addSettings(builder, criteriaQueryGenerator.settings());
            addGetResultStream(builder);
            builder.addContentLine(";");
            decreasePadding(builder, 2);
        });

    }

    @Override
    public void addDynamicDml(Method.Builder builder,
                              RepositoryInfo repositoryInfo,
                              TypedElementInfo methodInfo,
                              MethodParams methodParams,
                              DataQuery dataQuery,
                              TypeName returnType) {
        JakartaPersistenceCriteriaQueryGenerator criteriaQueryGenerator
                = JakartaPersistenceCriteriaQueryGenerator.create(repositoryInfo,
                                                                  methodParams,
                                                                  dataQuery,
                                                                  returnType);
        addEmLambdaBlock(builder, b -> {
            criteriaQueryGenerator.criteriaQuery(builder);
            increasePadding(builder, 2);
            addSettings(builder, criteriaQueryGenerator.settings());
            addExecuteUpdate(builder);
            builder.addContentLine(";");
            decreasePadding(builder, 2);
        });
    }

    @Override
    public void addSessionLambda(Method.Builder builder, Consumer<Method.Builder> content) {
        addEmLambda(builder, content);
    }

    @Override
    public void addSessionLambdaBlock(Method.Builder builder, Consumer<Method.Builder> content) {
        addEmLambdaBlock(builder, content);
    }

    private static void addCreateQuery(Method.Builder builder, String query, TypeName returnType) {
        builder.addContent("em.createQuery(\"")
                .addContent(query)
                .addContent("\"");
        if (returnType != null) {
            builder.addContent(", ")
                    .addContent(returnType)
                    .addContent(".class");
        }
        builder.addContent(")");
    }

    private static void addCreateQuery(Method.Builder builder, String query) {
        addCreateQuery(builder, query, null);
    }

    private static void addCreateQuery(Method.Builder builder, Consumer<Method.Builder> queryContent, TypeName returnType) {
        builder.addContent("em.createQuery(");
        queryContent.accept(builder);
        if (returnType != null) {
            builder.addContent(", ")
                    .addContent(returnType)
                    .addContent(".class");
        }
        builder.addContent(")");
    }

    private static void addSettings(Method.Builder builder, List<PersistenceGenerator.QuerySettings> settings) {
        settings.forEach(setting -> builder.addContent(".")
                .addContentLine(setting.code().toString()));
    }

    private static void addFirstResult(Method.Builder builder, String firstResult) {
        builder.addContent(".setFirstResult(")
                .addContent(firstResult)
                .addContentLine(")");
    }

    private static void addMaxResults(Method.Builder builder, String maxResults) {
        builder.addContent(".setMaxResults(")
                .addContent(maxResults)
                .addContentLine(")");
    }

    private static void addGetSingleResult(Method.Builder builder) {
        builder.addContent(".getSingleResult()");
    }

    private static void addGetSingleResultOrNull(Method.Builder builder) {
        builder.addContent(".getSingleResultOrNull()");
    }

    private static void addGetResultList(Method.Builder builder) {
        builder.addContent(".getResultList()");
    }

    private static void addGetResultStream(Method.Builder builder) {
        // Using ".getResultStream()" only works with EclipseLink. Causes IllegalStateException with Hibernate
        // So this less effective way of returning stream must be used.
        builder.addContentLine(".getResultList().stream()");
    }

    private static void addExecuteUpdate(Method.Builder builder) {
        builder.addContent(".executeUpdate()");
    }

    private static void addEmLambda(Method.Builder builder, Consumer<Method.Builder> content) {
        builder.addContent("em -> ");
        content.accept(builder);
    }

    private static void addEmLambdaBlock(Method.Builder builder, Consumer<Method.Builder> content) {
        builder.addContentLine("em -> {");
        increasePadding(builder, 1);
        content.accept(builder);
        decreasePadding(builder, 1);
        builder.addContent("}");
    }

    private static void addEmPersist(Method.Builder builder, String identifier) {
        builder.addContent("em.persist(")
                .addContent(identifier)
                .addContent(")");
    }

    private static void addEmMerge(Method.Builder builder, String identifier) {
        builder.addContent("em.merge(")
                .addContent(identifier)
                .addContent(")");
    }

    private static void addEmPersistCollection(Method.Builder builder, String identifier) {
        builder.addContent(identifier)
                .addContent(".forEach(em::persist)");
    }

    private static void addEmMergeCollection(Method.Builder builder, String identifier, String merged) {
        builder.addContent(identifier)
                .addContent(".forEach(e -> ")
                .addContent(merged)
                .addContent(".add(em.merge(e)))");
    }

    private static void addEmRemove(Method.Builder builder, Consumer<Method.Builder> content) {
        builder.addContent("em.remove(");
        content.accept(builder);
        builder.addContent(")");
    }

    private static void addEmRemoveCollection(Method.Builder builder, String identifier) {
        builder.addContent(identifier)
                .addContent(".forEach(e -> ");
        addEmRemove(builder,
                    b -> {
                        b.addContent("em.contains(")
                                .addContent("e")
                                .addContent(") ? ")
                                .addContent("e")
                                .addContent(" : ");
                        addEmMerge(b, "e");
                    });
        builder.addContent(")");
    }

    private static void addEmFind(Method.Builder builder, String identifier, TypeName entity) {
        builder.addContent("em.find(")
                .addContent(entity)
                .addContent(".class, ")
                .addContent(identifier)
                .addContent(")");
    }

}
