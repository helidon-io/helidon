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
/**
 * Support for OpenTelemetry metrics semantic conventions.
 */

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

@Features.Preview
@Features.Name("OTel Automatic Metrics")
@Features.Description("Automatic metrics for compliance with OpenTelemetry server HTTP semantic conventions")
@Features.Path({"Metrics", "Automatic Metrics", "Server", "HTTP", "OpenTelemetry"})
@Features.Flavor(HelidonFlavor.SE)
module io.helidon.webserver.observe.telemetry.metrics {
    requires io.helidon.telemetry.otelconfig;
    requires io.helidon.webserver.observe.metrics;
    requires io.helidon.service.registry;
    requires io.helidon.webserver;

    requires io.opentelemetry.api;
    requires io.opentelemetry.semconv;
    requires io.helidon.common.features.api;
    requires micrometer.core;
    requires io.helidon.metrics.api;
    requires io.opentelemetry.sdk.metrics;

    exports io.helidon.webserver.observe.telemetry.metrics;

}
