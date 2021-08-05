/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

import io.helidon.config.PropertiesConfigParser;

/**
 * Helidon SE Config module.
 *
 * @see io.helidon.config
 */
module io.helidon.config {

    requires java.logging;

    requires transitive java.annotation;

    requires transitive io.helidon.common;
    requires transitive io.helidon.common.reactive;
    requires transitive io.helidon.common.media.type;

    requires io.helidon.common.serviceloader;

    exports io.helidon.config;
    exports io.helidon.config.spi;

    uses io.helidon.config.spi.ConfigMapperProvider;
    uses io.helidon.config.spi.ConfigParser;
    uses io.helidon.config.spi.ConfigFilter;
    uses io.helidon.config.spi.ConfigSourceProvider;
    uses io.helidon.config.spi.OverrideSourceProvider;
    uses io.helidon.config.spi.RetryPolicyProvider;
    uses io.helidon.config.spi.PollingStrategyProvider;
    uses io.helidon.config.spi.ChangeWatcherProvider;

    provides io.helidon.config.spi.ConfigParser with PropertiesConfigParser;

}
