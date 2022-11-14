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

package io.helidon.pico.builder.config.spi;

import java.util.ServiceLoader;
import java.util.function.Supplier;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Provides lookup for {@link ConfigBuilderValidator}.
 */
@Singleton
public class ConfigBuilderValidatorProvider implements Provider<ConfigBuilderValidator>,
                                                       Supplier<ConfigBuilderValidator> {
    private static final LazyValue<ConfigBuilderValidator> INSTANCE = LazyValue.create(ConfigBuilderValidatorProvider::load);

    private static ConfigBuilderValidator load() {
        return HelidonServiceLoader.create(ServiceLoader.load(ConfigBuilderValidator.class, ConfigBuilderValidator.class.getClassLoader()))
                .asList()
                .stream()
                .findFirst().orElse(null);
    }

    @Override
    public ConfigBuilderValidator get() {
        return getInstance();
    }

    /**
     * @return The global {@link ConfigBuilderValidator} instance, or null if none found
     */
    public static ConfigBuilderValidator getInstance() {
        return INSTANCE.get();
    }
}
