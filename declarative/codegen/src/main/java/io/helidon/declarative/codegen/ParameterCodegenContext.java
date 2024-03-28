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

package io.helidon.declarative.codegen;

import java.util.List;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

/**
 * Information related to parameter processing.
 */
public interface ParameterCodegenContext {
    /**
     * Parameter annotations.
     *
     * @return annotations of the parameter
     */
    List<Annotation> parameterAnnotations();

    /**
     * Type of the parameter.
     *
     * @return parameter type
     */
    TypeName parameterType();

    /**
     * Builder of the class that is being processed, to allow addition of methods and fields.
     *
     * @return class model builder
     */
    ClassModel.Builder classBuilder();

    /**
     * Builder of the current method that should complete the parameter assignment.
     *
     * @return content builder of the method body
     */
    ContentBuilder<?> contentBuilder();

    /**
     * Name of the server request parameter.
     *
     * @return name of server request
     */
    String serverRequestParamName();

    /**
     * Name of the server response parameter.
     *
     * @return name of server response
     */
    String serverResponseParamName();

    /**
     * Method index (related to order of processing), to allow generation of unique names.
     *
     * @return method index
     * @see #paramIndex()
     */
    int methodIndex();
    /**
     * Method index (related to order of processing), to allow generation of unique names.
     *
     * @return method index
     * @see #paramIndex()
     */
    int paramIndex();
}
