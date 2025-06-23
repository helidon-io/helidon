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

package io.helidon.declarative.codegen.http.webserver.spi;

import io.helidon.declarative.codegen.http.webserver.ParameterCodegenContext;

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
     * @param context information about the parameter being processed
     * @return whether code was generated, return {@code false} if the parameter is not supported by this provider
     */
    boolean codegen(ParameterCodegenContext context);
}
