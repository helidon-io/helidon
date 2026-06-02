/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
 * OpenTelemetry support for Helidon telemetry integrations.
 */
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
@Features.Name("OpenTelemetry")
@Features.Description("Shared OpenTelemetry support")
@Features.Flavor({HelidonFlavor.SE, HelidonFlavor.MP})
@Features.Path({"Telemetry", "OpenTelemetry"})
module io.helidon.telemetry.opentelemetry {
    requires io.helidon.common;
    requires io.helidon.config;
    requires io.helidon.service.registry;
    requires static io.helidon.common.features.api;

    requires transitive io.opentelemetry.api;
    requires io.opentelemetry.context;

    exports io.helidon.telemetry.opentelemetry;
    exports io.helidon.telemetry.opentelemetry.spi;
}
