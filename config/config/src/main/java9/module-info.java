/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
 * config module.
 */
module io.helidon.config {

    requires java.logging;

    requires transitive java.annotation;

    requires transitive io.helidon.common;
    requires transitive io.helidon.common.reactive;
    requires io.helidon.common.serviceloader;
    requires io.helidon.common.media.type;
    requires transitive microprofile.config.api;

    exports io.helidon.config;
    exports io.helidon.config.spi;

    exports io.helidon.config.internal to io.helidon.config.git;

    uses io.helidon.config.spi.ConfigMapperProvider;
    uses io.helidon.config.spi.ConfigParser;
    uses io.helidon.config.spi.ConfigFilter;
    uses io.helidon.config.spi.ConfigSourceProvider;
    uses io.helidon.config.spi.OverrideSourceProvider;
    uses io.helidon.config.spi.RetryPolicyProvider;
    uses io.helidon.config.spi.PollingStrategyProvider;

    uses java.nio.file.spi.FileTypeDetector;

    provides io.helidon.config.spi.ConfigParser with io.helidon.config.internal.PropertiesConfigParser;
    provides java.nio.file.spi.FileTypeDetector with io.helidon.config.internal.ConfigFileTypeDetector;
    provides org.eclipse.microprofile.config.spi.ConfigProviderResolver with io.helidon.config.MpConfigProviderResolver;

}
