/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Preview;
import io.helidon.microprofile.telemetry.TelemetryAutoDiscoverable;
import io.helidon.microprofile.telemetry.TelemetryCdiExtension;

/**
 * MicroProfile Telemetry support for Helidon.
 */
@Preview
@Feature(value = "Telemetry",
         description = "MP Telemetry support",
         in = HelidonFlavor.MP,
         path = "Telemetry"
)
module io.helidon.microprofile.telemetry {
    requires static io.helidon.common.features.api;

    requires io.helidon.common.context;
    requires io.helidon.config.mp;
    requires io.helidon.config;
    requires io.opentelemetry.api;
    requires io.opentelemetry.context;
    requires io.opentelemetry.sdk;
    requires io.opentelemetry.sdk.autoconfigure.spi;
    requires io.opentelemetry.sdk.autoconfigure;
    requires io.opentelemetry.sdk.common;
    requires jakarta.annotation;
    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.ws.rs;
    requires java.logging;
    requires jersey.common;
    requires microprofile.config.api;
    requires opentelemetry.instrumentation.annotations;
    requires io.helidon.tracing.opentelemetry;

    exports io.helidon.microprofile.telemetry;

    provides jakarta.enterprise.inject.spi.Extension
            with TelemetryCdiExtension;

    provides org.glassfish.jersey.internal.spi.AutoDiscoverable
            with TelemetryAutoDiscoverable;

}
