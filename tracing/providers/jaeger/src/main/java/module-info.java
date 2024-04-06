/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

/**
 * Jaeger tracing support.
 */
@Feature(value = "Jaeger",
        description = "Jaeger tracer integration",
        in = {HelidonFlavor.SE, HelidonFlavor.MP},
        path = {"Tracing", "Jaeger"}
)
module io.helidon.tracing.providers.jaeger {
    requires io.helidon.common.configurable;
    requires io.helidon.common.context;
    requires io.helidon.tracing.providers.opentelemetry;
    requires io.opentelemetry.exporter.jaeger;
    requires io.opentelemetry.sdk.common;
    requires io.opentelemetry.sdk.trace;
    requires io.opentelemetry.sdk;
    // this was missing from module path, need an explicit requires
    // as kotlin is modularized
    requires kotlin.stdlib;

    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;

    requires transitive io.helidon.common.config;
    requires transitive io.helidon.common;
    requires transitive io.helidon.tracing;
    requires io.opentelemetry.context;
    requires io.opentelemetry.extension.trace.propagation;
    requires io.opentelemetry.semconv;

    exports io.helidon.tracing.providers.jaeger;

    uses io.helidon.tracing.SpanLifeCycleListener;

    provides io.helidon.tracing.spi.TracerProvider
            with io.helidon.tracing.providers.jaeger.JaegerTracerProvider;
    provides io.helidon.common.context.spi.DataPropagationProvider
            with io.helidon.tracing.providers.jaeger.JaegerDataPropagationProvider;

}
