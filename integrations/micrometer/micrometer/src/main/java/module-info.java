/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Preview;

/**
 * Support for Micrometer in Helidon SE.
 */
@Features.Preview
@Features.Name("Micrometer")
@Features.Description("Micrometer integration")
@Features.Flavor(HelidonFlavor.SE)
@Features.Path("Micrometer")
module io.helidon.integrations.micrometer {

    requires io.helidon.config;
    requires io.helidon.http;
    requires io.helidon.webserver.cors;
    requires java.logging;
    requires micrometer.core;
    requires micrometer.registry.prometheus;
    requires simpleclient;

    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;
    requires static jakarta.annotation;

    requires transitive io.helidon.common;
    requires transitive io.helidon.servicecommon;
    requires micrometer.registry.prometheus.simpleclient;

    exports io.helidon.integrations.micrometer;

}
