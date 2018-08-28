/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
module io.helidon.mp.health {
    requires java.logging;
    requires java.management;

    requires io.helidon.common;
    requires io.helidon.mp.server;

    requires cdi.api;
    requires javax.inject;
    requires java.ws.rs;
    requires org.glassfish.java.json;
    requires microprofile.config.api;
    requires microprofile.health.api;

    exports io.helidon.microprofile.health;

    provides org.eclipse.microprofile.health.spi.HealthCheckResponseProvider with
            io.helidon.microprofile.health.HealthCheckResponseProviderImpl;
    provides io.helidon.microprofile.server.spi.MpService with io.helidon.microprofile.health.HealthMpService;
}
