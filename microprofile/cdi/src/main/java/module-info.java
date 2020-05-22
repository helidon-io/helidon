/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import javax.enterprise.inject.se.SeContainerInitializer;

import io.helidon.microprofile.cdi.HelidonContainerInitializer;

/**
 * CDI implementation enhancements for Helidon MP.
 */
module io.helidon.microprofile.cdi {
    // needed for Unsafe used from Weld
    requires jdk.unsupported;
    requires java.logging;
    requires jakarta.enterprise.cdi.api;

    requires io.helidon.common;
    requires io.helidon.config;
    requires io.helidon.config.mp;

    requires weld.core.impl;
    requires weld.spi;
    requires weld.environment.common;
    requires weld.se.core;
    requires io.helidon.common.context;
    requires jakarta.inject.api;
    requires microprofile.config.api;

    exports io.helidon.microprofile.cdi;

    uses javax.enterprise.inject.spi.Extension;

    provides SeContainerInitializer with HelidonContainerInitializer;
}
