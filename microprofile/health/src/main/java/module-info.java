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

    requires cdi.api;
    requires javax.inject;
    requires java.ws.rs;
    requires org.glassfish.java.json;
    requires microprofile.config.api;
    requires microprofile.health.api;

    exports io.helidon.microprofile.health;

    uses io.helidon.microprofile.health.HealthCheckProvider;

    provides javax.enterprise.inject.spi.Extension with io.helidon.microprofile.health.HealthCdiExtension;
}
