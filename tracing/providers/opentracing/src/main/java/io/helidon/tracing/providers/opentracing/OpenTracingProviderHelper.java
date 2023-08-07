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
package io.helidon.tracing.providers.opentracing;

import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.tracing.providers.opentracing.spi.OpenTracingProvider;

/**
 * Tracer provider helper to find implementation to use.
 */
final class OpenTracingProviderHelper {
    private static final OpenTracingProvider TRACER_PROVIDER =
            HelidonServiceLoader.builder(ServiceLoader.load(OpenTracingProvider.class))
                    .addService(NoOpBuilder::create, 0)
                    .build()
                    .iterator()
                    .next();

    private OpenTracingProviderHelper() {
    }

    public static boolean available() {
        return !TRACER_PROVIDER.createBuilder().getClass().equals(NoOpBuilder.class);
    }

    static OpenTracingProvider provider() {
        return TRACER_PROVIDER;
    }

    static OpenTracingTracerBuilder<?> findTracerBuilder() {
        return TRACER_PROVIDER.createBuilder();
    }
}
