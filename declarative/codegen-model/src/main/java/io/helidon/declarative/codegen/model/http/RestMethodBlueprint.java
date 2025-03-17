/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.model.http;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Definition of a rest method.
 * Rest method is a method on an interface or a class annotation with HTTP method annotation.
 */
@Prototype.Blueprint
interface RestMethodBlueprint extends HttpAnnotatedBlueprint {
    /**
     * HTTP Status (server side only) annotation on the method.
     *
     * @return status to return
     */
    Optional<HttpStatus> status();

    /**
     * Type returned by this method.
     *
     * @return return type of the method
     */
    TypeName returnType();

    /**
     * Method name.
     *
     * @return name of the method
     */
    String name();

    /**
     * Unique name of the method (used for generated code).
     * If there is more than one method with the same name within a type, the second (and further) methods
     * will have an index based unique name generated.
     *
     * @return unique name of this method for generated code
     */
    String uniqueName();

    /**
     * HTTP method of this method.
     *
     * @return HTTP method
     */
    HttpMethod httpMethod();

    /**
     * All parameters of this method.
     *
     * @return method parameters
     */
    @Option.Singular
    List<RestMethodParameter> parameters();

    /**
     * Entity parameter (if defined).
     *
     * @return method entity parameter
     */
    Optional<RestMethodParameter> entityParameter();

    /**
     * Header parameters (if defined).
     *
     * @return header parameters
     */
    @Option.Singular
    List<RestMethodParameter> headerParameters();

    /**
     * Query parameters (if defined).
     *
     * @return query parameters
     */
    @Option.Singular
    List<RestMethodParameter> queryParameters();

    /**
     * Path parameters (if defined).
     *
     * @return path parameters
     */
    @Option.Singular
    List<RestMethodParameter> pathParameters();

    /**
     * Method element info.
     *
     * @return method element info
     */
    TypedElementInfo method();

    /**
     * Owning type info.
     *
     * @return type info
     */
    TypeInfo type();
}
