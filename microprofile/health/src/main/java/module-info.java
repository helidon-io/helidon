/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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
 * Microprofile health module.
 */
module io.helidon.microprofile.health {
    requires java.logging;
    requires java.management;

    requires io.helidon.common;
    requires io.helidon.common.serviceloader;
    requires io.helidon.health;
    requires io.helidon.health.common;
    requires io.helidon.microprofile.server;

    requires jakarta.enterprise.cdi.api;
    requires jakarta.inject.api;
    requires java.ws.rs;
    requires java.json;
    requires microprofile.config.api;
    requires microprofile.health.api;
    requires io.helidon.config.mp;

    exports io.helidon.microprofile.health;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.health to weld.core.impl, io.helidon.microprofile.cdi;

    uses io.helidon.microprofile.health.HealthCheckProvider;

    provides javax.enterprise.inject.spi.Extension with io.helidon.microprofile.health.HealthCdiExtension;
}
