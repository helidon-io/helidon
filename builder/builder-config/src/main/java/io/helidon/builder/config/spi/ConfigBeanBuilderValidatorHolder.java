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

package io.helidon.builder.config.spi;

import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;

/**
 * Provides access to the {@link ConfigBeanBuilderValidator}.
 *
 * @see ConfigBeanBuilderValidatorProvider
 */
public class ConfigBeanBuilderValidatorHolder {
    private static final LazyValue<Optional<ConfigBeanBuilderValidator<?>>> INSTANCE =
            LazyValue.create(ConfigBeanBuilderValidatorHolder::load);

    private ConfigBeanBuilderValidatorHolder() {
    }

    /**
     * Provides the global service-loader instance of {@link ConfigBeanBuilderValidator}.
     * <p>
     * Note that the current expectation here is that the global instance is capable for validating any
     * {@link io.helidon.builder.config.ConfigBean}-annotated type. The parameter used for the configBeanBuilderType serves
     * to both type case the validator, and also reserves the possibility that the returned instance may be nuanced per builder
     * type at some point in the future.
     *
     * @param configBeanBuilderType the config bean builder type to validate
     * @param <CBB> the config bean builder type
     * @return the config bean builder validator
     */
    @SuppressWarnings({"rawTypes", "unchecked"})
    public static <CBB> Optional<ConfigBeanBuilderValidator<CBB>> configBeanValidatorFor(
            Class<CBB> configBeanBuilderType) {
        return (Optional) INSTANCE.get();
    }

    private static Optional<ConfigBeanBuilderValidator<?>> load() {
        Optional<ConfigBeanBuilderValidatorProvider> provider = HelidonServiceLoader
                .create(ServiceLoader.load(ConfigBeanBuilderValidatorProvider.class,
                                           ConfigBeanBuilderValidatorProvider.class.getClassLoader()))
                .asList()
                .stream()
                .findFirst();
        if (provider.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(provider.get().configBeanBuilderValidator());
    }

}
