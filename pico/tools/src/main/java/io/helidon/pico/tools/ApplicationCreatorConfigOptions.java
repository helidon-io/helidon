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

import java.util.Set;

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;
import io.helidon.pico.types.TypeName;

/**
 * Configuration directives and options optionally provided to the {@link ApplicationCreator}.
 */
@Builder
public interface ApplicationCreatorConfigOptions {

    /**
     * Defines how the generator should allow the presence of {@link jakarta.inject.Provider}'s or
     * {@link io.helidon.pico.InjectionPointProvider}'s. Since providers add a level of non-deterministic behavior
     * to the system it is required for the application to explicitly permit whether this feature should be permitted.
     */
    enum PermittedProviderType {

        /**
         * No provider types are permitted.
         */
        NONE,

        /**
         * Each individual provider needs to be white-listed.
         */
        NAMED,

        /**
         * Allows all/any provider type the system recognizes.
         */
        ALL

    }

    /**
     * Determines the application generator's tolerance around the usage of providers.
     *
     * @return provider generation permission type
     */
    PermittedProviderType permittedProviderTypes();

    /**
     * Only applicable when {@link #permittedProviderTypes()} is set to
     * {@link ApplicationCreatorConfigOptions.PermittedProviderType#NAMED}. This is the set of provider names that are explicitly
     * permitted to be generated.
     *
     * @return the white-listed named providers (which is the FQN of the underlying service type)
     */
    @Singular
    Set<String> permittedProviderNames();

    /**
     * Only applicable when {@link #permittedProviderTypes()} is set to
     * {@link ApplicationCreatorConfigOptions.PermittedProviderType#NAMED}. This is the set of qualifier types that are explicitly
     * permitted to be generated.
     *
     * @return the white-listed qualifier type names
     */
    @Singular
    Set<TypeName> permittedProviderQualifierTypeNames();

}
