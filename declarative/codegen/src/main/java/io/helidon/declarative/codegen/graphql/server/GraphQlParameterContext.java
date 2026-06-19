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

import java.util.Set;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

/**
 * Information about a GraphQL resolver method parameter.
 */
public interface GraphQlParameterContext {
    /**
     * Parameter annotations.
     *
     * @return annotations of the parameter
     */
    Set<Annotation> annotations();

    /**
     * Parameter type.
     *
     * @return parameter type
     */
    TypeName parameterType();

    /**
     * Type name of the GraphQL endpoint that declares the resolver method.
     *
     * @return endpoint type name
     */
    TypeName endpointType();

    /**
     * Java resolver method name.
     *
     * @return resolver method name
     */
    String methodName();

    /**
     * Unique Java resolver method name as seen by code generation.
     *
     * @return unique resolver method name
     */
    String uniqueMethodName();

    /**
     * Java parameter name.
     *
     * @return parameter name
     */
    String paramName();

    /**
     * Parameter index.
     *
     * @return parameter index
     */
    int paramIndex();

    /**
     * Resolver method kind.
     *
     * @return resolver method kind
     */
    GraphQlResolverKind resolverKind();

    /**
     * Whether the parameter belongs to a child field resolver.
     *
     * @return whether this is a child field resolver parameter
     */
    default boolean childResolver() {
        return resolverKind() == GraphQlResolverKind.FIELD;
    }
}
