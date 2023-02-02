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

/**
 * Pico config-driven-services module.
 */
module io.helidon.pico.config.services {
    requires static jakarta.inject;
    requires static jakarta.annotation;
    requires static io.helidon.config.metadata;

    requires transitive io.helidon.config;
    requires transitive io.helidon.pico.builder.config;
    requires transitive io.helidon.pico;
    requires transitive io.helidon.pico.services;
    requires transitive io.helidon.pico.types;
    requires transitive io.helidon.builder;

    exports io.helidon.pico.config.services;

    uses io.helidon.pico.builder.config.spi.ConfigBeanRegistryProvider;
    uses io.helidon.pico.builder.config.spi.StringValueParserProvider;
    uses io.helidon.pico.builder.config.spi.ConfigBeanMapperProvider;
    uses io.helidon.pico.builder.config.spi.ConfigResolverProvider;

    provides io.helidon.pico.builder.config.spi.ConfigBeanRegistryProvider
            with io.helidon.pico.config.services.DefaultConfigBeanRegistryProvider;
    provides io.helidon.pico.builder.config.spi.ConfigResolverProvider
            with io.helidon.pico.config.services.DefaultConfigResolverProvider;
    provides io.helidon.pico.builder.config.spi.StringValueParserProvider
            with io.helidon.pico.config.services.DefaultStringValueParserProvider;
    provides io.helidon.pico.builder.config.spi.ConfigBeanBuilderValidatorProvider
            with io.helidon.pico.config.services.DefaultConfigBeanBuilderValidatorProvider;
}
