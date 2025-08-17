/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

/**
 * OpenTelemetry implementation for telemetry.
 */
@Features.Name("Telemetry Tracing Semantic Conventions")
@Features.Description("Support for Telemetry Semantic Conventions")
@Features.Flavor({HelidonFlavor.SE})
@Features.Path({"Telemetry/OpenTelemetry/SemConv"})
@Features.Incubating
module io.helidon.webserver.observe.telemetry.tracing {

    requires io.helidon.service.registry;


    requires static io.helidon.common.features.api;
    requires io.helidon.webserver.observe.tracing;
    requires io.helidon.tracing.config;
    requires io.helidon.webserver;
    requires io.helidon.tracing;
}
