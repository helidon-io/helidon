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

package io.helidon.pico.config.services;

import java.util.ServiceLoader;
import java.util.function.Supplier;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.pico.spi.ext.Resetable;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Provides access to the global {@link io.helidon.pico.config.services.ConfigBeanRegistry} instance.
 */
@Singleton
public class ConfigBeanRegistryProvider implements Provider<ConfigBeanRegistry>,
                                                   Supplier<ConfigBeanRegistry> {
    private static final LazyValue<ConfigBeanRegistry> INSTANCE = LazyValue.create(ConfigBeanRegistryProvider::load);

    private static ConfigBeanRegistry load() {
        return HelidonServiceLoader.create(ServiceLoader.load(ConfigBeanRegistry.class, ConfigBeanRegistry.class.getClassLoader()))
                .asList()
                .stream()
                .findFirst().orElseThrow();
    }

    @Override
    public ConfigBeanRegistry get() {
        return getInstance();
    }

    /**
     * @return The global {@link io.helidon.pico.config.services.ConfigBeanRegistry} instance.
     */
    public static ConfigBeanRegistry getInstance() {
        return INSTANCE.get();
    }

    /**
     * Reset the config bean registry.
     */
    public static void reset() {
        ConfigBeanRegistry cbr = getInstance();
        if (cbr instanceof Resetable) {
            ((Resetable) cbr).reset();
        }
    }

}
