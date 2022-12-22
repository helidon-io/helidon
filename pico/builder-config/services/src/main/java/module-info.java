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

module io.helidon.pico.config.services {
    requires static jakarta.inject;
    requires static jakarta.annotation;

    requires static io.helidon.pico.api;
    requires static io.helidon.pico.builder.api;
    requires static io.helidon.pico.config.api;

    requires transitive io.helidon.pico;
    requires transitive io.helidon.pico.config.spi;
    requires transitive io.helidon.config;

    exports io.helidon.pico.config.services;
    exports io.helidon.pico.config.services.impl;

//    uses io.helidon.pico.config.services.ConfigBeanRegistry;
//    uses io.helidon.pico.config.services.StringValueParser;
//    uses io.helidon.pico.config.spi.ConfigBeanMapper;
//    uses io.helidon.pico.config.spi.ConfigResolver;
//
    provides io.helidon.pico.config.services.ConfigBeanRegistry
            with io.helidon.pico.config.services.impl.DefaultConfigBeanRegistry;
    provides io.helidon.pico.config.spi.ConfigResolver
            with io.helidon.pico.config.services.impl.DefaultConfigResolver;
    provides io.helidon.pico.config.services.StringValueParser
            with io.helidon.pico.config.services.impl.DefaultStringValueParsers;
    provides io.helidon.pico.config.spi.ConfigBuilderValidator
            with io.helidon.pico.config.services.impl.DefaultConfigBuilderValidator;
}
