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
 * Transitional OpenTelemetry-compatible Zipkin HTTP exporter using Helidon WebClient.
 *
 * @deprecated Migrate to an OTLP exporter.
 */
@Deprecated(since = "4.5.7", forRemoval = true)
@Features.Name("Zipkin Exporter")
@Features.Description("OpenTelemetry-compatible Zipkin HTTP Exporter")
@Features.Flavor({HelidonFlavor.SE, HelidonFlavor.MP})
@Features.Path({"Tracing", "Exporter", "Zipkin"})
@Features.Incubating
module io.helidon.tracing.exporter.zipkin {
    requires transitive io.opentelemetry.sdk.trace;
    requires transitive io.opentelemetry.sdk.common;
    requires transitive io.opentelemetry.api;
    requires transitive zipkin2;
    requires transitive zipkin2.reporter;
    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.config;
    requires transitive io.helidon.common.config;
    requires io.helidon.common.tls;
    requires io.helidon.http.media;
    requires io.opentelemetry.context;
    requires io.opentelemetry.sdk.autoconfigure.spi;
    requires io.helidon.common;
    requires io.helidon.webclient.http1;
    requires io.helidon.webclient.api;
    requires io.helidon.http;
    requires java.logging;
    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;

    exports io.helidon.tracing.exporter.zipkin;

    provides io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider
            with io.helidon.tracing.exporter.zipkin.ZipkinSpanExporterProvider;
}
