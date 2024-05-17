/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tracing.providers.opentelemetry;

import io.helidon.common.context.Contexts;
import io.helidon.common.context.spi.DataPropagationProvider;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;

/**
 * A data propagation provider for OpenTelemetry which makes sure active spans are properly propagated
 * across threads managed by {@link io.helidon.common.context.ContextAwareExecutorService}.
 */
public class OpenTelemetryDataPropagationProvider
        implements DataPropagationProvider<OpenTelemetryDataPropagationProvider.OpenTelemetryContext> {

    private static final System.Logger LOGGER = System.getLogger(OpenTelemetryDataPropagationProvider.class.getName());

    @Override
    public OpenTelemetryContext data() {
        return Contexts.context().map(context -> context.get(Span.class).map(span -> {
            Tracer tracer = context.get(Tracer.class).orElseGet(OpenTelemetryTracerProvider::globalTracer);
            return new OpenTelemetryContext(tracer, span);
        }).orElse(null)).orElse(null);
    }

    @Override
    public void clearData(OpenTelemetryContext context) {
        if (context != null && context.scope != null) {
            try {
                context.scope.close();
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.TRACE, "Cannot close tracing span", e);
            }
        }
    }

    @Override
    public void propagateData(OpenTelemetryContext context) {
        if (context != null) {
            context.scope = context.span.activate();
        }
    }

    /**
     * OpenTelementry context.
     */
    public static class OpenTelemetryContext {
        private final Span span;
        private final Tracer tracer;
        private Scope scope;

        protected OpenTelemetryContext(Tracer tracer, Span span) {
            this.tracer = tracer;
            this.span = span;
        }

        /**
         * Return the current scope.
         *
         * @return current scope, null if the span in this context is not active
         */
        public Scope scope() {
            return scope;
        }

        /**
         * Return the tracer.
         *
         * @return tracer from the context
         */
        public Tracer tracer() {
            return tracer;
        }

        /**
         * Return the span.
         *
         * @return span from the context
         */
        public Span span() {
            return span;
        }
    }
}
