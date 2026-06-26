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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.graphql.server.spi.GraphQlParameterCodegenProvider;

final class GraphQlServerTypes {
    private GraphQlServerTypes() {
    }

    enum OperationKind {
        QUERY("query"),
        MUTATION("mutation"),
        FIELD("field");

        private final String label;

        OperationKind(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    record Resolvers(List<Operation> queries,
                     List<Operation> mutations,
                     List<Operation> fieldResolvers,
                     Set<String> queryNames,
                     Set<String> mutationNames) {
    }

    record EndpointDeclaration(TypeInfo typeInfo,
                               Set<Annotation> annotations,
                               GroupKey groupKey,
                               String schemaUri) {
    }

    record GroupKey(String listener,
                    String context) {
    }

    record GraphQlGroup(GroupKey key,
                        String schemaUri,
                        GraphQlServerExtension.SchemaTypes schemaTypes,
                        List<GraphQlEndpoint> endpoints) {
        GraphQlEndpoint primaryEndpoint() {
            return endpoints.getFirst();
        }

        List<Operation> queries() {
            return endpoints.stream()
                    .flatMap(endpoint -> endpoint.queries().stream())
                    .toList();
        }

        List<Operation> mutations() {
            return endpoints.stream()
                    .flatMap(endpoint -> endpoint.mutations().stream())
                    .toList();
        }

        List<Operation> operations() {
            return endpoints.stream()
                    .flatMap(endpoint -> endpoint.operations().stream())
                    .toList();
        }
    }

    record GraphQlEndpoint(TypeInfo typeInfo,
                           Set<Annotation> annotations,
                           List<Operation> queries,
                           List<Operation> mutations,
                           List<Operation> fieldResolvers) {
        List<Operation> operations() {
            List<Operation> result = new ArrayList<>(queries);
            result.addAll(mutations);
            result.addAll(fieldResolvers);
            return result;
        }
    }

    record Operation(OperationKind kind,
                     TypeInfo endpoint,
                     TypedElementInfo method,
                     List<Annotation> annotations,
                     String graphQlName,
                     String uniqueName,
                     String descriptorMethodName,
                     List<ResolverParameter> parameters) {
        List<Argument> arguments() {
            return parameters.stream()
                    .flatMap(parameter -> parameter.argument().stream())
                    .toList();
        }
    }

    record ResolverParameter(TypedElementInfo parameter,
                             Optional<Argument> argument,
                             Optional<TypeInfo> sourceType,
                             Optional<GraphQlParameterCodegenProvider> provider,
                             GraphQlParameterContext context,
                             ResolverParameterKind kind) {
    }

    enum ResolverParameterKind {
        ARGUMENT,
        SOURCE,
        ENVIRONMENT,
        HELIDON_CONTEXT,
        EXECUTION_CONTEXT,
        SECURITY_CONTEXT,
        CUSTOM
    }

    record SourceParameter(int index,
                           TypedElementInfo parameter,
                           TypeInfo typeInfo) {
    }

    record Argument(TypedElementInfo parameter,
                    String graphQlName,
                    List<Annotation> annotations,
                    Optional<String> defaultValue,
                    boolean nonNull,
                    String schemaType,
                    ValueSchemaType valueType) {
    }

    interface SchemaType {
        String graphQlName();
    }

    record ObjectSchemaType(TypeName javaType,
                            String graphQlName,
                            Optional<String> description,
                            List<SchemaField> fields,
                            Object originatingElement) implements SchemaType {
    }

    record EnumSchemaType(TypeName javaType,
                          String graphQlName,
                          Optional<String> description,
                          List<EnumValue> values) implements SchemaType {
    }

    record ScalarSchemaType(TypeName javaType,
                            String graphQlName,
                            Optional<String> description,
                            Object originatingElement) implements SchemaType {
    }

    record InputSchemaType(TypeName javaType,
                           String graphQlName,
                           Optional<String> description,
                           List<InputSchemaField> fields,
                           Object originatingElement) {
    }

    record SchemaField(String graphQlName,
                       String schemaType,
                       Optional<String> description,
                       List<Argument> arguments,
                       Optional<String> accessor,
                       Optional<String> resolver) {
    }

    record InputSchemaField(TypedElementInfo element,
                            String graphQlName,
                            String schemaType,
                            Optional<String> description,
                            ValueSchemaType valueType) {
    }

    record InputSchemaFieldType(String graphQlName,
                                ValueSchemaType valueType) {
    }

    record ValueSchemaType(TypeName javaType,
                           String graphQlName,
                           Optional<EnumSchemaType> enumType,
                           Optional<InputSchemaType> inputType,
                           Optional<ValueSchemaType> elementType) {
        boolean list() {
            return elementType.isPresent();
        }
    }

    record EnumValue(String javaName,
                     String graphQlName,
                     Optional<String> description) {
    }

    static Optional<String> propertyName(TypedElementInfo method) {
        String methodName = method.elementName();
        if (isPropertyGetter(methodName)) {
            return Optional.of(nameFromPropertyGetter(methodName));
        }
        if (isBooleanPropertyGetter(methodName, method.typeName())) {
            return Optional.of(nameFromBooleanPropertyGetter(methodName));
        }
        return Optional.empty();
    }

    private static boolean isPropertyGetter(String methodName) {
        return methodName.startsWith("get")
                && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))
                && !"getClass".equals(methodName);
    }

    private static boolean isBooleanPropertyGetter(String methodName, TypeName typeName) {
        TypeName boxed = typeName.boxed();
        return methodName.startsWith("is")
                && methodName.length() > 2
                && Character.isUpperCase(methodName.charAt(2))
                && boxed.equals(TypeNames.BOXED_BOOLEAN);
    }

    private static String nameFromPropertyGetter(String methodName) {
        return lowerFirstProperty(methodName.substring(3));
    }

    private static String nameFromBooleanPropertyGetter(String methodName) {
        return lowerFirstProperty(methodName.substring(2));
    }

    private static String lowerFirstProperty(String propertyName) {
        char firstChar = propertyName.charAt(0);
        if (propertyName.length() == 1) {
            return String.valueOf(Character.toLowerCase(firstChar));
        }
        if (!Character.isUpperCase(propertyName.charAt(1))) {
            return Character.toLowerCase(firstChar) + propertyName.substring(1);
        }
        return propertyName;
    }

    static void appendObject(StringBuilder result, ObjectSchemaType objectType) {
        appendDescription(result, 0, objectType.description());
        result.append("type ")
                .append(objectType.graphQlName())
                .append(" {\n");
        for (SchemaField field : objectType.fields()) {
            appendDescription(result, 2, field.description());
            result.append("  ")
                    .append(field.graphQlName());
            appendArguments(result, field.arguments());
            result.append(": ")
                    .append(field.schemaType())
                    .append('\n');
        }
        result.append("}\n");
    }

    static void appendInput(StringBuilder result, InputSchemaType inputType) {
        appendDescription(result, 0, inputType.description());
        result.append("input ")
                .append(inputType.graphQlName())
                .append(" {\n");
        for (InputSchemaField field : inputType.fields()) {
            appendDescription(result, 2, field.description());
            result.append("  ")
                    .append(field.graphQlName())
                    .append(": ")
                    .append(field.schemaType())
                    .append('\n');
        }
        result.append("}\n");
    }

    static void appendEnum(StringBuilder result, EnumSchemaType enumType) {
        appendDescription(result, 0, enumType.description());
        result.append("enum ")
                .append(enumType.graphQlName())
                .append(" {\n");
        for (EnumValue value : enumType.values()) {
            appendDescription(result, 2, value.description());
            result.append("  ")
                    .append(value.graphQlName())
                    .append('\n');
        }
        result.append("}\n");
    }

    static void appendScalar(StringBuilder result, ScalarSchemaType scalarType) {
        appendDescription(result, 0, scalarType.description());
        result.append("scalar ")
                .append(scalarType.graphQlName())
                .append('\n');
    }

    static void appendArguments(StringBuilder result, List<Argument> arguments) {
        if (arguments.isEmpty()) {
            return;
        }
        result.append('(');
        for (int i = 0; i < arguments.size(); i++) {
            Argument argument = arguments.get(i);
            if (i > 0) {
                result.append(", ");
            }
            result.append(argument.graphQlName())
                    .append(": ")
                    .append(argument.schemaType());
            argument.defaultValue().ifPresent(it -> result.append(" = ").append(it));
        }
        result.append(')');
    }

    private static void appendDescription(StringBuilder result, int indent, Optional<String> description) {
        description.ifPresent(it -> result.append(" ".repeat(indent))
                .append('"')
                .append(escapeDescription(it))
                .append("\"\n"));
    }

    private static String escapeDescription(String description) {
        return description.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
