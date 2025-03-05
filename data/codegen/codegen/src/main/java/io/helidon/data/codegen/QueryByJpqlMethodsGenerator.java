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

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.data.codegen.common.MethodParams;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.parser.QueryParametersParser;

import static io.helidon.data.codegen.HelidonDataTypes.PAGE;
import static io.helidon.data.codegen.HelidonDataTypes.SLICE;

class QueryByJpqlMethodsGenerator extends BaseQueryMethodsGenerator {

    private final QueryParametersParser parser;
    private final List<TypedElementInfo> methods;

    private QueryByJpqlMethodsGenerator(RepositoryInfo repositoryInfo,
                                        List<TypedElementInfo> methods,
                                        ClassModel.Builder classModel,
                                        CodegenContext codegenContext,
                                        PersistenceGenerator persistenceGenerator) {
        super(repositoryInfo, classModel, codegenContext, persistenceGenerator);
        this.parser = QueryParametersParser.create();
        this.methods = methods;
    }

    // Generate all "query by method name" methods
    static void generate(RepositoryInfo repositoryInfo,
                         List<TypedElementInfo> methods,
                         ClassModel.Builder classModel,
                         CodegenContext codegenContext,
                         PersistenceGenerator persistenceGenerator) {
        QueryByJpqlMethodsGenerator generator = new QueryByJpqlMethodsGenerator(repositoryInfo,
                                                                                methods,
                                                                                classModel,
                                                                                codegenContext,
                                                                                persistenceGenerator);
        generator.generate();
    }

    // Generate all query methods
    @Override
    public void generate() {
        methods.forEach(this::addGeneratedMethod);
    }

    private static void generateQueryList(Method.Builder builder,
                                          TypedElementInfo methodInfo,
                                          PersistenceGenerator.StatementGenerator statementGenerator,
                                          PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator.addExecuteQueryList(b2,
                                                                                query,
                                                                                genericReturnTypeArgument(methodInfo)),
                                   EXECUTOR));
    }

    private static void generateQueryStream(Method.Builder builder,
                                            TypedElementInfo methodInfo,
                                            PersistenceGenerator.StatementGenerator statementGenerator,
                                            PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator.addExecuteQueryStream(b2,
                                                                                  query,
                                                                                  genericReturnTypeArgument(methodInfo)),
                                   EXECUTOR));
    }

    private static void generateQuerySlice(Method.Builder builder,
                                           TypedElementInfo methodInfo,
                                           PersistenceGenerator.StatementGenerator statementGenerator,
                                           PersistenceGenerator.Query query,
                                           MethodParams methodParams) {
        if (methodParams.pageRequest().isEmpty()) {
            throw new IllegalArgumentException("Method " + methodInfo.elementName()
                                                       + " returns " + query.returnType()
                                                       + ", but PageRequest parameter is missing");
        }
        TypedElementInfo pageRequest = methodParams.pageRequest().get();
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator.addSessionLambda(b2, b3 -> {
                                       b3.addContent(SLICE)
                                               .addContentLine(".create(");
                                       increasePadding(b3, 2);
                                       b3.addContent(pageRequest.elementName())
                                               .addContentLine(",");
                                       statementGenerator.addQueryPage(b3,
                                                                       query,
                                                                       genericReturnTypeArgument(methodInfo),
                                                                       pageRequest.elementName() + ".offset()",
                                                                       pageRequest.elementName() + ".size()");
                                       b3.addContent(")");
                                       decreasePadding(b3, 2);
                                   }),
                                   EXECUTOR));
    }

    private static void generateQueryPage(Method.Builder builder,
                                          TypedElementInfo methodInfo,
                                          PersistenceGenerator.StatementGenerator statementGenerator,
                                          PersistenceGenerator.Query query,
                                          MethodParams methodParams) {
        if (methodParams.pageRequest().isEmpty()) {
            throw new IllegalArgumentException("Method " + methodInfo.elementName()
                                                       + " returns " + query.returnType()
                                                       + ", but PageRequest parameter is missing");
        }
        TypedElementInfo pageRequest = methodParams.pageRequest().get();
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator.addSessionLambda(b2, b3 -> {
                                       b3.addContent(PAGE)
                                               .addContentLine(".create(");
                                       increasePadding(b3, 2);
                                       b3.addContent(pageRequest.elementName())
                                               .addContentLine(",");
                                       statementGenerator.addQueryPage(b3,
                                                                       query,
                                                                       genericReturnTypeArgument(methodInfo),
                                                                       pageRequest.elementName() + ".offset()",
                                                                       pageRequest.elementName() + ".size()");
                                       b3.addContentLine(",");
                                       statementGenerator.addQueryCount(b3, query);
                                       b3.addContent(")");
                                       decreasePadding(b3, 2);
                                   }),
                                   EXECUTOR));
    }

    // Add generated method to the model
    private void addGeneratedMethod(TypedElementInfo methodInfo) {
        try {
            classModel().addMethod(builder -> generateMethod(builder, methodInfo));
        } catch (CodegenException e) {
            throw e;
        } catch (Exception e) {
            String message = e.getMessage() != null && !e.getMessage().isEmpty()
                    ? e.getMessage()
                    : "Code generation of @Data.Query annotated method failed";
            throw new CodegenException(message, e, methodInfo.originatingElement());
        }
    }

    // Generate method code
    private void generateMethod(Method.Builder builder, TypedElementInfo methodInfo) {
        MethodParams methodParams = generateHeader(builder, methodInfo);

        codegenContext().logger()
                .log(Level.TRACE, String.format("JPQL method %s", methodInfo.elementName()));

        if (methodParams.dynamic()) {
            throw new IllegalArgumentException("Method " + methodInfo.elementName()
                                                       + " contains dynamic query parameter of type Sort");
        }

        // Query string from @Query annotation
        String jpql = methodInfo.annotation(HelidonDataTypes.QUERY_ANNOTATION)
                .value()
                .orElseThrow(() -> new CodegenException("@Data.Query annotation value is missing",
                                                        methodInfo.originatingElement()));

        PersistenceGenerator.Query query = queryBuilder().buildQuery(jpql,
                                                                     parser.parse(jpql),
                                                                     params(methodParams));
        if (STREAM.equals(methodInfo.typeName())) {
            generateQueryStream(builder, methodInfo, statementGenerator(), query);
        } else if (isListOrCollection(methodInfo.typeName())) {
            generateQueryList(builder, methodInfo, statementGenerator(), query);
        } else if (methodInfo.typeName().isOptional()) {
            generateQueryOptional(builder, methodInfo, query);
        } else if (SLICE.equals(methodInfo.typeName())) {
            generateQuerySlice(builder, methodInfo, statementGenerator(), query, methodParams);
        } else if (PAGE.equals(methodInfo.typeName())) {
            generateQueryPage(builder, methodInfo, statementGenerator(), query, methodParams);
        } else {
            generateQueryItem(builder, query, methodInfo.typeName());
        }
    }

    private void generateQueryOptional(Method.Builder builder,
                                       TypedElementInfo methodInfo,
                                       PersistenceGenerator.Query query) {
        returnStatement(
                builder,
                // Jakarta Persistence 3.2 compliant code disabled
                // b1 -> optionalOfNullable(b1,
                // Jakarta Persistence 3.1 workaround
                b1 -> optionalFromQuery(
                        b1,
                        b2 -> call(
                                b2,
                                // Jakarta Persistence 3.2 compliant code disabled
                                // b3 -> statementGenerator().addExecuteQueryItemOrNull(b3,
                                // Jakarta Persistence 3.1 workaround
                                b3 -> statementGenerator().addExecuteQueryList(
                                        b3,
                                        query,
                                        genericReturnTypeArgument(
                                                methodInfo)),
                                EXECUTOR),
                        statementGenerator().executorType()));
    }

    private void generateQueryItem(Method.Builder builder,
                                   PersistenceGenerator.Query query,
                                   TypeName resultType) {
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator().addExecuteQueryItem(b2,
                                                                                  query,
                                                                                  resultType),
                                   EXECUTOR));

    }

    /**
     * Create {@link List} of {@link PersistenceGenerator.QueryBuilder.MethodParameter} from method parameters
     * {@link List} of {@link TypedElementInfo}.
     * Returned list contains information about optional parameter aliases to link parameter
     * with query named parameter.
     *
     * @param methodParams method parameters
     * @return {@link List} of {@link PersistenceGenerator.QueryBuilder.MethodParameter}
     */
    private List<PersistenceGenerator.QueryBuilder.MethodParameter> params(MethodParams methodParams) {
        List<PersistenceGenerator.QueryBuilder.MethodParameter> params = new ArrayList<>(methodParams.params().size());
        methodParams.params()
                .forEach(param -> params.add(
                        PersistenceGenerator.QueryBuilder.MethodParameter.create(param.elementName(),
                                                                                 param.elementName())));
        return List.copyOf(params);
    }

}
