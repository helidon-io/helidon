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
@Features.Name("Telemetry")
@Features.Description("Support for Telemetry")
@Features.Flavor({HelidonFlavor.SE, HelidonFlavor.MP})
@Features.Path({"Telemetry/OpenTelemetry/Config"})
@Features.Incubating
module io.helidon.telemetry.otelconfig {

    requires io.helidon.builder.api;
    requires io.helidon.common.configurable;
    requires io.helidon.config;
    requires io.helidon.service.registry;
    requires io.helidon.tracing;
    requires io.helidon.tracing.providers.opentelemetry;

    requires io.opentelemetry.api;
    requires io.opentelemetry.context;
    requires io.opentelemetry.extension.trace.propagation;
    requires io.opentelemetry.sdk;
    requires io.opentelemetry.sdk.common;
    requires io.opentelemetry.sdk.trace;
    requires io.opentelemetry.semconv;

    requires zipkin2;
    requires zipkin2.reporter;

    requires static io.helidon.common.features.api;

    /*
    For conditionally-present exporter types.
     */
    requires static io.opentelemetry.exporter.logging;
    requires static io.opentelemetry.exporter.logging.otlp;
    requires static io.opentelemetry.exporter.otlp;
    requires static io.opentelemetry.exporter.zipkin;

    exports io.helidon.telemetry.otelconfig;

}
