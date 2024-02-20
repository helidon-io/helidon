/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.spi;

import java.util.List;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

/**
 * Java {@link java.util.ServiceLoader} provider interface to add support for parameters of HTTP endpoint
 * methods.
 * The parameter retrieval is expected to be code-generated.
 */
public interface HttpParameterCodegenProvider {
    /**
     * Code generate parameter assignment.
     * The content builder's current content will be something like {@code var someParam =}, and
     * this method is responsible for adding the appropriate extraction of parameter from
     * server request, server response, or other component.
     *
     * @param parameterAnnotations    annotations of the parameter being processed
     * @param parameterType           type of the parameter being processed
     * @param classBuilder            builder of the content of the class (i.e. when constants need to be added)
     * @param contentBuilder          builder of the parameter assignment
     * @param serverRequestParamName  name of the parameter of WebServer server request
     * @param serverResponseParamName name of the parameter of WebServer server response
     * @param methodIndex             index of the method being processed (to be able to have unique constant names)
     * @param paramIndex              index of the parameter being processed (to be able to have unique constant names)
     * @return whether code was generated, return {@code false} if the parameter is not supported by this provider
     */
    boolean codegen(List<Annotation> parameterAnnotations,
                    TypeName parameterType,
                    ClassModel.Builder classBuilder,
                    ContentBuilder<?> contentBuilder,
                    String serverRequestParamName,
                    String serverResponseParamName,
                    int methodIndex,
                    int paramIndex);
}
