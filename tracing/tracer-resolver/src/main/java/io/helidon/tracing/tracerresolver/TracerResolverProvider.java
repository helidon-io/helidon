/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

package io.helidon.tracing.tracerresolver;

import io.helidon.common.Prioritized;
import io.helidon.tracing.opentracing.OpenTracingTracerBuilder;
import io.helidon.tracing.opentracing.spi.OpenTracingProvider;

import jakarta.annotation.Priority;

/**
 * Service to use {@link io.opentracing.contrib.tracerresolver.TracerResolver} to find tracer to use with Helidon.
 */
// lower priority, so this get overridden by specific tracers if present
@Priority(Prioritized.DEFAULT_PRIORITY + 1000)
public class TracerResolverProvider implements OpenTracingProvider {
    @Override
    public OpenTracingTracerBuilder<?> createBuilder() {
        return new TracerResolverBuilder();
    }
}
