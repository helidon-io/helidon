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

package io.helidon.declarative.codegen.graphql.server.spi;

import io.helidon.declarative.codegen.graphql.server.GraphQlParameterCodegenContext;
import io.helidon.declarative.codegen.graphql.server.GraphQlParameterContext;

/**
 * Java {@link java.util.ServiceLoader} provider interface to add support for custom parameters of GraphQL resolver
 * methods.
 * <p>
 * Parameters supported by this SPI are resolver-only parameters. They do not contribute arguments to the generated GraphQL
 * schema.
 */
public interface GraphQlParameterCodegenProvider {
    /**
     * Whether this provider supports the parameter.
     *
     * @param context information about the parameter being processed
     * @return whether the provider supports this parameter
     */
    boolean supports(GraphQlParameterContext context);

    /**
     * Code generate the parameter value expression.
     *
     * @param context information about the parameter being processed
     */
    void codegen(GraphQlParameterCodegenContext context);
}
