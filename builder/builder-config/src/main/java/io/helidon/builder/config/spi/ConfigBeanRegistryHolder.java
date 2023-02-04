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

package io.helidon.builder.config.spi;

import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;

/**
 * Provides access to the active {@link io.helidon.builder.config.spi.BasicConfigBeanRegistry} instance.
 *
 * @see BasicConfigBeanRegistry
 */
public class ConfigBeanRegistryHolder {
    private static final LazyValue<Optional<BasicConfigBeanRegistry>> INSTANCE = LazyValue.create(ConfigBeanRegistryHolder::load);

    private ConfigBeanRegistryHolder() {
    }

    /**
     * Provides the global service-loader instance of {@link BasicConfigBeanRegistry}.
     *
     * @param <T> the config bean registry type
     * @return the config bean registry
     */
    @SuppressWarnings("unchecked")
    public static <T extends BasicConfigBeanRegistry> Optional<T> configBeanRegistry() {
        return (Optional<T>) INSTANCE.get();
    }

    private static <T extends BasicConfigBeanRegistry> Optional<T> load() {
        return HelidonServiceLoader
                .create(ServiceLoader.load(ConfigBeanRegistryProvider.class, ConfigBeanRegistryProvider.class.getClassLoader()))
                .asList()
                .stream()
                .findFirst()
                .map(ConfigBeanRegistryProvider::configBeanRegistry);
    }

}
