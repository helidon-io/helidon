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
 * Provides access to the {@link ConfigResolver}.
 *
 * @see ConfigResolverProvider
 */
public class ConfigResolverHolder {
    private static final LazyValue<Optional<ConfigResolver>> INSTANCE = LazyValue.create(ConfigResolverHolder::load);

    private ConfigResolverHolder() {
    }

    /**
     * Provides the global service-loader instance of {@link ConfigResolver}.
     *
     * @return the config resolver
     */
    public static Optional<ConfigResolver> configResolver() {
        return INSTANCE.get();
    }

    private static Optional<ConfigResolver> load() {
        return HelidonServiceLoader
                .create(ServiceLoader.load(ConfigResolverProvider.class, ConfigResolverProvider.class.getClassLoader()))
                .asList()
                .stream()
                .findFirst()
                .map(ConfigResolverProvider::configResolver);
    }

}
