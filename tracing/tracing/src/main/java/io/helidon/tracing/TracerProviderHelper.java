/*
 * Copyright (c) 2019, 2025 Oracle and/or its affiliates.
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
package io.helidon.tracing;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.logging.common.HelidonMdc;
import io.helidon.tracing.spi.TracerProvider;

/**
 * Tracer provider helper to find implementation to use.
 */
final class TracerProviderHelper {
    private static final System.Logger LOGGER = System.getLogger(TracerProviderHelper.class.getName());
    private static final TracerProvider TRACER_PROVIDER;

    static {
        TracerProvider provider = null;
        try {
            List<TracerProvider> allProviders = HelidonServiceLoader.builder(ServiceLoader.load(TracerProvider.class))
                    .addService(new NoOpTracerProvider(), 0)
                    .build()
                    .asList();

            if (allProviders.size() == 1 || allProviders.size() == 2) {
                // only noop or one and noop
                provider = allProviders.get(0);
            }
            for (TracerProvider aProvider : allProviders) {
                if (aProvider.available()) {
                    provider = aProvider;
                    break;
                }
            }
        } catch (Throwable e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to set up tracer provider, using no-op", e);
        }

        TRACER_PROVIDER = provider == null ? new NoOpTracerProvider() : provider;

        /*
        To obtain the current span, the Helidon tracing providers delegate to the underlying implementations.
        Therefore we can centrally set up MDC support for the trace ID here, using the neutral API, and still correctly
        retrieve the current trace ID for logging even if the user's code has used the underlying tracing library directly to
        activate a span.
         */
        HelidonMdc.setDeferred("trace_id", () -> currentSpan()
                .map(Span::context)
                .map(SpanContext::traceId)
                .orElse("none"));
    }

    private TracerProviderHelper() {
    }

    public static Optional<Span> currentSpan() {
        /*
        If a custom TracerProvider implementation indirectly tries to access the current span (for example, by triggering custom
        logging that adds the current span to each message), then this method can be invoked before the static initializer has
        completed and, therefore, before TRACER_PROVIDER is assigned.
         */
        return (TRACER_PROVIDER == null) ? Optional.empty() : TRACER_PROVIDER.currentSpan();
    }

    static Tracer global() {
        if (TRACER_PROVIDER == null) {
            throw new IllegalStateException("Use before initialization has completed");
        }
        return TRACER_PROVIDER.global();
    }

    static void global(Tracer tracer) {
        if (TRACER_PROVIDER == null) {
            throw new IllegalStateException("Use before initialization has completed");
        }
        TRACER_PROVIDER.global(tracer);
    }

    static TracerBuilder<?> findTracerBuilder() {
        if (TRACER_PROVIDER == null) {
            throw new IllegalStateException("Use before initialization has completed");
        }
        return TRACER_PROVIDER.createBuilder();
    }

}
