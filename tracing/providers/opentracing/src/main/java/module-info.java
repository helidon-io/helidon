/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import io.helidon.tracing.SpanLifeCycleListener;

/**
 * Open tracing support for Helidon tracing.
 */
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.tracing.providers.opentracing {

    requires io.opentracing.noop;
    requires io.opentracing.util;

    requires static io.helidon.config.metadata;

    requires transitive io.helidon.common.config;
    requires transitive io.helidon.common;
    requires transitive io.helidon.tracing;
    requires transitive io.opentracing.api;

    exports io.helidon.tracing.providers.opentracing.spi;
    exports io.helidon.tracing.providers.opentracing;

    uses io.helidon.tracing.providers.opentracing.spi.OpenTracingProvider;
    uses SpanLifeCycleListener;

    provides io.helidon.tracing.spi.TracerProvider
            with io.helidon.tracing.providers.opentracing.OpenTracingTracerProvider;

}