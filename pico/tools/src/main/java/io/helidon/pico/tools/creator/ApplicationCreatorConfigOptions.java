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

import java.util.Set;

import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.TypeName;

/**
 * Config options available to {@link io.helidon.pico.tools.creator.ApplicationCreator}.
 */
public interface ApplicationCreatorConfigOptions {

    /**
     * Will the generator allow the presence of {@link jakarta.inject.Provider}'s or
     * {@link io.helidon.pico.InjectionPointProvider}'s. Since providers adds a level of non-deterministic behavior
     * to the system it is required for the application to specify whether this should feature be permitted.
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
     * @return Determines the application generator's tolerance around the usage of providers.
     */
    PermittedProviderType getPermittedProviderTypes();

    /**
     * Only applicable when {@link #getPermittedProviderTypes()} is set to
     * {@link io.helidon.pico.tools.creator.ApplicationCreatorConfigOptions.PermittedProviderType#NAMED}.
     *
     * @return the white-listed named providers (which is the FQN of the underlying service type)
     */
    Set<String> getPermittedProviderNames();

    /**
     * Only applicable when {@link #getPermittedProviderTypes()} is set to
     * {@link io.helidon.pico.tools.creator.ApplicationCreatorConfigOptions.PermittedProviderType#NAMED}.
     *
     * @return the white-listed qualifier type names
     */
    Set<TypeName> getPermittedProviderQualifierTypeNames();

    /**
     * Converts a str to the associated {@link io.helidon.pico.tools.creator.ApplicationCreatorConfigOptions.PermittedProviderType}.
     *
     * @param str the str
     * @return the associated type, defaulting to {@link io.helidon.pico.tools.creator.ApplicationCreatorConfigOptions.PermittedProviderType#NONE}
     */
    static PermittedProviderType toPermittedProviderTypes(String str) {
        if (!AnnotationAndValue.hasNonBlankValue(str)) {
            return PermittedProviderType.NONE;
        }

        try {
            return PermittedProviderType.valueOf(str.toUpperCase());
        } catch (Exception e) {
            throw new ToolsException("unable to convert " + str, e);
        }
    }

}
