/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.api;

import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;

/**
 * This is the bootstrap needed to provide to {@code Services} initialization.
 *
 * @see io.helidon.inject.spi.InjectionServicesProvider
 * @see io.helidon.inject.api.InjectionServices#globalBootstrap()
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Prototype.Blueprint
interface BootstrapBlueprint {

    /**
     * Provides the base primordial bootstrap configuration to the {@link io.helidon.inject.spi.InjectionServicesProvider}.
     * The provider will then bootstrap {@link InjectionServices} using this bootstrap instance.
     * then default values will be used accordingly.
     *
     * @return the bootstrap helidon configuration
     */
    Optional<Config> config();

    /**
     * In certain conditions Injection services should be initialized but not started (i.e., avoiding calls to {@code PostConstruct}
     * etc.). This can be used in special cases where the normal Injection startup should limit lifecycle up to a given phase. Normally
     * one should not use this feature - it is mainly used in Injection tooling (e.g., the injection maven-plugin).
     *
     * @return the phase to stop at during lifecycle
     */
    Optional<Phase> limitRuntimePhase();

}
