/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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
    requires jdk.unsupported;

    requires transitive java.annotation;

    requires io.helidon.common;
    requires transitive io.helidon.common.reactive;

    exports io.helidon.config;
    exports io.helidon.config.spi;

    exports io.helidon.config.internal to io.helidon.config.git;

    uses io.helidon.config.spi.ConfigMapperProvider;
    uses io.helidon.config.spi.ConfigParser;
    uses io.helidon.config.spi.ConfigFilter;
    uses java.nio.file.spi.FileTypeDetector;

    provides io.helidon.config.spi.ConfigParser with io.helidon.config.internal.PropertiesConfigParser;
    provides java.nio.file.spi.FileTypeDetector with io.helidon.config.internal.ConfigFileTypeDetector;

}
