/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.tools.creator;

import java.util.Collection;

import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.types.TypeName;

/**
 * General base interface for any codegen-related create activity.
 */
public interface GeneralCreatorRequest extends GeneralCodeGenNames {
    /**
     * Tag controlling whether we fail on error.
     */
    String TAG_FAIL_ON_ERROR = PicoServicesConfig.FQN + ".failOnError";
    /**
     * Tag controlling whether we fail on warnings.
     */
    String TAG_FAIL_ON_WARNING = PicoServicesConfig.FQN + ".failOnWarning";

    /**
     * @return if set to true then no codegen will occur on disk.
     */
    boolean isAnalysisOnly();

    /**
     * Where codegen should be read & written.
     *
     * @return the code gen paths to use
     */
    CodeGenPaths getCodeGenPaths();

    /**
     * Optionally, any compiler options to pass explicitly to the compiler. Not applicable during annotation processing.
     *
     * @return explicit compiler options
     */
    CompilerOptions getCompilerOptions();

    /**
     * The target fully qualified class name for the service implementation to be built or analyzed.
     * <p/>
     * Assumptions:
     * <li>The service type is available for reflection/introspection at creator invocation time (typically at
     * compile time).
     *
     * @return the collection of service type names to generate
     */
    Collection<TypeName> getServiceTypeNames();

    /**
     * Should exceptions be thrown, or else captured in the response under {@link ActivatorCreatorResponse#getError()}.
     * The default is true.
     *
     * @return true if the creator should fail, otherwise the response will show the error
     */
    boolean isFailOnError();

    /**
     * Provides the generator (used to append to code generated artifacts in {@link javax.annotation.processing.Generated}
     * annotations).
     *
     * @return the generator name
     */
    String getGenerator();

}
