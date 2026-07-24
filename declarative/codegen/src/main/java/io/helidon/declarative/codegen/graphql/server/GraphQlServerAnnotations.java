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

import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_DESCRIPTION;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_IGNORE;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_NAME;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_NON_NULL;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SERVER_CONTEXT;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SERVER_ENDPOINT;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SERVER_LISTENER;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SERVER_SCHEMA_URI;

final class GraphQlServerAnnotations {
    private static final Set<TypeName> AUTOMATIC_FIELD_ANNOTATIONS = Set.of(GRAPHQL_DESCRIPTION,
                                                                            GRAPHQL_IGNORE,
                                                                            GRAPHQL_NAME,
                                                                            GRAPHQL_NON_NULL);
    private static final Set<TypeName> GROUP_ROUTE_ANNOTATIONS = Set.of(GRAPHQL_SERVER_ENDPOINT,
                                                                        GRAPHQL_SERVER_LISTENER,
                                                                        GRAPHQL_SERVER_CONTEXT,
                                                                        GRAPHQL_SERVER_SCHEMA_URI);

    private GraphQlServerAnnotations() {
    }

    static boolean hasNonNull(Collection<Annotation> annotations) {
        return Annotations.findFirst(GRAPHQL_NON_NULL, annotations).isPresent();
    }

    static boolean hasNonNull(TypeName type) {
        return Annotations.findFirst(GRAPHQL_NON_NULL, type.annotations())
                .or(() -> Annotations.findFirst(GRAPHQL_NON_NULL, type.inheritedAnnotations()))
                .isPresent();
    }

    static List<Annotation> requestMetadataAnnotations(Set<Annotation> annotations) {
        return annotations.stream()
                .filter(it -> !GROUP_ROUTE_ANNOTATIONS.contains(it.typeName()))
                .sorted()
                .toList();
    }

    static void validateAutomaticFieldAnnotations(TypeInfo typeInfo,
                                                  TypedElementInfo element,
                                                  Set<Annotation> annotations) {
        annotations.stream()
                .filter(annotation -> !AUTOMATIC_FIELD_ANNOTATIONS.contains(annotation.typeName()))
                .findFirst()
                .ifPresent(annotation -> {
                    throw new CodegenException("GraphQL entity field " + typeInfo.typeName().fqName()
                                                       + "." + element.elementName() + " uses annotation "
                                                       + annotation.typeName().fqName()
                                                       + ". Automatic GraphQL fields only support GraphQL schema "
                                                       + "annotations; use an explicit @GraphQlServer.Field resolver "
                                                       + "when field annotations require runtime processing.",
                                               element.originatingElementValue());
                });
    }
}
