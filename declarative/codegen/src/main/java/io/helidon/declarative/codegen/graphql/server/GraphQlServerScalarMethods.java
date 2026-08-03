/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.graphql.server;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.COERCED_VARIABLES;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.COERCING;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.COERCING_PARSE_LITERAL_EXCEPTION;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.COERCING_PARSE_VALUE_EXCEPTION;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.COERCING_SERIALIZE_EXCEPTION;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_CONTEXT;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SCALAR_SPI;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SCALAR_TYPE;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_VALUE;

final class GraphQlServerScalarMethods {
    private static final TypeName LIST_OF_OBJECTS = TypeName.builder(TypeNames.LIST)
            .addTypeArgument(TypeNames.OBJECT)
            .build();
    private static final TypeName MAP_OF_STRING_TO_OBJECT = TypeName.builder(TypeNames.MAP)
            .addTypeArgument(TypeNames.STRING)
            .addTypeArgument(TypeNames.OBJECT)
            .build();

    private GraphQlServerScalarMethods() {
    }

    static void addScalarMethods(ClassModel.Builder classModel) {
        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .returnType(GRAPHQL_SCALAR_TYPE)
                .name("graphQlScalar")
                .addParameter(param -> param
                        .type(TypeNames.STRING)
                        .name("name"))
                .addParameter(param -> param
                        .type(TypeName.builder(TypeName.create(Class.class))
                                      .addTypeArgument(TypeNames.WILDCARD)
                                      .build())
                        .name("type"))
                .addContentLine("var matchingScalars = scalars.stream()")
                .increaseContentPadding()
                .addContentLine(".filter(scalar -> scalar.name().equals(name) && scalar.type().equals(type))")
                .addContentLine(".toList();")
                .decreaseContentPadding()
                .addContentLine("if (matchingScalars.size() == 1) {")
                .increaseContentPadding()
                .addContentLine("return graphQlScalarType(matchingScalars.getFirst());")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("if (matchingScalars.isEmpty()) {")
                .increaseContentPadding()
                .addContentLine("throw new IllegalStateException(\"Missing GraphQL scalar provider for scalar '\"")
                .increaseContentPadding()
                .addContentLine("+ name + \"' and Java type '\" + type.getName() + \"'.\");")
                .decreaseContentPadding()
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("throw new IllegalStateException(\"Multiple GraphQL scalar providers found for scalar '\"")
                .increaseContentPadding()
                .addContentLine("+ name + \"' and Java type '\" + type.getName() + \"'.\");")
                .decreaseContentPadding());

        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .returnType(GRAPHQL_SCALAR_TYPE)
                .name("graphQlScalarType")
                .addParameter(param -> param
                        .type(GRAPHQL_SCALAR_SPI)
                        .name("scalar"))
                .addContent("var builder = ")
                .addContent(GRAPHQL_SCALAR_TYPE)
                .addContentLine(".newScalar()")
                .increaseContentPadding()
                .addContentLine(".name(scalar.name())")
                .addContent(".coercing(new ")
                .addContent(COERCING)
                .addContentLine("<Object, Object>() {")
                .increaseContentPadding()
                .update(GraphQlServerScalarMethods::addScalarCoercingMethods)
                .decreaseContentPadding()
                .addContentLine("});")
                .decreaseContentPadding()
                .addContentLine("if (!scalar.description().isBlank()) {")
                .increaseContentPadding()
                .addContentLine("builder.description(scalar.description());")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("return builder.build();"));

        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .returnType(TypeNames.OBJECT)
                .name("scalarLiteralValue")
                .addParameter(param -> param
                        .type(TypeName.builder(GRAPHQL_VALUE)
                                      .addTypeArgument(TypeNames.WILDCARD)
                                      .build())
                        .name("value"))
                .addParameter(param -> param
                        .type(COERCED_VARIABLES)
                        .name("variables"))
                .addContentLine("return switch (value) {")
                .increaseContentPadding()
                .addContentLine("case graphql.language.StringValue stringValue -> stringValue.getValue();")
                .addContentLine("case graphql.language.IntValue intValue -> intValue.getValue();")
                .addContentLine("case graphql.language.FloatValue floatValue -> floatValue.getValue();")
                .addContentLine("case graphql.language.BooleanValue booleanValue -> booleanValue.isValue();")
                .addContentLine("case graphql.language.EnumValue enumValue -> enumValue.getName();")
                .addContentLine("case graphql.language.NullValue nullValue -> null;")
                .addContentLine("case graphql.language.VariableReference variable -> variables.get(variable.getName());")
                .addContentLine("case graphql.language.ArrayValue arrayValue -> scalarLiteralList(arrayValue, variables);")
                .addContentLine("case graphql.language.ObjectValue objectValue -> scalarLiteralObject(objectValue, variables);")
                .addContent("default -> throw new ")
                .addContent(COERCING_PARSE_LITERAL_EXCEPTION)
                .addContentLine("(\"Unsupported GraphQL scalar literal: \" + value.getClass().getName());")
                .decreaseContentPadding()
                .addContentLine("};"));

        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .returnType(LIST_OF_OBJECTS)
                .name("scalarLiteralList")
                .addParameter(param -> param
                        .type(TypeName.create("graphql.language.ArrayValue"))
                        .name("arrayValue"))
                .addParameter(param -> param
                        .type(COERCED_VARIABLES)
                        .name("variables"))
                .addContentLine("List<Object> result = new java.util.ArrayList<>(arrayValue.getValues().size());")
                .addContentLine("for (graphql.language.Value<?> value : arrayValue.getValues()) {")
                .increaseContentPadding()
                .addContentLine("result.add(scalarLiteralValue(value, variables));")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("return result;"));

        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .returnType(MAP_OF_STRING_TO_OBJECT)
                .name("scalarLiteralObject")
                .addParameter(param -> param
                        .type(TypeName.create("graphql.language.ObjectValue"))
                        .name("objectValue"))
                .addParameter(param -> param
                        .type(COERCED_VARIABLES)
                        .name("variables"))
                .addContentLine("Map<String, Object> result = new java.util.LinkedHashMap<>();")
                .addContentLine("for (graphql.language.ObjectField field : objectValue.getObjectFields()) {")
                .increaseContentPadding()
                .addContentLine("result.put(field.getName(), scalarLiteralValue(field.getValue(), variables));")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("return result;"));
    }

    private static void addScalarCoercingMethods(io.helidon.codegen.classmodel.Method.Builder method) {
        method.addContentLine("@Override")
                .addContent("public Object serialize(Object dataFetcherResult, ")
                .addContent(GRAPHQL_CONTEXT)
                .addContentLine(" graphQLContext, java.util.Locale locale) {")
                .increaseContentPadding()
                .addContentLine("try {")
                .increaseContentPadding()
                .addContentLine("return dataFetcherResult == null")
                .increaseContentPadding()
                .addContentLine("? null")
                .addContentLine(": java.util.Objects.requireNonNull(scalar.serialize(dataFetcherResult));")
                .decreaseContentPadding()
                .decreaseContentPadding()
                .addContentLine("} catch (RuntimeException e) {")
                .increaseContentPadding()
                .addContent("throw new ")
                .addContent(COERCING_SERIALIZE_EXCEPTION)
                .addContentLine("(\"Failed to serialize GraphQL scalar '\" + scalar.name() + \"'.\", e);")
                .decreaseContentPadding()
                .addContentLine("}")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine()
                .addContentLine("@Override")
                .addContent("public Object parseValue(Object input, ")
                .addContent(GRAPHQL_CONTEXT)
                .addContentLine(" graphQLContext, java.util.Locale locale) {")
                .increaseContentPadding()
                .addContentLine("try {")
                .increaseContentPadding()
                .addContentLine("return input == null")
                .increaseContentPadding()
                .addContentLine("? null")
                .addContentLine(": java.util.Objects.requireNonNull(scalar.parseValue(input));")
                .decreaseContentPadding()
                .decreaseContentPadding()
                .addContentLine("} catch (RuntimeException e) {")
                .increaseContentPadding()
                .addContent("throw new ")
                .addContent(COERCING_PARSE_VALUE_EXCEPTION)
                .addContentLine("(\"Failed to parse GraphQL scalar '\" + scalar.name() + \"'.\", e);")
                .decreaseContentPadding()
                .addContentLine("}")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine()
                .addContentLine("@Override")
                .addContent("public Object parseLiteral(")
                .addContent(TypeName.builder(GRAPHQL_VALUE)
                                    .addTypeArgument(TypeNames.WILDCARD)
                                    .build())
                .addContent(" input, ")
                .addContent(COERCED_VARIABLES)
                .addContent(" variables, ")
                .addContent(GRAPHQL_CONTEXT)
                .addContentLine(" graphQLContext, java.util.Locale locale) {")
                .increaseContentPadding()
                .addContentLine("try {")
                .increaseContentPadding()
                .addContentLine("Object literalValue = input == null ? null : scalarLiteralValue(input, variables);")
                .addContentLine("return literalValue == null")
                .increaseContentPadding()
                .addContentLine("? null")
                .addContentLine(": java.util.Objects.requireNonNull(scalar.parseLiteral(literalValue));")
                .decreaseContentPadding()
                .decreaseContentPadding()
                .addContentLine("} catch (RuntimeException e) {")
                .increaseContentPadding()
                .addContent("throw new ")
                .addContent(COERCING_PARSE_LITERAL_EXCEPTION)
                .addContentLine("(\"Failed to parse GraphQL scalar literal '\" + scalar.name() + \"'.\", e);")
                .decreaseContentPadding()
                .addContentLine("}")
                .decreaseContentPadding()
                .addContentLine("}");
    }
}
