/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import io.helidon.tracing.providers.opentracing.OpenTracingTracerProvider;
import io.helidon.tracing.providers.opentracing.spi.OpenTracingProvider;
import io.helidon.tracing.spi.TracerProvider;

/**
 * Open tracing support for Helidon tracing.
 */
module io.helidon.tracing.providers.opentracing {
    exports io.helidon.tracing.providers.opentracing.spi;
    exports io.helidon.tracing.providers.opentracing;
    requires transitive io.helidon.common;
    requires transitive io.helidon.common.config;
    requires transitive io.helidon.tracing;
    requires static io.helidon.config.metadata;

    requires io.opentracing.util;
    requires io.opentracing.api;
    requires io.opentracing.noop;

    uses OpenTracingProvider;
    provides TracerProvider with OpenTracingTracerProvider;
}