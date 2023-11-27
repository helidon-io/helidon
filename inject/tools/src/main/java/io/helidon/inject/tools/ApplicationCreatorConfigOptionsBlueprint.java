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

package io.helidon.inject.tools;

import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.types.TypeName;

/**
 * Configuration directives and options optionally provided to the {@link io.helidon.inject.tools.spi.ApplicationCreator}.
 */
@Prototype.Blueprint
interface ApplicationCreatorConfigOptionsBlueprint {
    /**
     * The default permitted provider type.
     */
    PermittedProviderType DEFAULT_PERMITTED_PROVIDER_TYPE = PermittedProviderType.ALL;

    /**
     * Determines the application generator's tolerance around the usage of providers.
     *
     * @return provider generation permission type
     */
    @Option.Default("ALL")
    PermittedProviderType permittedProviderTypes();

    /**
     * Only applicable when {@link #permittedProviderTypes()} is set to
     * {@link PermittedProviderType#NAMED}. This is the set of provider names that are explicitly
     * permitted to be generated.
     *
     * @return the allow-listed named providers (which is the FQN of the underlying service type)
     */
    @Option.Singular
    Set<String> permittedProviderNames();

    /**
     * Only applicable when {@link #permittedProviderTypes()} is set to
     * {@link PermittedProviderType#NAMED}. This is the set of qualifier types that are explicitly
     * permitted to be generated.
     *
     * @return the allow-listed qualifier type names
     */
    @Option.Singular
    Set<TypeName> permittedProviderQualifierTypeNames();

}
