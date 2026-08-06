/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.builder.codegen;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.common.types.TypeName;

/**
 * Definition of an option that is a provider (i.e. loaded through registry or service loader).
 */
@Prototype.Blueprint(detach = true)
interface OptionProviderBlueprint {
    /**
     * Type of the provider to lookup.
     *
     * @return provider type
     */
    TypeName providerType();

    /**
     * Configured provider identity.
     * The compatibility default identifies providers by both type and name.
     *
     * @return configured provider identity
     */
    @Option.Default("TYPE_AND_NAME")
    @Api.Internal
    Option.Provider.Identity providerIdentity();

    /**
     * Configured provider outer configuration form.
     * The compatibility default derives the accepted form from the provider identity.
     *
     * @return configured provider outer configuration form
     */
    @Option.Default("AUTO")
    @Api.Internal
    Option.Provider.ConfigForm configForm();

    /**
     * Whether to discover services by default.
     *
     * @return whether to discover services
     */
    boolean discoverServices();
}
