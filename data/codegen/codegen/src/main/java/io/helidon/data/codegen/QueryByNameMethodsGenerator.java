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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.data.codegen.common.BaseRepositoryInterfaceGenerator;
import io.helidon.data.codegen.common.MethodParams;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.common.spi.PersistenceGenerator.QueryReturnType;
import io.helidon.data.codegen.parser.MethodNameParser;
import io.helidon.data.codegen.query.DataQuery;

import static io.helidon.data.codegen.HelidonDataTypes.PAGE;
import static io.helidon.data.codegen.HelidonDataTypes.SLICE;

/**
 * Helidon Data <i>Query by method name</i> methods generator.
 */
class QueryByNameMethodsGenerator extends BaseQueryMethodsGenerator {

    private static final Map<TypeName, Generator> NUMBER_RESULT_GENERATORS;
    private static final Map<TypeName, Generator> BOOLEAN_RESULT_GENERATORS;
    private static final Map<TypeName, Generator> DML_GENERATORS;
    private static final Map<TypeName, DynamicGenerator> DYNAMIC_NUMBER_RESULT_GENERATORS;
    private static final Map<TypeName, DynamicGenerator> DYNAMIC_BOOLEAN_RESULT_GENERATORS;
    private static final Map<TypeName, DynamicGenerator> DYNAMIC_DML_GENERATORS;

    static {
        Map<TypeName, Generator> generators = new HashMap<>();
        generators.put(NUMBER, QueryByNameMethodsGenerator::generateNumber);
        generators.put(TypeNames.PRIMITIVE_LONG, QueryByNameMethodsGenerator::generateLong);
        generators.put(TypeNames.BOXED_LONG, QueryByNameMethodsGenerator::generateLong);
        generators.put(TypeNames.PRIMITIVE_INT, QueryByNameMethodsGenerator::generateInt);
        generators.put(TypeNames.BOXED_INT, QueryByNameMethodsGenerator::generateInt);
        generators.put(TypeNames.PRIMITIVE_SHORT, QueryByNameMethodsGenerator::generateShort);
        generators.put(TypeNames.BOXED_SHORT, QueryByNameMethodsGenerator::generateShort);
        generators.put(TypeNames.PRIMITIVE_BYTE, QueryByNameMethodsGenerator::generateByte);
        generators.put(TypeNames.BOXED_BYTE, QueryByNameMethodsGenerator::generateByte);
        generators.put(TypeNames.PRIMITIVE_FLOAT, QueryByNameMethodsGenerator::generateFloat);
        generators.put(TypeNames.BOXED_FLOAT, QueryByNameMethodsGenerator::generateFloat);
        generators.put(TypeNames.PRIMITIVE_DOUBLE, QueryByNameMethodsGenerator::generateDouble);
        generators.put(TypeNames.BOXED_DOUBLE, QueryByNameMethodsGenerator::generateDouble);
        generators.put(BIG_INTEGER, QueryByNameMethodsGenerator::generateBigInteger);
        generators.put(BIG_DECIMAL, QueryByNameMethodsGenerator::generateBigDecimal);
        NUMBER_RESULT_GENERATORS = Map.copyOf(generators);
    }

    static {
        Map<TypeName, Generator> generators = new HashMap<>();
        generators.put(TypeNames.PRIMITIVE_BOOLEAN, QueryByNameMethodsGenerator::generateBoolean);
        generators.put(TypeNames.BOXED_BOOLEAN, QueryByNameMethodsGenerator::generateBoolean);
        BOOLEAN_RESULT_GENERATORS = Map.copyOf(generators);
    }

    static {
        Map<TypeName, Generator> generators = new HashMap<>();
        generators.put(TypeNames.PRIMITIVE_VOID, QueryByNameMethodsGenerator::generateDmlVoid);
        generators.put(TypeNames.BOXED_VOID, QueryByNameMethodsGenerator::generateDmlBoxedVoid);
        generators.put(TypeNames.PRIMITIVE_BOOLEAN, QueryByNameMethodsGenerator::generateDmlBoolean);
        generators.put(TypeNames.BOXED_BOOLEAN, QueryByNameMethodsGenerator::generateDmlBoolean);
        generators.put(TypeNames.PRIMITIVE_LONG, QueryByNameMethodsGenerator::generateDmlLong);
        generators.put(TypeNames.BOXED_LONG, QueryByNameMethodsGenerator::generateDmlLong);
        generators.put(TypeNames.PRIMITIVE_INT, QueryByNameMethodsGenerator::generateDmlInt);
        generators.put(TypeNames.BOXED_INT, QueryByNameMethodsGenerator::generateDmlInt);
        generators.put(TypeNames.PRIMITIVE_SHORT, QueryByNameMethodsGenerator::generateDmlShort);
        generators.put(TypeNames.BOXED_SHORT, QueryByNameMethodsGenerator::generateDmlShort);
        generators.put(TypeNames.PRIMITIVE_BYTE, QueryByNameMethodsGenerator::generateDmlByte);
        generators.put(TypeNames.BOXED_BYTE, QueryByNameMethodsGenerator::generateDmlByte);
        generators.put(TypeNames.PRIMITIVE_FLOAT, QueryByNameMethodsGenerator::generateDmlFloat);
        generators.put(TypeNames.BOXED_FLOAT, QueryByNameMethodsGenerator::generateDmlFloat);
        generators.put(TypeNames.PRIMITIVE_DOUBLE, QueryByNameMethodsGenerator::generateDmlDouble);
        generators.put(TypeNames.BOXED_DOUBLE, QueryByNameMethodsGenerator::generateDmlDouble);
        generators.put(BIG_INTEGER, QueryByNameMethodsGenerator::generateDmlBigInteger);
        generators.put(BIG_DECIMAL, QueryByNameMethodsGenerator::generateDmlBigDecimal);
        DML_GENERATORS = Map.copyOf(generators);
    }

    static {
        Map<TypeName, DynamicGenerator> generators = new HashMap<>();
        generators.put(NUMBER, QueryByNameMethodsGenerator::generateDynamicNumber);
        generators.put(TypeNames.PRIMITIVE_LONG, QueryByNameMethodsGenerator::generateDynamicLong);
        generators.put(TypeNames.BOXED_LONG, QueryByNameMethodsGenerator::generateDynamicLong);
        generators.put(TypeNames.PRIMITIVE_INT, QueryByNameMethodsGenerator::generateDynamicInt);
        generators.put(TypeNames.BOXED_INT, QueryByNameMethodsGenerator::generateDynamicInt);
        generators.put(TypeNames.PRIMITIVE_SHORT, QueryByNameMethodsGenerator::generateDynamicShort);
        generators.put(TypeNames.BOXED_SHORT, QueryByNameMethodsGenerator::generateDynamicShort);
        generators.put(TypeNames.PRIMITIVE_BYTE, QueryByNameMethodsGenerator::generateDynamicByte);
        generators.put(TypeNames.BOXED_BYTE, QueryByNameMethodsGenerator::generateDynamicByte);
        generators.put(TypeNames.PRIMITIVE_FLOAT, QueryByNameMethodsGenerator::generateDynamicFloat);
        generators.put(TypeNames.BOXED_FLOAT, QueryByNameMethodsGenerator::generateDynamicFloat);
        generators.put(TypeNames.PRIMITIVE_DOUBLE, QueryByNameMethodsGenerator::generateDynamicDouble);
        generators.put(TypeNames.BOXED_DOUBLE, QueryByNameMethodsGenerator::generateDynamicDouble);
        generators.put(BIG_INTEGER, QueryByNameMethodsGenerator::generateDynamicBigInteger);
        generators.put(BIG_DECIMAL, QueryByNameMethodsGenerator::generateDynamicBigDecimal);
        DYNAMIC_NUMBER_RESULT_GENERATORS = Map.copyOf(generators);
    }

    static {
        Map<TypeName, DynamicGenerator> generators = new HashMap<>();
        generators.put(TypeNames.PRIMITIVE_BOOLEAN, QueryByNameMethodsGenerator::generateDynamicBoolean);
        generators.put(TypeNames.BOXED_BOOLEAN, QueryByNameMethodsGenerator::generateDynamicBoolean);
        DYNAMIC_BOOLEAN_RESULT_GENERATORS = Map.copyOf(generators);
    }

    static {
        Map<TypeName, DynamicGenerator> generators = new HashMap<>();
        generators.put(TypeNames.PRIMITIVE_VOID, QueryByNameMethodsGenerator::generateDynamicDmlVoid);
        generators.put(TypeNames.BOXED_VOID, QueryByNameMethodsGenerator::generateDynamicDmlBoxedVoid);
        generators.put(TypeNames.PRIMITIVE_BOOLEAN, QueryByNameMethodsGenerator::generateDynamicDmlBoolean);
        generators.put(TypeNames.BOXED_BOOLEAN, QueryByNameMethodsGenerator::generateDynamicDmlBoolean);
        generators.put(TypeNames.PRIMITIVE_LONG, QueryByNameMethodsGenerator::generateDynamicDmlLong);
        generators.put(TypeNames.BOXED_LONG, QueryByNameMethodsGenerator::generateDynamicDmlLong);
        generators.put(TypeNames.PRIMITIVE_INT, QueryByNameMethodsGenerator::generateDynamicDmlInt);
        generators.put(TypeNames.BOXED_INT, QueryByNameMethodsGenerator::generateDynamicDmlInt);
        generators.put(TypeNames.PRIMITIVE_SHORT, QueryByNameMethodsGenerator::generateDynamicDmlShort);
        generators.put(TypeNames.BOXED_SHORT, QueryByNameMethodsGenerator::generateDynamicDmlShort);
        generators.put(TypeNames.PRIMITIVE_BYTE, QueryByNameMethodsGenerator::generateDynamicDmlByte);
        generators.put(TypeNames.BOXED_BYTE, QueryByNameMethodsGenerator::generateDynamicDmlByte);
        generators.put(TypeNames.PRIMITIVE_FLOAT, QueryByNameMethodsGenerator::generateDynamicDmlFloat);
        generators.put(TypeNames.BOXED_FLOAT, QueryByNameMethodsGenerator::generateDynamicDmlFloat);
        generators.put(TypeNames.PRIMITIVE_DOUBLE, QueryByNameMethodsGenerator::generateDynamicDmlDouble);
        generators.put(TypeNames.BOXED_DOUBLE, QueryByNameMethodsGenerator::generateDynamicDmlDouble);
        generators.put(BIG_INTEGER, QueryByNameMethodsGenerator::generateDynamicDmlBigInteger);
        generators.put(BIG_DECIMAL, QueryByNameMethodsGenerator::generateDynamicDmlBigDecimal);
        DYNAMIC_DML_GENERATORS = Map.copyOf(generators);
    }

    private final io.helidon.data.codegen.parser.MethodNameParser parser;
    private final List<TypedElementInfo> methods;

    private QueryByNameMethodsGenerator(RepositoryInfo repositoryInfo,
                                        List<TypedElementInfo> methods,
                                        ClassModel.Builder classModel,
                                        CodegenContext codegenContext,
                                        PersistenceGenerator persistenceGenerator) {
        super(repositoryInfo, classModel, codegenContext, persistenceGenerator);
        this.parser = MethodNameParser.create();
        this.methods = methods;
    }

    // Generate all "query by method name" methods
    static void generate(RepositoryInfo repositoryInfo,
                         List<TypedElementInfo> methods,
                         ClassModel.Builder classModel,
                         CodegenContext codegenContext,
                         PersistenceGenerator persistenceGenerator) {
        QueryByNameMethodsGenerator generator = new QueryByNameMethodsGenerator(repositoryInfo,
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

    private static void generateQueryItem(Method.Builder builder,
                                          PersistenceGenerator.StatementGenerator statementGenerator,
                                          PersistenceGenerator.Query query,
                                          TypeName resultType) {
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator.addExecuteQueryItem(
                                           b2,
                                           query,
                                           resultType),
                                   EXECUTOR));
    }

    private static void generateDynamicQueryItem(Method.Builder builder,
                                                 RepositoryInfo repositoryInfo,
                                                 TypedElementInfo methodInfo,
                                                 PersistenceGenerator.StatementGenerator statementGenerator,
                                                 MethodParams methodParams,
                                                 DataQuery dataQuery,
                                                 TypeName resultType) {
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator.addExecuteDynamicQueryItem(
                                           b2,
                                           repositoryInfo,
                                           methodInfo,
                                           methodParams,
                                           dataQuery,
                                           resultType),
                                   EXECUTOR));

    }

    private static void generateQueryOptional(Method.Builder builder,
                                              TypedElementInfo methodInfo,
                                              PersistenceGenerator.StatementGenerator statementGenerator,
                                              PersistenceGenerator.Query query) {
        returnStatement(builder,
                        // Jakarta Persistence 3.2 compliant code disabled
                        // b1 -> optionalOfNullable(b1,
                        // Jakarta Persistence 3.1 workaround
                        b1 -> optionalFromQuery(b1,
                                                b2 -> call(b2,
                                                           // Jakarta Persistence 3.2 compliant code disabled
                                                           // b3 -> statementGenerator.addExecuteQueryItemOrNull(
                                                           // Jakarta Persistence 3.1 workaround
                                                           b3 -> statementGenerator.addExecuteQueryList(
                                                                   b3,
                                                                   query,
                                                                   genericReturnTypeArgument(methodInfo)),
                                                           EXECUTOR)));
    }

    private static void generateDynamicQueryOptional(Method.Builder builder,
                                                     RepositoryInfo repositoryInfo,
                                                     TypedElementInfo methodInfo,
                                                     PersistenceGenerator.StatementGenerator statementGenerator,
                                                     MethodParams methodParams,
                                                     DataQuery dataQuery) {
        returnStatement(builder,
                        // Jakarta Persistence 3.2 compliant code disabled
                        // b1 -> optionalOfNullable(b1,
                        // Jakarta Persistence 3.1 workaround
                        b1 -> optionalFromQuery(b1,
                                                b2 -> call(b2,
                                                           // Jakarta Persistence 3.2 compliant code disabled
                                                           // b3 -> statementGenerator.addExecuteDynamicQueryItemOrNull(
                                                           // Jakarta Persistence 3.1 workaround
                                                           b3 -> statementGenerator.addExecuteDynamicQueryList(
                                                                   b3,
                                                                   repositoryInfo,
                                                                   methodInfo,
                                                                   methodParams,
                                                                   dataQuery,
                                                                   genericReturnTypeArgument(methodInfo)),
                                                           EXECUTOR)));
    }

    private static void generateQueryList(Method.Builder builder,
                                          TypedElementInfo methodInfo,
                                          PersistenceGenerator.StatementGenerator statementGenerator,
                                          PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator.addExecuteQueryList(
                                           b2,
                                           query,
                                           genericReturnTypeArgument(methodInfo)),
                                   EXECUTOR));
    }

    private static void generateDynamicQueryList(Method.Builder builder,
                                                 RepositoryInfo repositoryInfo,
                                                 TypedElementInfo methodInfo,
                                                 PersistenceGenerator.StatementGenerator statementGenerator,
                                                 MethodParams methodParams,
                                                 DataQuery dataQuery) {
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator.addExecuteDynamicQueryList(
                                           b2,
                                           repositoryInfo,
                                           methodInfo,
                                           methodParams,
                                           dataQuery,
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

    private static void generateDynamicQueryStream(Method.Builder builder,
                                                   RepositoryInfo repositoryInfo,
                                                   TypedElementInfo methodInfo,
                                                   PersistenceGenerator.StatementGenerator statementGenerator,
                                                   MethodParams methodParams,
                                                   DataQuery dataQuery) {
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator.addExecuteDynamicQueryStream(b2,
                                                                                         repositoryInfo,
                                                                                         methodInfo,
                                                                                         methodParams,
                                                                                         dataQuery,
                                                                                         genericReturnTypeArgument(methodInfo)),
                                   EXECUTOR));
    }

    private static void generateQuerySlice(Method.Builder builder,
                                           TypedElementInfo methodInfo,
                                           PersistenceGenerator.StatementGenerator statementGenerator,
                                           PersistenceGenerator.Query query,
                                           MethodParams methodParams) {
        TypedElementInfo pageRequest = pageRequestRequired(methodParams, methodInfo);
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

    private static void generateDynamicQuerySlice(Method.Builder builder,
                                                  RepositoryInfo repositoryInfo,
                                                  TypedElementInfo methodInfo,
                                                  PersistenceGenerator.StatementGenerator statementGenerator,
                                                  MethodParams methodParams,
                                                  DataQuery dataQuery) {
        TypedElementInfo pageRequest = pageRequestRequired(methodParams, methodInfo);
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator.addSessionLambdaBlock(b2, b3 -> {
                                       List<PersistenceGenerator.QuerySettings> settings = statementGenerator
                                               .addDynamicSliceQuery(
                                                       builder,
                                                       repositoryInfo,
                                                       methodInfo,
                                                       methodParams,
                                                       dataQuery,
                                                       "stmt",
                                                       genericReturnTypeArgument(methodInfo));
                                       returnStatement(
                                               builder,
                                               b4 -> {
                                                   b4.addContent(SLICE)
                                                           .addContentLine(".create(");
                                                   increasePadding(b4, 2);
                                                   b4.addContent(pageRequest.elementName())
                                                           .addContentLine(",");
                                                   statementGenerator.addQueryPage(
                                                           b4,
                                                           b5 -> identifier(b5, "stmt"),
                                                           settings,
                                                           null,
                                                           pageRequest.elementName() + ".offset()",
                                                           pageRequest.elementName() + ".size()");
                                                   b4.addContent(")");
                                                   decreasePadding(b4, 2);
                                               });

                                   }),
                                   EXECUTOR));
    }

    private static void generateQueryPage(Method.Builder builder,
                                          TypedElementInfo methodInfo,
                                          PersistenceGenerator.StatementGenerator statementGenerator,
                                          PersistenceGenerator.Query query,
                                          DataQuery dataQuery,
                                          MethodParams methodParams,
                                          PersistenceGenerator.QueryBuilder queryBuilder) {
        TypedElementInfo pageRequest = pageRequestRequired(methodParams, methodInfo);
        PersistenceGenerator.Query countQuery = queryBuilder.buildCountQuery(
                dataQuery,
                methodParams.params().stream()
                        .map(QueryByNameMethodsGenerator::paramElementName)
                        .collect(Collectors.toList()));
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
                                       statementGenerator.addQueryItem(b3, countQuery, NUMBER);
                                       b3.addContentLine("")
                                               .padContent(2)
                                               .addContent(".intValue()");
                                       b3.addContent(")");
                                       decreasePadding(b3, 2);
                                   }),
                                   EXECUTOR));
    }

    private static void generateDynamicQueryPage(Method.Builder builder,
                                                 RepositoryInfo repositoryInfo,
                                                 TypedElementInfo methodInfo,
                                                 PersistenceGenerator.StatementGenerator statementGenerator,
                                                 MethodParams methodParams,
                                                 DataQuery dataQuery) {
        TypedElementInfo pageRequest = pageRequestRequired(methodParams, methodInfo);
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> statementGenerator.addSessionLambdaBlock(b2, b3 -> {
                                       List<PersistenceGenerator.QuerySettings> settings = statementGenerator
                                               .addDynamicPageQueries(
                                                       builder,
                                                       repositoryInfo,
                                                       methodInfo,
                                                       methodParams,
                                                       dataQuery,
                                                       "stmt",
                                                       "countStmt",
                                                       genericReturnTypeArgument(methodInfo));
                                       returnStatement(
                                               builder,
                                               b4 -> {
                                                   b4.addContent(PAGE)
                                                           .addContentLine(".create(");
                                                   increasePadding(b4, 2);
                                                   b4.addContent(pageRequest.elementName())
                                                           .addContentLine(",");
                                                   statementGenerator.addQueryPage(
                                                           b4,
                                                           b5 -> identifier(b5, "stmt"),
                                                           settings,
                                                           null,
                                                           pageRequest.elementName() + ".offset()",
                                                           pageRequest.elementName() + ".size()");
                                                   b4.addContentLine(",");
                                                   statementGenerator.addQueryCount(
                                                           builder,
                                                           b5 -> identifier(b5, "countStmt"),
                                                           settings,
                                                           null);
                                                   b4.addContent(")");
                                                   decreasePadding(b4, 2);
                                               });

                                   }),
                                   EXECUTOR));
    }

    private static void generateNumber(Method.Builder builder,
                                       TypedElementInfo methodInfo,
                                       PersistenceGenerator.StatementGenerator statementGenerator,
                                       PersistenceGenerator.Query query) {
        generateQueryItem(builder, statementGenerator, query, NUMBER);
    }

    private static void generateLong(Method.Builder builder,
                                     TypedElementInfo methodInfo,
                                     PersistenceGenerator.StatementGenerator statementGenerator,
                                     PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> {
                                       statementGenerator.addExecuteQueryItem(b2, query, NUMBER);
                                       b2.addContent(".longValue()");
                                   },
                                   EXECUTOR));
    }

    private static void generateInt(Method.Builder builder,
                                    TypedElementInfo methodInfo,
                                    PersistenceGenerator.StatementGenerator statementGenerator,
                                    PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> {
                                       statementGenerator.addExecuteQueryItem(b2, query, NUMBER);
                                       b2.addContent(".intValue()");
                                   },
                                   EXECUTOR));
    }

    private static void generateShort(Method.Builder builder,
                                      TypedElementInfo methodInfo,
                                      PersistenceGenerator.StatementGenerator statementGenerator,
                                      PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> {
                                       statementGenerator.addExecuteQueryItem(b2, query, NUMBER);
                                       b2.addContent(".shortValue()");
                                   },
                                   EXECUTOR));
    }

    private static void generateByte(Method.Builder builder,
                                     TypedElementInfo methodInfo,
                                     PersistenceGenerator.StatementGenerator statementGenerator,
                                     PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> {
                                       statementGenerator.addExecuteQueryItem(b2, query, NUMBER);
                                       b2.addContent(".byteValue()");
                                   },
                                   EXECUTOR));
    }

    private static void generateFloat(Method.Builder builder,
                                      TypedElementInfo methodInfo,
                                      PersistenceGenerator.StatementGenerator statementGenerator,
                                      PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> {
                                       statementGenerator.addExecuteQueryItem(b2, query, NUMBER);
                                       b2.addContent(".floatValue()");
                                   },
                                   EXECUTOR));
    }

    private static void generateDouble(Method.Builder builder,
                                       TypedElementInfo methodInfo,
                                       PersistenceGenerator.StatementGenerator statementGenerator,
                                       PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> call(b1,
                                   b2 -> {
                                       statementGenerator.addExecuteQueryItem(b2, query, NUMBER);
                                       b2.addContent(".doubleValue()");
                                   },
                                   EXECUTOR));
    }

    private static void generateBigInteger(Method.Builder builder,
                                           TypedElementInfo methodInfo,
                                           PersistenceGenerator.StatementGenerator statementGenerator,
                                           PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> {
                            b1.addContent(BigInteger.class)
                                    .addContent(".valueOf(");
                            call(b1,
                                 b2 -> {
                                     statementGenerator.addExecuteQueryItem(b2, query, NUMBER);
                                     b2.addContent(".longValue()");
                                 },
                                 EXECUTOR);
                            b1.addContent(")");
                        });
    }

    private static void generateBigDecimal(Method.Builder builder,
                                           TypedElementInfo methodInfo,
                                           PersistenceGenerator.StatementGenerator statementGenerator,
                                           PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> {
                            // FIXME uses JPA gapi from codegen
                            b1.addContent(TypeName.create("io.helidon.data.jakarta.persistence.gapi.JpaRepositoryExecutor"))
                                    .addContent(".createBigDecimal(");
                            call(b1,
                                 b2 -> statementGenerator.addExecuteQueryItem(b2, query, NUMBER),
                                 EXECUTOR);
                            b1.addContent(")");
                        });
    }

    // Boolean result of numeric queries, currently only exists projection based on COUNT query is implemented
    private static void generateBoolean(Method.Builder builder,
                                        TypedElementInfo methodInfo,
                                        PersistenceGenerator.StatementGenerator statementGenerator,
                                        PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addExecuteQueryItem(
                                         b2,
                                         query,
                                         NUMBER),
                                 EXECUTOR);
                            b1.addContent(".longValue() > 0");
                        });
    }

    private static void generateDynamicNumber(Method.Builder builder,
                                              RepositoryInfo repositoryInfo,
                                              TypedElementInfo methodInfo,
                                              PersistenceGenerator.StatementGenerator statementGenerator,
                                              MethodParams methodParams,
                                              DataQuery dataQuery,
                                              TypeName returnType) {
        generateDynamicQueryItem(builder, repositoryInfo, methodInfo, statementGenerator, methodParams, dataQuery, NUMBER);
    }

    private static void generateDynamicLong(Method.Builder builder,
                                            RepositoryInfo repositoryInfo,
                                            TypedElementInfo methodInfo,
                                            PersistenceGenerator.StatementGenerator statementGenerator,
                                            MethodParams methodParams,
                                            DataQuery dataQuery,
                                            TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDynamicQueryItem(
                                         b2,
                                         repositoryInfo,
                                         methodInfo,
                                         methodParams,
                                         dataQuery,
                                         NUMBER),
                                 EXECUTOR);
                            b1.addContent(".longValue()");
                        });
    }

    private static void generateDynamicInt(Method.Builder builder,
                                           RepositoryInfo repositoryInfo,
                                           TypedElementInfo methodInfo,
                                           PersistenceGenerator.StatementGenerator statementGenerator,
                                           MethodParams methodParams,
                                           DataQuery dataQuery,
                                           TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDynamicQueryItem(
                                         b2,
                                         repositoryInfo,
                                         methodInfo,
                                         methodParams,
                                         dataQuery,
                                         NUMBER),
                                 EXECUTOR);
                            b1.addContent(".intValue()");
                        });
    }

    private static void generateDynamicShort(Method.Builder builder,
                                             RepositoryInfo repositoryInfo,
                                             TypedElementInfo methodInfo,
                                             PersistenceGenerator.StatementGenerator statementGenerator,
                                             MethodParams methodParams,
                                             DataQuery dataQuery,
                                             TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDynamicQueryItem(
                                         b2,
                                         repositoryInfo,
                                         methodInfo,
                                         methodParams,
                                         dataQuery,
                                         NUMBER),
                                 EXECUTOR);
                            b1.addContent(".shortValue()");
                        });
    }

    private static void generateDynamicByte(Method.Builder builder,
                                            RepositoryInfo repositoryInfo,
                                            TypedElementInfo methodInfo,
                                            PersistenceGenerator.StatementGenerator statementGenerator,
                                            MethodParams methodParams,
                                            DataQuery dataQuery,
                                            TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDynamicQueryItem(
                                         b2,
                                         repositoryInfo,
                                         methodInfo,
                                         methodParams,
                                         dataQuery,
                                         NUMBER),
                                 EXECUTOR);
                            b1.addContent(".byteValue()");
                        });
    }

    private static void generateDynamicFloat(Method.Builder builder,
                                             RepositoryInfo repositoryInfo,
                                             TypedElementInfo methodInfo,
                                             PersistenceGenerator.StatementGenerator statementGenerator,
                                             MethodParams methodParams,
                                             DataQuery dataQuery,
                                             TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDynamicQueryItem(
                                         b2,
                                         repositoryInfo,
                                         methodInfo,
                                         methodParams,
                                         dataQuery,
                                         NUMBER),
                                 EXECUTOR);
                            b1.addContent(".floatValue()");
                        });
    }

    private static void generateDynamicDouble(Method.Builder builder,
                                              RepositoryInfo repositoryInfo,
                                              TypedElementInfo methodInfo,
                                              PersistenceGenerator.StatementGenerator statementGenerator,
                                              MethodParams methodParams,
                                              DataQuery dataQuery,
                                              TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDynamicQueryItem(
                                         b2,
                                         repositoryInfo,
                                         methodInfo,
                                         methodParams,
                                         dataQuery,
                                         NUMBER),
                                 EXECUTOR);
                            b1.addContent(".doubleValue()");
                        });
    }

    private static void generateDynamicBigInteger(Method.Builder builder,
                                                  RepositoryInfo repositoryInfo,
                                                  TypedElementInfo methodInfo,
                                                  PersistenceGenerator.StatementGenerator statementGenerator,
                                                  MethodParams methodParams,
                                                  DataQuery dataQuery,
                                                  TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            b1.addContent(BigInteger.class)
                                    .addContent(".valueOf(");
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDynamicQueryItem(
                                         b2,
                                         repositoryInfo,
                                         methodInfo,
                                         methodParams,
                                         dataQuery,
                                         NUMBER),
                                 EXECUTOR);
                            b1.addContent(".longValue())");
                        });
    }

    private static void generateDynamicBigDecimal(Method.Builder builder,
                                                  RepositoryInfo repositoryInfo,
                                                  TypedElementInfo methodInfo,
                                                  PersistenceGenerator.StatementGenerator statementGenerator,
                                                  MethodParams methodParams,
                                                  DataQuery dataQuery,
                                                  TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            // FIXME uses JPA gapi from codegen
                            b1.addContent(TypeName.create("io.helidon.data.jakarta.persistence.gapi.JpaRepositoryExecutor"))
                                    .addContent(".createBigDecimal(");
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDynamicQueryItem(
                                         b2,
                                         repositoryInfo,
                                         methodInfo,
                                         methodParams,
                                         dataQuery,
                                         NUMBER),
                                 EXECUTOR);
                            b1.addContent(")");
                        });
    }

    private static void generateDynamicBoolean(Method.Builder builder,
                                               RepositoryInfo repositoryInfo,
                                               TypedElementInfo methodInfo,
                                               PersistenceGenerator.StatementGenerator statementGenerator,
                                               MethodParams methodParams,
                                               DataQuery dataQuery,
                                               TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDynamicQueryItem(
                                         b2,
                                         repositoryInfo,
                                         methodInfo,
                                         methodParams,
                                         dataQuery,
                                         NUMBER),
                                 EXECUTOR);
                            b1.addContent(".longValue() > 0");
                        });
    }

    private static void generateDmlVoid(Method.Builder builder,
                                        TypedElementInfo methodInfo,
                                        PersistenceGenerator.StatementGenerator statementGenerator,
                                        PersistenceGenerator.Query query) {
        statement(builder,
                  b1 -> run(b1,
                            b2 -> statementGenerator.addExecuteDml(b2, query),
                            EXECUTOR));

    }

    private static void generateDmlBoxedVoid(Method.Builder builder,
                                             TypedElementInfo methodInfo,
                                             PersistenceGenerator.StatementGenerator statementGenerator,
                                             PersistenceGenerator.Query query) {
        statement(builder,
                  b1 -> run(b1,
                            b2 -> statementGenerator.addExecuteDml(b2, query),
                            EXECUTOR));
        returnStatement(builder, BaseRepositoryInterfaceGenerator::nullValue);
    }

    private static void generateDmlBoolean(Method.Builder builder,
                                           TypedElementInfo methodInfo,
                                           PersistenceGenerator.StatementGenerator statementGenerator,
                                           PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDml(b2, query),
                                 EXECUTOR);
                            b1.addContent(" > 0");
                        });

    }

    private static void generateDmlLong(Method.Builder builder,
                                        TypedElementInfo methodInfo,
                                        PersistenceGenerator.StatementGenerator statementGenerator,
                                        PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDml(b2, query),
                                 EXECUTOR);
                            b1.addContentLine("")
                                    .padContent(2)
                                    .addContent(".longValue()");
                        });
    }

    private static void generateDmlInt(Method.Builder builder,
                                       TypedElementInfo methodInfo,
                                       PersistenceGenerator.StatementGenerator statementGenerator,
                                       PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDml(b2, query),
                                 EXECUTOR);
                            b1.addContentLine("")
                                    .padContent(2)
                                    .addContent(".intValue()");
                        });
    }

    private static void generateDmlShort(Method.Builder builder,
                                         TypedElementInfo methodInfo,
                                         PersistenceGenerator.StatementGenerator statementGenerator,
                                         PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDml(b2, query),
                                 EXECUTOR);
                            b1.addContentLine("")
                                    .padContent(2)
                                    .addContent(".shortValue()");
                        });
    }

    private static void generateDmlByte(Method.Builder builder,
                                        TypedElementInfo methodInfo,
                                        PersistenceGenerator.StatementGenerator statementGenerator,
                                        PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDml(b2, query),
                                 EXECUTOR);
                            b1.addContentLine("")
                                    .padContent(2)
                                    .addContent(".byteValue()");
                        });
    }

    private static void generateDmlFloat(Method.Builder builder,
                                         TypedElementInfo methodInfo,
                                         PersistenceGenerator.StatementGenerator statementGenerator,
                                         PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDml(b2, query),
                                 EXECUTOR);
                            b1.addContentLine("")
                                    .padContent(2)
                                    .addContent(".floatValue()");
                        });
    }

    private static void generateDmlDouble(Method.Builder builder,
                                          TypedElementInfo methodInfo,
                                          PersistenceGenerator.StatementGenerator statementGenerator,
                                          PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDml(b2, query),
                                 EXECUTOR);
                            b1.addContentLine("")
                                    .padContent(2)
                                    .addContent(".doubleValue()");
                        });
    }

    private static void generateDmlBigInteger(Method.Builder builder,
                                              TypedElementInfo methodInfo,
                                              PersistenceGenerator.StatementGenerator statementGenerator,
                                              PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> {
                            b1.addContent(BigInteger.class)
                                    .addContent(".valueOf(");
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDml(b2, query),
                                 EXECUTOR);
                            b1.addContentLine("")
                                    .padContent(2)
                                    .addContent(".longValue())");
                        });
    }

    private static void generateDmlBigDecimal(Method.Builder builder,
                                              TypedElementInfo methodInfo,
                                              PersistenceGenerator.StatementGenerator statementGenerator,
                                              PersistenceGenerator.Query query) {
        returnStatement(builder,
                        b1 -> {
                            b1.addContent(BigDecimal.class)
                                    .addContent(".valueOf(");
                            call(b1,
                                 b2 -> statementGenerator.addExecuteDml(b2, query),
                                 EXECUTOR);
                            b1.addContentLine("")
                                    .padContent(2)
                                    .addContent(".longValue())");
                        });
    }

    private static void generateDynamicDmlVoid(Method.Builder builder,
                                               RepositoryInfo repositoryInfo,
                                               TypedElementInfo methodInfo,
                                               PersistenceGenerator.StatementGenerator statementGenerator,
                                               MethodParams methodParams,
                                               DataQuery dataQuery,
                                               TypeName returnType) {
        statement(builder,
                  b1 -> run(b1,
                            b2 -> statementGenerator.addDynamicDml(b2,
                                                                   repositoryInfo,
                                                                   methodInfo,
                                                                   methodParams,
                                                                   dataQuery,
                                                                   returnType),
                            EXECUTOR));

    }

    private static void generateDynamicDmlBoxedVoid(Method.Builder builder,
                                                    RepositoryInfo repositoryInfo,
                                                    TypedElementInfo methodInfo,
                                                    PersistenceGenerator.StatementGenerator statementGenerator,
                                                    MethodParams methodParams,
                                                    DataQuery dataQuery,
                                                    TypeName returnType) {
        statement(builder,
                  b1 -> run(b1,
                            b2 -> statementGenerator.addDynamicDml(b2,
                                                                   repositoryInfo,
                                                                   methodInfo,
                                                                   methodParams,
                                                                   dataQuery,
                                                                   returnType),
                            EXECUTOR));
        returnStatement(builder, BaseRepositoryInterfaceGenerator::nullValue);
    }

    private static void generateDynamicDmlBoolean(Method.Builder builder,
                                                  RepositoryInfo repositoryInfo,
                                                  TypedElementInfo methodInfo,
                                                  PersistenceGenerator.StatementGenerator statementGenerator,
                                                  MethodParams methodParams,
                                                  DataQuery dataQuery,
                                                  TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addDynamicDml(b2,
                                                                        repositoryInfo,
                                                                        methodInfo,
                                                                        methodParams,
                                                                        dataQuery,
                                                                        returnType),
                                 EXECUTOR);
                            b1.addContent(" > 0");
                        });

    }

    private static void generateDynamicDmlLong(Method.Builder builder,
                                               RepositoryInfo repositoryInfo,
                                               TypedElementInfo methodInfo,
                                               PersistenceGenerator.StatementGenerator statementGenerator,
                                               MethodParams methodParams,
                                               DataQuery dataQuery,
                                               TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addDynamicDml(b2,
                                                                        repositoryInfo,
                                                                        methodInfo,
                                                                        methodParams,
                                                                        dataQuery,
                                                                        returnType),
                                 EXECUTOR);
                            b1.addContentLine("")
                                    .padContent(2)
                                    .addContent(".longValue()");
                        });
    }

    private static void generateDynamicDmlInt(Method.Builder builder,
                                              RepositoryInfo repositoryInfo,
                                              TypedElementInfo methodInfo,
                                              PersistenceGenerator.StatementGenerator statementGenerator,
                                              MethodParams methodParams,
                                              DataQuery dataQuery,
                                              TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addDynamicDml(b2,
                                                                        repositoryInfo,
                                                                        methodInfo,
                                                                        methodParams,
                                                                        dataQuery,
                                                                        returnType),
                                 EXECUTOR);
                            b1.addContentLine("")
                                    .padContent(2)
                                    .addContent(".intValue()");
                        });
    }

    private static void generateDynamicDmlShort(Method.Builder builder,
                                                RepositoryInfo repositoryInfo,
                                                TypedElementInfo methodInfo,
                                                PersistenceGenerator.StatementGenerator statementGenerator,
                                                MethodParams methodParams,
                                                DataQuery dataQuery,
                                                TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addDynamicDml(b2,
                                                                        repositoryInfo,
                                                                        methodInfo,
                                                                        methodParams,
                                                                        dataQuery,
                                                                        returnType),
                                 EXECUTOR);
                            b1.addContentLine("")
                                    .padContent(2)
                                    .addContent(".shortValue()");
                        });
    }

    private static void generateDynamicDmlByte(Method.Builder builder,
                                               RepositoryInfo repositoryInfo,
                                               TypedElementInfo methodInfo,
                                               PersistenceGenerator.StatementGenerator statementGenerator,
                                               MethodParams methodParams,
                                               DataQuery dataQuery,
                                               TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addDynamicDml(b2,
                                                                        repositoryInfo,
                                                                        methodInfo,
                                                                        methodParams,
                                                                        dataQuery,
                                                                        returnType),
                                 EXECUTOR);
                            b1.addContentLine("")
                                    .padContent(2)
                                    .addContent(".byteValue()");
                        });
    }

    private static void generateDynamicDmlFloat(Method.Builder builder,
                                                RepositoryInfo repositoryInfo,
                                                TypedElementInfo methodInfo,
                                                PersistenceGenerator.StatementGenerator statementGenerator,
                                                MethodParams methodParams,
                                                DataQuery dataQuery,
                                                TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addDynamicDml(b2,
                                                                        repositoryInfo,
                                                                        methodInfo,
                                                                        methodParams,
                                                                        dataQuery,
                                                                        returnType),
                                 EXECUTOR);
                            b1.addContentLine("")
                                    .padContent(2)
                                    .addContent(".floatValue()");
                        });
    }

    private static void generateDynamicDmlDouble(Method.Builder builder,
                                                 RepositoryInfo repositoryInfo,
                                                 TypedElementInfo methodInfo,
                                                 PersistenceGenerator.StatementGenerator statementGenerator,
                                                 MethodParams methodParams,
                                                 DataQuery dataQuery,
                                                 TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            call(b1,
                                 b2 -> statementGenerator.addDynamicDml(b2,
                                                                        repositoryInfo,
                                                                        methodInfo,
                                                                        methodParams,
                                                                        dataQuery,
                                                                        returnType),
                                 EXECUTOR);
                            b1.addContentLine("")
                                    .padContent(2)
                                    .addContent(".doubleValue()");
                        });
    }

    private static void generateDynamicDmlBigInteger(Method.Builder builder,
                                                     RepositoryInfo repositoryInfo,
                                                     TypedElementInfo methodInfo,
                                                     PersistenceGenerator.StatementGenerator statementGenerator,
                                                     MethodParams methodParams,
                                                     DataQuery dataQuery,
                                                     TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            b1.addContent(BigInteger.class)
                                    .addContent(".valueOf(");
                            call(b1,
                                 b2 -> statementGenerator.addDynamicDml(b2,
                                                                        repositoryInfo,
                                                                        methodInfo,
                                                                        methodParams,
                                                                        dataQuery,
                                                                        returnType),
                                 EXECUTOR);
                            b1.addContentLine("")
                                    .padContent(2)
                                    .addContent(".longValue())");
                        });
    }

    private static void generateDynamicDmlBigDecimal(Method.Builder builder,
                                                     RepositoryInfo repositoryInfo,
                                                     TypedElementInfo methodInfo,
                                                     PersistenceGenerator.StatementGenerator statementGenerator,
                                                     MethodParams methodParams,
                                                     DataQuery dataQuery,
                                                     TypeName returnType) {
        returnStatement(builder,
                        b1 -> {
                            b1.addContent(BigDecimal.class)
                                    .addContent(".valueOf(");
                            call(b1,
                                 b2 -> statementGenerator.addDynamicDml(b2,
                                                                        repositoryInfo,
                                                                        methodInfo,
                                                                        methodParams,
                                                                        dataQuery,
                                                                        returnType),
                                 EXECUTOR);
                            b1.addContentLine("")
                                    .padContent(2)
                                    .addContent(".longValue())");
                        });
    }

    // Add generated method to the model
    private void addGeneratedMethod(TypedElementInfo methodInfo) {
        if (parser.parse(methodInfo.elementName())) {
            try {
                classModel().addMethod(builder -> generateMethod(builder, methodInfo));
            } catch (Exception e) {
                String message = e.getMessage() != null && !e.getMessage().isEmpty()
                        ? e.getMessage()
                        : "Code generation of query by method name method failed";
                throw new CodegenException(message, e, methodInfo.originatingElement());
            }
        } else {
            methodWarning(methodInfo, "Skipping unsupported method name");
        }
    }

    // Generate method code
    private void generateMethod(Method.Builder builder, TypedElementInfo methodInfo) {
        MethodParams methodParams = generateHeader(builder, methodInfo);
        DataQuery dataQuery = parser.dataQuery();
        validateResult(dataQuery, methodInfo.typeName());

        codegenContext().logger()
                .log(Level.TRACE, String.format("QbMN method %s", methodInfo.elementName()));

        // Dynamnic query as execution of Jakarta Persistence Criteria
        if (methodParams.dynamic()) {
            switch (dataQuery.projection().action()) {
            case Select:
                generateDynamicQuery(builder, methodInfo, dataQuery, methodParams);
                break;
            case Delete:
            case Update:
/* Disabled until dynamic criteria support is added
                    // Will make sense with dynamic criteria, Sort is useless
                    if (methodParams.sort().isPresent()) {
                        methodWarning(methodInfo, "Ignoring Sort parameter in DML statement");
                    }
                    // Already implemented to use Jakarta Persistence Criteria
                    generateDynamicDml(builder, methodInfo, dataQuery, methodParams);
                    break;
 */
                // Unsupported until criteria support is added
                throw new UnsupportedOperationException("Sort parameter used in DML statement");
            default:
                throw new UnsupportedOperationException("Unknown query action " + dataQuery.projection().action());
            }
            // Static query (no dynamic method parameter is present) as execution of JPQL statement
        } else {
            PersistenceGenerator.Query query = queryBuilder().buildQuery(
                    dataQuery,
                    methodParams.params().stream()
                            .map(QueryByNameMethodsGenerator::paramElementName)
                            .collect(Collectors.toList()));
            if (query.isDml()) {
                generateDml(builder, methodInfo, query);
            } else {
                generateQuery(builder, methodInfo, query, dataQuery, methodParams);
            }
        }
    }

    // Generate DML execution code statements
    private void generateDml(Method.Builder builder,
                             TypedElementInfo methodInfo,
                             PersistenceGenerator.Query query) {
        TypeName returnType = methodInfo.typeName();
        if (!DML_GENERATORS.containsKey(returnType)) {
            throw new UnsupportedOperationException("Unsupported method "
                                                            + methodInfo.elementName()
                                                            + " return type "
                                                            + returnType);
        }
        DML_GENERATORS.get(returnType)
                .generate(builder, methodInfo, statementGenerator(), query);
    }

    // Generate Dynamic DML execution code statements
    private void generateDynamicDml(Method.Builder builder,
                                    TypedElementInfo methodInfo,
                                    DataQuery dataQuery,
                                    MethodParams methodParams
    ) {
        TypeName returnType = methodInfo.typeName();
        if (!DYNAMIC_DML_GENERATORS.containsKey(returnType)) {
            throw new UnsupportedOperationException("Unsupported method "
                                                            + methodInfo.elementName()
                                                            + " return type "
                                                            + returnType);
        }
        DYNAMIC_DML_GENERATORS.get(returnType)
                .generate(builder, repositoryInfo(), methodInfo, statementGenerator(), methodParams, dataQuery, returnType);
    }

    // Generate query execution code statements
    private void generateQuery(Method.Builder builder,
                               TypedElementInfo methodInfo,
                               PersistenceGenerator.Query query,
                               DataQuery dataQuery,
                               MethodParams methodParams) {
        switch (query.returnType()) {
        case ENTITY:
            generateEntityQuery(builder, methodInfo, query, dataQuery, methodParams);
            break;
        case NUMBER:
            generateQuery(builder, methodInfo, query, NUMBER_RESULT_GENERATORS);
            break;
        case BOOLEAN:
            generateQuery(builder, methodInfo, query, BOOLEAN_RESULT_GENERATORS);
            break;
        case DML:
            throw new UnsupportedOperationException("Query return type "
                                                            + query.returnType()
                                                            + " is not supported for SELECT statement");
        default:
            throw new UnsupportedOperationException("Unsupported query return type " + query.returnType());
        }
    }

    // Generate dynamic query execution code statements
    private void generateDynamicQuery(Method.Builder builder,
                                      TypedElementInfo methodInfo,
                                      DataQuery dataQuery,
                                      MethodParams methodParams) {
        QueryReturnType returnType = queryBuilder().queryReturntype(dataQuery);
        switch (returnType) {
        case ENTITY:
            generateEntityDynamicQuery(builder, methodInfo, dataQuery, methodParams);
            break;
        // Return type of Count, Max, Min, Sum and Avg projection expressions
        case NUMBER:
            generateDynamicQuery(builder, methodInfo, dataQuery, methodParams, DYNAMIC_NUMBER_RESULT_GENERATORS);
            break;
        // Return type of Exists projection expressions
        case BOOLEAN:
            generateDynamicQuery(builder, methodInfo, dataQuery, methodParams, DYNAMIC_BOOLEAN_RESULT_GENERATORS);
            break;
        case DML:
            throw new UnsupportedOperationException("Query return type "
                                                            + returnType
                                                            + " is not supported for SELECT statement");
        default:
            throw new UnsupportedOperationException("Unsupported query return type " + returnType);
        }
    }

    private void generateQuery(Method.Builder builder,
                               TypedElementInfo methodInfo,
                               PersistenceGenerator.Query query,
                               Map<TypeName, Generator> generators) {
        TypeName returnType = methodInfo.typeName();
        if (!generators.containsKey(returnType)) {
            throw new UnsupportedOperationException("Unsupported return type "
                                                            + returnType
                                                            + " of method "
                                                            + methodInfo.elementName());
        }
        generators.get(returnType)
                .generate(builder, methodInfo, statementGenerator(), query);

    }

    private void generateDynamicQuery(Method.Builder builder,
                                      TypedElementInfo methodInfo,
                                      DataQuery dataQuery,
                                      MethodParams methodParams,
                                      Map<TypeName, DynamicGenerator> generators) {
        TypeName returnType = methodInfo.typeName();
        if (!generators.containsKey(returnType)) {
            throw new UnsupportedOperationException("Unsupported return type "
                                                            + returnType
                                                            + " of method "
                                                            + methodInfo.elementName());
        }
        generators.get(returnType)
                .generate(builder, repositoryInfo(), methodInfo, statementGenerator(), methodParams, dataQuery, returnType);

    }

    private void generateEntityQuery(Method.Builder builder,
                                     TypedElementInfo methodInfo,
                                     PersistenceGenerator.Query query,
                                     DataQuery dataQuery,
                                     MethodParams methodParams) {
        if (STREAM.equals(methodInfo.typeName())) {
            generateQueryStream(builder, methodInfo, statementGenerator(), query);
        } else if (isListOrCollection(methodInfo.typeName())) {
            generateQueryList(builder, methodInfo, statementGenerator(), query);
        } else if (methodInfo.typeName().isOptional()) {
            generateQueryOptional(builder, methodInfo, statementGenerator(), query);
        } else if (SLICE.equals(methodInfo.typeName())) {
            generateQuerySlice(builder, methodInfo, statementGenerator(), query, methodParams);
        } else if (PAGE.equals(methodInfo.typeName())) {
            generateQueryPage(builder, methodInfo, statementGenerator(), query, dataQuery, methodParams, queryBuilder());
        } else {
            generateQueryItem(builder, statementGenerator(), query, methodInfo.typeName());
        }
    }

    private void generateEntityDynamicQuery(Method.Builder builder,
                                            TypedElementInfo methodInfo,
                                            DataQuery dataQuery,
                                            MethodParams methodParams) {
        if (STREAM.equals(methodInfo.typeName())) {
            generateDynamicQueryStream(builder, repositoryInfo(), methodInfo, statementGenerator(), methodParams, dataQuery);
        } else if (isListOrCollection(methodInfo.typeName())) {
            generateDynamicQueryList(builder, repositoryInfo(), methodInfo, statementGenerator(), methodParams, dataQuery);
        } else if (methodInfo.typeName().isOptional()) {
            generateDynamicQueryOptional(builder, repositoryInfo(), methodInfo, statementGenerator(), methodParams, dataQuery);
        } else if (SLICE.equals(methodInfo.typeName())) {
            generateDynamicQuerySlice(builder, repositoryInfo(), methodInfo, statementGenerator(), methodParams, dataQuery);
        } else if (PAGE.equals(methodInfo.typeName())) {
            generateDynamicQueryPage(builder, repositoryInfo(), methodInfo, statementGenerator(), methodParams, dataQuery);
        } else {
            generateDynamicQueryItem(builder,
                                     repositoryInfo(),
                                     methodInfo,
                                     statementGenerator(),
                                     methodParams,
                                     dataQuery,
                                     methodInfo.typeName());
        }
    }

    // FIXME: Implement missing validations
    private void validateResult(DataQuery dataQuery, TypeName returnType) {
        dataQuery.projection()
                .result()
                .ifPresentOrElse(
                        result -> {

                        },
                        () -> {
                            throw new IllegalArgumentException("Missing projection result in DataQuery instance");
                        });
        switch (dataQuery.projection().action()) {
        // SELECT validations
        case Select:
            dataQuery.projection()
                    .expression()
                    .ifPresent(expression -> {
                        // Avg expression operator requires Double as return type
                        switch (expression.operator()) {
                        case Avg:
                            if (!returnType.equals(TypeNames.PRIMITIVE_DOUBLE)
                                    && !returnType.equals(TypeNames.BOXED_DOUBLE)
                                    && !returnType.equals(TypeNames.PRIMITIVE_FLOAT)
                                    && !returnType.equals(TypeNames.BOXED_FLOAT)) {
                                throw new IllegalArgumentException(
                                        "Projection operator Avg requires method return type float or double");
                            }
                            break;
                        default:
                            // Do nothing
                        }
                    });
            break;
        default:
            // Do nothing
        }
    }

    // Dispatcher interface
    @FunctionalInterface
    private interface Generator {

        void generate(Method.Builder builder,
                      TypedElementInfo methodInfo,
                      PersistenceGenerator.StatementGenerator statementGenerator,
                      PersistenceGenerator.Query query);

    }

    // Dispatcher interface
    @FunctionalInterface
    private interface DynamicGenerator {

        void generate(Method.Builder builder,
                      RepositoryInfo repositoryInfo,
                      TypedElementInfo methodInfo,
                      PersistenceGenerator.StatementGenerator statementGenerator,
                      MethodParams methodParams,
                      DataQuery dataQuery,
                      TypeName returnType);

    }

}
