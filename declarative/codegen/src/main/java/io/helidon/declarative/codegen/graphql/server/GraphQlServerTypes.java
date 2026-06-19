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
import io.helidon.common.types.TypedElementInfo;

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
                             ResolverParameterKind kind) {
    }

    enum ResolverParameterKind {
        ARGUMENT,
        SOURCE,
        ENVIRONMENT,
        HELIDON_CONTEXT,
        EXECUTION_CONTEXT,
        SECURITY_CONTEXT
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
                    Optional<InputSchemaType> inputType) {
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

    record EnumSchemaType(String graphQlName,
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
                            Optional<InputSchemaType> inputType) {
    }

    record InputSchemaFieldType(String graphQlName,
                                Optional<InputSchemaType> inputType) {
    }

    record EnumValue(String graphQlName,
                     Optional<String> description) {
    }
}
