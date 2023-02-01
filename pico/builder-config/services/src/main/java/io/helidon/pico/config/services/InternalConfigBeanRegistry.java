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

package io.helidon.pico.config.services;

import io.helidon.pico.config.spi.ConfigBeanInfo;
import io.helidon.pico.PicoServices;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.spi.ext.Resetable;

/**
 * Internal, SPI-like functionality.
 * Note: normally these methods are deemed internal-only, and should therefore not be called directly by end-consumers
 * of the {@link io.helidon.pico.config.services.ConfigBeanRegistry} API.
 */
public interface InternalConfigBeanRegistry extends ConfigBeanRegistry, Resetable {

    /**
     * Binds a {@link io.helidon.pico.config.services.ConfiguredServiceProvider} to the
     * {@link ConfigBeanInfo} annotation it is configured by.
     *
     * @param configuredServiceProvider the configured service provider
     * @param configuredByQualifier the qualifier associated with the {@link io.helidon.pico.config.spi.ConfigBeanInfo}
     * @param metaConfigBeanInfo the meta config bean info associated with this service provider
     */
    void bind(ConfiguredServiceProvider<?, ?> configuredServiceProvider,
              QualifierAndValue configuredByQualifier,
              ConfigBeanInfo metaConfigBeanInfo);

    /**
     * The first call to this initialize the bean registry, by loading all the backing configuration from the config
     * subsystem.
     *
     * @param picoServices the pico services instance
     */
    void initialize(PicoServices picoServices);

}
