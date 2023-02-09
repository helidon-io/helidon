/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico;

import java.util.Optional;

import io.helidon.builder.Builder;
import io.helidon.common.config.Config;

/**
 * This is the bootstrap needed to provide to {@code Pico} initialization.
 *
 * @see io.helidon.pico.spi.PicoServicesProvider
 * @see io.helidon.pico.PicoServices#globalBootstrap()
 */
@Builder
public interface Bootstrap {

    /**
     * Provides the base primordial bootstrap configuration to the {@link io.helidon.pico.spi.PicoServicesProvider}.
     * The provider will then bootstrap {@link io.helidon.pico.PicoServices} using this bootstrap instance.
     * then default values will be used accordingly.
     *
     * @return the bootstrap helidon configuration
     */
    Optional<Config> config();

    /**
     * In certain conditions Pico services should be initialized but not started (i.e., avoiding calls to {@code PostConstruct}
     * etc). This can be used in special cases where the normal Pico startup should limit lifecycle up to a given phase. Normally
     * one should not use this feature - it is mainly used in Pico tooling (e.g., the pico-maven-plugin).
     *
     * @return the phase to stop at during lifecycle
     */
    Optional<Phase> limitRuntimePhase();

}
