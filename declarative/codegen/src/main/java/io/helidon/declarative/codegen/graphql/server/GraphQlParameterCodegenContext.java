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
import io.helidon.codegen.classmodel.ContentBuilder;

/**
 * Code generation context for a GraphQL resolver method parameter.
 */
public interface GraphQlParameterCodegenContext extends GraphQlParameterContext {
    /**
     * Builder of the generated GraphQL feature class.
     *
     * @return class model builder
     */
    ClassModel.Builder classBuilder();

    /**
     * Builder of the resolver method invocation.
     * <p>
     * The provider is expected to append a Java expression that supplies the parameter value.
     *
     * @return content builder
     */
    ContentBuilder<?> contentBuilder();

    /**
     * Name of the {@code graphql.schema.DataFetchingEnvironment} parameter in the generated resolver method.
     *
     * @return data fetching environment parameter name
     */
    String environmentParamName();
}
