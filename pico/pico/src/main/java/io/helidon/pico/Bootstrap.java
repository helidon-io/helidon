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
     * The provider will then bootstrap its {@link PicoServicesConfig} with any configuration instance provided. If not supplied
     * then default values will be used accordingly.
     *
     * @return the bootstrap helidon configuration
     * @see #realizedPicoConfig()
     */
    Optional<Config> config();

    /**
     * Provides the pico services bootstrap configuration. This is the highest level of configuration that can be provided during
     * bootstrapping.
     *
     * @return the bootstrap pico services configuration.
     * @see #realizedPicoConfig()
     */
    Optional<PicoServicesConfig> picoConfig();

    /**
     * This is the realized configuration (i.e., what will be used at runtime). The implementation will attempt to use the
     * {@link #picoConfig()} if it was provided, and if not will construct a new {@link DefaultPicoServicesConfig} using any
     * provided {@link #config()} for the lower level config attribute getters.
     *
     * @return the realized configuration
     */
    default PicoServicesConfig realizedPicoConfig() {
        // note that this style will still use config() above, it's just built into the impl
        return picoConfig().orElse(DefaultPicoServicesConfig.builder().build());
    }

}
