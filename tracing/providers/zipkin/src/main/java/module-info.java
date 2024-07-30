/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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
 * Zipkin tracing support.
 */
@Feature(value = "Zipkin",
        description = "Zipkin tracer integration",
        in = {HelidonFlavor.MP, HelidonFlavor.SE},
        path = {"Tracing", "Zipkin"}
)
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.tracing.providers.zipkin {
    requires brave.opentracing;
    requires brave;
    requires io.helidon.common;
    requires io.helidon.tracing.providers.opentracing;
    requires io.opentracing.noop;
    requires io.opentracing.util;

    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;
    requires transitive io.helidon.common.config;
    requires transitive io.helidon.tracing;
    requires transitive io.opentracing.api;

    requires zipkin2.reporter.urlconnection;
    requires zipkin2.reporter;
    requires zipkin2;

    exports io.helidon.tracing.providers.zipkin;

    uses io.helidon.tracing.SpanListener;

    provides io.helidon.tracing.providers.opentracing.spi.OpenTracingProvider
            with io.helidon.tracing.providers.zipkin.ZipkinTracerProvider;

}