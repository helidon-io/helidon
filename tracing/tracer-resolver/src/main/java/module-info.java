/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 * {@link io.opentracing.contrib.tracerresolver.TracerResolver} tracing support.
 */
module io.helidon.tracing.tracerresolver {
    requires io.helidon.common;
    requires io.helidon.config;
    requires io.helidon.tracing;

    requires java.logging;
    requires io.opentracing.util;
    requires io.opentracing.noop;
    requires io.opentracing.contrib.tracerresolver;

    exports io.helidon.tracing.tracerresolver;

    provides io.helidon.tracing.spi.TracerProvider with io.helidon.tracing.tracerresolver.TracerResolverProvider;
}
