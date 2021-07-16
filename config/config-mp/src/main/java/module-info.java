/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
 * Implementation of the non-CDI parts of Eclipse MicroProfile Config specification.
 */
module io.helidon.config.mp {
    requires java.logging;
    requires io.helidon.common;
    requires io.helidon.config;
    requires io.helidon.config.yaml.mp;
    requires transitive microprofile.config.api;
    requires java.annotation;
    requires io.helidon.common.serviceloader;

    exports io.helidon.config.mp;
    exports io.helidon.config.mp.spi;

    uses org.eclipse.microprofile.config.spi.ConfigSource;
    uses org.eclipse.microprofile.config.spi.ConfigSourceProvider;
    uses org.eclipse.microprofile.config.spi.Converter;
    uses io.helidon.config.mp.spi.MpConfigFilter;
    uses io.helidon.config.spi.ConfigParser;

    provides org.eclipse.microprofile.config.spi.ConfigProviderResolver with io.helidon.config.mp.MpConfigProviderResolver;
}