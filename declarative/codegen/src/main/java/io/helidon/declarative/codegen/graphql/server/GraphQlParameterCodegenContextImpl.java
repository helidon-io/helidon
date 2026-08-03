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

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

record GraphQlParameterCodegenContextImpl(GraphQlParameterContext delegate,
                                          ClassModel.Builder classBuilder,
                                          ContentBuilder<?> contentBuilder,
                                          String environmentParamName) implements GraphQlParameterCodegenContext {
    @Override
    public Set<Annotation> annotations() {
        return delegate.annotations();
    }

    @Override
    public TypeName parameterType() {
        return delegate.parameterType();
    }

    @Override
    public TypeName endpointType() {
        return delegate.endpointType();
    }

    @Override
    public String methodName() {
        return delegate.methodName();
    }

    @Override
    public String uniqueMethodName() {
        return delegate.uniqueMethodName();
    }

    @Override
    public String paramName() {
        return delegate.paramName();
    }

    @Override
    public int paramIndex() {
        return delegate.paramIndex();
    }

    @Override
    public GraphQlResolverKind resolverKind() {
        return delegate.resolverKind();
    }
}
