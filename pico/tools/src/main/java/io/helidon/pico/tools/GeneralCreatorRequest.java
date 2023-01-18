/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.tools;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.Builder;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.pico.types.TypeName;

/**
 * Base interface codegen-related requests.
 */
@Builder
public interface GeneralCreatorRequest extends GeneralCodeGenNames {

    /**
     * Set to true to avoid code-generating, and instead provide the plan for what would be built.
     *
     * @return if set to true then no codegen will occur on disk.
     */
    boolean analysisOnly();

    /**
     * Where codegen should be read and written.
     *
     * @return the code paths to use for reading and writing artifacts
     */
    CodeGenPaths codeGenPaths();

    /**
     * Optionally, any compiler options to pass explicitly to the java compiler. Not applicable during annotation processing.
     *
     * @return explicit compiler options
     */
    Optional<CompilerOptions> compilerOptions();

    /**
     * The target fully qualified class name for the service implementation to be built or analyzed.
     * <p>
     * Assumptions:
     * <ul>
     * <li>The service type is available for reflection/introspection at creator invocation time (typically at
     * compile time).
     * </ul>
     *
     * @return the collection of service type names to generate
     */
    List<TypeName> serviceTypeNames();

    /**
     * Should exceptions be thrown, or else captured in the response under {@link ActivatorCreatorResponse#error()}.
     * The default is true.
     *
     * @return true if the creator should fail, otherwise the response will show the error
     */
    @ConfiguredOption("true")
    boolean throwOnFailure();

    /**
     * Provides the generator (used to append to code generated artifacts in {@code javax.annotation.processing.Generated}
     * annotations).
     *
     * @return the generator name
     */
    String generator();

}
