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
package io.helidon.data.codegen;

import java.util.function.Consumer;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;
import io.helidon.data.codegen.common.BaseRepositoryInterfaceGenerator;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.query.DataQuery;
import io.helidon.data.codegen.query.Projection;

import static io.helidon.data.codegen.HelidonDataTypes.DATA_QUERY;
import static io.helidon.data.codegen.HelidonDataTypes.PAGE;
import static io.helidon.data.codegen.HelidonDataTypes.SLICE;

class PageableRepositoryInterfaceGenerator extends BaseRepositoryInterfaceGenerator {
    /** Method parameter: PageRequest pageRequest. */
    protected static final String PAGE_REQUEST_PARAM_NAME = "pageRequest";
    protected static final Parameter PAGE_REQUEST_PARAM = Parameter.builder()
            .name(PAGE_REQUEST_PARAM_NAME)
            .type(HelidonDataTypes.PAGE_REQUEST)
            .build();

    PageableRepositoryInterfaceGenerator(RepositoryInfo repositoryInfo,
                                         ClassModel.Builder classModel,
                                         CodegenContext codegenContext,
                                         PersistenceGenerator persistenceGenerator) {
        super(repositoryInfo, classModel, codegenContext, persistenceGenerator);
    }

    @Override
    public void generate() {
        classModel()
                .addMethod(this::generateFindAllPages)
                .addMethod(this::generateFindAllSlices);
    }

    // DataQuery <identifier> = DataQuery.builder()
    //              <content>
    //              .build()
    private static void buildDataQuery(Method.Builder builder,
                                       Consumer<Method.Builder> content,
                                       String identifier) {
        builder.addContent(DATA_QUERY)
                .addContent(" ")
                .addContent(identifier)
                .addContent(" = ")
                .addContent(DATA_QUERY)
                .addContentLine(".builder()");
        increasePadding(builder, 2);
        content.accept(builder);
        builder.addContentLine("")
                .addContent(".build()");
        decreasePadding(builder, 2);
    }

    // Page<E> findAllPages(PageRequest pageable)
    private void generateFindAllPages(Method.Builder builder) {
        TypeName returnType = TypeName.builder()
                .from(PAGE)
                .addTypeArgument(repositoryInfo().entity())
                .build();
        PersistenceGenerator.Query query = queryBuilder().buildQuery(
                DataQuery.builder()
                        .projection(Projection.select())
                        .build());
        PersistenceGenerator.Query countQuery = queryBuilder().buildQuery(
                DataQuery.builder()
                        .projection(Projection.selectCount())
                        .build());
        // Method prototype
        builder.name("pages")
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(PAGE_REQUEST_PARAM)
                .returnType(returnType);
        // Method body (Jakarta Persistence example)
        //        return executor.call(em -> Page.create(
        //                pageRequest,
        //                em.createQuery("SELECT k FROM Kind k", Kind.class)
        //                        .setFirstResult((int) pageRequest.offset())
        //                        .setMaxResults(pageRequest.size())
        //                        .getResultList(),
        //                em.createQuery("SELECT COUNT(k) FROM Kind k", Long.class)
        //                        .getSingleResult()));
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator().addSessionLambda(b2, b3 -> {
                                       b3.addContent(PAGE)
                                               .addContentLine(".create(");
                                       increasePadding(b3, 2);
                                       b3.addContent(PAGE_REQUEST_PARAM_NAME)
                                               .addContentLine(",");
                                       statementGenerator().addQueryPage(b3,
                                                                         query,
                                                                         repositoryInfo().entity(),
                                                                         PAGE_REQUEST_PARAM_NAME + ".offset()",
                                                                         PAGE_REQUEST_PARAM_NAME + ".size()");
                                       b3.addContentLine(",");
                                       // Specific number type is unpredictable here.
                                       // Using common Number cast and .intValue() to make code safe.
                                       statementGenerator().addQueryItem(b3, countQuery, NUMBER);
                                       b3.addContentLine("")
                                               .padContent(2)
                                               .addContent(".intValue()");
                                       b3.addContent(")");
                                       decreasePadding(b3, 2);
                                   }),
                                   EXECUTOR));
    }

    // Slice<E> findAllSlices(PageRequest pageable)
    private void generateFindAllSlices(Method.Builder builder) {
        TypeName returnType = TypeName.builder()
                .from(SLICE)
                .addTypeArgument(repositoryInfo().entity())
                .build();
        PersistenceGenerator.Query query = queryBuilder().buildQuery(
                DataQuery.builder()
                        .projection(Projection.select())
                        .build());
        // Method prototype
        builder.name("slices")
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(PAGE_REQUEST_PARAM)
                .returnType(returnType);
        // Method body (Jakarta Persistence example)
        //        return executor.call(em -> Slice.create(
        //                pageRequest,
        //                em.createQuery("SELECT k FROM Kind k", Kind.class)
        //                        .setFirstResult((int) pageRequest.offset())
        //                        .setMaxResults(pageRequest.size())
        //                        .getResultList()));
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator().addSessionLambda(b2, b3 -> {
                                       b3.addContent(SLICE)
                                               .addContentLine(".create(");
                                       increasePadding(b3, 2);
                                       b3.addContent(PAGE_REQUEST_PARAM_NAME)
                                               .addContentLine(",");
                                       statementGenerator().addQueryPage(b3,
                                                                         query,
                                                                         repositoryInfo().entity(),
                                                                         PAGE_REQUEST_PARAM_NAME + ".offset()",
                                                                         PAGE_REQUEST_PARAM_NAME + ".size()");
                                       b3.addContent(")");
                                       decreasePadding(b3, 2);
                                   }),
                                   EXECUTOR));
    }

}
