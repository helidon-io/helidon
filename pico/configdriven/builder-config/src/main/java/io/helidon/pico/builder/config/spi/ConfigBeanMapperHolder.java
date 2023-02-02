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

package io.helidon.pico.builder.config.spi;

import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;

/**
 * Provides access to the {@link ConfigBeanMapper}.
 *
 * @see ConfigBeanMapperProvider
 */
public class ConfigBeanMapperHolder {
    private static final LazyValue<Optional<ConfigBeanMapper>> INSTANCE = LazyValue.create(ConfigBeanMapperHolder::load);

    private ConfigBeanMapperHolder() {
    }

    /**
     * Provides the global service-loader instance of {@link ConfigBeanMapper}.
     * <p>
     * Note that the current expectation here is that the global instance is capable for mapping any
     * {@link io.helidon.pico.builder.config.ConfigBean}-annotated type. The parameter used for the configBeanType serves
     * to both type case the mapper, and also reserves the possibility that the returned instance may be nuanced per bean
     * type at some point in the future.
     *
     * @param configBeanType the config bean type to map
     * @return the config bean mapper
     */
    public static Optional<ConfigBeanMapper> configBeanMapperFor(
            Class<?> configBeanType) {
        return INSTANCE.get();
    }

    private static Optional<ConfigBeanMapper> load() {
        return HelidonServiceLoader
                .create(ServiceLoader.load(ConfigBeanMapperProvider.class, ConfigBeanMapperProvider.class.getClassLoader()))
                .asList()
                .stream()
                .findFirst()
                .map(ConfigBeanMapperProvider::configBeanMapper);
    }
}
