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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.Argument;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.EnumSchemaType;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.EnumValue;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.InputSchemaField;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.InputSchemaType;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.Operation;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.ValueSchemaType;

final class GraphQlServerInputValues {
    private GraphQlServerInputValues() {
    }

    static List<EnumSchemaType> enumInputTypes(List<Operation> operations, List<InputSchemaType> inputTypes) {
        Map<TypeName, EnumSchemaType> result = new LinkedHashMap<>();
        for (Operation operation : operations) {
            for (Argument argument : operation.arguments()) {
                collectEnumInputTypes(result, argument.valueType());
            }
        }
        for (InputSchemaType inputType : inputTypes) {
            for (InputSchemaField field : inputType.fields()) {
                collectEnumInputTypes(result, field.valueType());
            }
        }
        return List.copyOf(result.values());
    }

    static void addEnumInputMethods(ClassModel.Builder classModel, List<EnumSchemaType> enumTypes) {
        for (EnumSchemaType enumType : enumTypes) {
            classModel.addMethod(method -> {
                method.accessModifier(AccessModifier.PRIVATE)
                        .isStatic(true)
                        .returnType(enumType.javaType())
                        .name(enumInputMethodName(enumType))
                        .addParameter(param -> param
                                .type(TypeNames.OBJECT)
                                .name("value"))
                        .addContentLine("if (value == null) {")
                        .increaseContentPadding()
                        .addContentLine("return null;")
                        .decreaseContentPadding()
                        .addContentLine("}")
                        .addContent("if (value instanceof ")
                        .addContent(enumType.javaType())
                        .addContentLine(" enumValue) {")
                        .increaseContentPadding()
                        .addContentLine("return enumValue;")
                        .decreaseContentPadding()
                        .addContentLine("}")
                        .addContentLine("return switch ((String) value) {")
                        .increaseContentPadding();
                for (EnumValue value : enumType.values()) {
                    method.addContent("case ")
                            .addContentLiteral(value.graphQlName())
                            .addContent(" -> ")
                            .addContent(enumType.javaType())
                            .addContent(".")
                            .addContent(value.javaName())
                            .addContentLine(";");
                }
                method.addContent("default -> throw new IllegalArgumentException(\"Unsupported GraphQL enum value \" + value")
                        .addContent(" + \" for ")
                        .addContent(enumType.javaType().fqName())
                        .addContentLine("\");")
                        .decreaseContentPadding()
                        .addContentLine("};");
            });
        }
    }

    static List<ValueSchemaType> listInputTypes(List<Operation> operations, List<InputSchemaType> inputTypes) {
        Map<String, ValueSchemaType> result = new LinkedHashMap<>();
        for (Operation operation : operations) {
            for (Argument argument : operation.arguments()) {
                collectListInputTypes(result, argument.valueType());
            }
        }
        for (InputSchemaType inputType : inputTypes) {
            for (InputSchemaField field : inputType.fields()) {
                collectListInputTypes(result, field.valueType());
            }
        }
        return List.copyOf(result.values());
    }

    static void addListInputMethods(ClassModel.Builder classModel, List<ValueSchemaType> listTypes) {
        for (ValueSchemaType listType : listTypes) {
            classModel.addMethod(method -> {
                method.accessModifier(AccessModifier.PRIVATE)
                        .isStatic(true)
                        .returnType(listType.javaType())
                        .name(listInputMethodName(listType))
                        .addParameter(param -> param
                                .type(TypeNames.OBJECT)
                                .name("value"))
                        .addContentLine("if (value == null) {")
                        .increaseContentPadding()
                        .addContentLine("return null;")
                        .decreaseContentPadding()
                        .addContentLine("}")
                        .addContentLine("var list = (java.util.List<?>) value;")
                        .addContent("var result = new java.util.ArrayList<")
                        .addContent(listType.elementType().orElseThrow().javaType())
                        .addContentLine(">(list.size());")
                        .addContentLine("for (Object it : list) {")
                        .increaseContentPadding()
                        .addContent("result.add(");
                valueExpression(method, listType.elementType().orElseThrow(), "it");
                method.addContentLine(");")
                        .decreaseContentPadding()
                        .addContentLine("}")
                        .addContentLine("return result;");
            });
        }
    }

    static void addInputObjectMethods(ClassModel.Builder classModel, List<InputSchemaType> inputTypes) {
        for (InputSchemaType inputType : inputTypes) {
            classModel.addMethod(method -> {
                method.accessModifier(AccessModifier.PRIVATE)
                        .isStatic(true)
                        .addAnnotation(Annotation.create(SuppressWarnings.class, "unchecked"))
                        .returnType(inputType.javaType())
                        .name(inputObjectMethodName(inputType))
                        .addParameter(param -> param
                                .type(TypeNames.OBJECT)
                                .name("value"))
                        .addContentLine("if (value == null) {")
                        .increaseContentPadding()
                        .addContentLine("return null;")
                        .decreaseContentPadding()
                        .addContentLine("}")
                        .addContentLine("var input = (java.util.Map<String, Object>) value;")
                        .addContent("return new ")
                        .addContent(inputType.javaType())
                        .addContentLine("(")
                        .increaseContentPadding()
                        .increaseContentPadding();
                List<InputSchemaField> fields = inputType.fields();
                for (int i = 0; i < fields.size(); i++) {
                    inputFieldValue(method, fields.get(i));
                    if (i + 1 == fields.size()) {
                        method.addContentLine(");");
                    } else {
                        method.addContentLine(",");
                    }
                }
                method.decreaseContentPadding()
                        .decreaseContentPadding();
            });
        }
    }

    static void valueExpression(Method.Builder method, ValueSchemaType valueType, String source) {
        if (valueType.enumType().isPresent()) {
            method.addContent(enumInputMethodName(valueType.enumType().orElseThrow()))
                    .addContent("(")
                    .addContent(source)
                    .addContent(")");
            return;
        }
        if (valueType.inputType().isPresent()) {
            method.addContent(inputObjectMethodName(valueType.inputType().orElseThrow()))
                    .addContent("(")
                    .addContent(source)
                    .addContent(")");
            return;
        }
        if (valueType.elementType().isPresent()) {
            method.addContent(listInputMethodName(valueType))
                    .addContent("(")
                    .addContent(source)
                    .addContent(")");
            return;
        }

        method.addContent("(")
                .addContent(valueType.javaType().boxed())
                .addContent(") ")
                .addContent(source);
    }

    private static void collectEnumInputTypes(Map<TypeName, EnumSchemaType> result, ValueSchemaType valueType) {
        valueType.enumType().ifPresent(it -> result.putIfAbsent(it.javaType(), it));
        valueType.elementType().ifPresent(it -> collectEnumInputTypes(result, it));
    }

    private static void collectListInputTypes(Map<String, ValueSchemaType> result, ValueSchemaType valueType) {
        if (valueType.list()) {
            result.putIfAbsent(valueType.javaType().resolvedName(), valueType);
        }
        valueType.elementType().ifPresent(it -> collectListInputTypes(result, it));
    }

    private static void inputFieldValue(Method.Builder method, InputSchemaField field) {
        valueExpression(method, field.valueType(), "input.get(\"" + field.graphQlName() + "\")");
    }

    private static String inputObjectMethodName(InputSchemaType inputType) {
        return "input_" + inputType.javaType().fqName().replace('.', '_').replace('$', '_');
    }

    private static String enumInputMethodName(EnumSchemaType enumType) {
        return "enum_" + enumType.javaType().fqName().replace('.', '_').replace('$', '_');
    }

    private static String listInputMethodName(ValueSchemaType listType) {
        return "list_" + listType.javaType().resolvedName().replaceAll("[^A-Za-z0-9]", "_");
    }
}
