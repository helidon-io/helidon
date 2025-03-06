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

package io.helidon.declarative.codegen;

import java.util.List;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

record ParamCodegenContextImpl(List<Annotation> qualifiers,
                               TypeName parameterType,
                               ClassModel.Builder classBuilder,
                               ContentBuilder<?> contentBuilder,
                               String serverRequestParamName,
                               String serverResponseParamName,
                               TypeName endpointType,
                               String methodName,
                               String paramName,
                               int methodIndex,
                               int paramIndex) implements ParameterCodegenContext {
}
