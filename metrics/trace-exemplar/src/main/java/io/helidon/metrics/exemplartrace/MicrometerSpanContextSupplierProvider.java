/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
package io.helidon.metrics.exemplartrace;

import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.common.context.Contexts;
import io.helidon.tracing.Span;

import io.prometheus.metrics.tracer.common.SpanContext;

/**
 * Full-featured implementation of provider for trace information to support exemplars.
 */
public class MicrometerSpanContextSupplierProvider
        implements io.helidon.metrics.providers.micrometer.spi.SpanContextSupplierProvider {

    /**
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public MicrometerSpanContextSupplierProvider() {
    }

    @Override
    public SpanContext get() {
        return new SpanContextImpl();
    }

    private record SpanContextImpl() implements SpanContext {

        @Override
        public String getCurrentTraceId() {
            return spanContext().map(io.helidon.tracing.SpanContext::traceId).orElse(null);
        }

        @Override
        public String getCurrentSpanId() {
            return spanContext().map(io.helidon.tracing.SpanContext::spanId).orElse(null);
        }

        @Override
        public boolean isCurrentSpanSampled() {
            return spanContext().map(io.helidon.tracing.SpanContext::sampled).orElse(false);
        }

        @Override
        public void markCurrentSpanAsExemplar() {
            span().ifPresent(span -> span.tag(SpanContext.EXEMPLAR_ATTRIBUTE_NAME,
                                              SpanContext.EXEMPLAR_ATTRIBUTE_VALUE));
        }

        private Optional<io.helidon.tracing.SpanContext> spanContext() {
            return Contexts.context()
                    .flatMap(c -> c.get(io.helidon.tracing.SpanContext.class));
        }

        private Optional<Span> span() {
            return Contexts.context().flatMap(c -> c.get(Span.class));
        }
    }
}
