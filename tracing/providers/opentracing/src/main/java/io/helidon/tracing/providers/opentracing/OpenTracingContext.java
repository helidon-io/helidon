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
package io.helidon.tracing.providers.opentracing;

import io.helidon.tracing.Span;

import io.opentracing.SpanContext;

class OpenTracingContext implements io.helidon.tracing.SpanContext {
    private final SpanContext delegate;

    OpenTracingContext(SpanContext context) {
        this.delegate = context;
    }

    @Override
    public String traceId() {
        return delegate.toTraceId();
    }

    @Override
    public String spanId() {
        return delegate.toSpanId();
    }

    @Override
    public void asParent(Span.Builder<?> spanBuilder) {
        spanBuilder.unwrap(OpenTracingSpanBuilder.class)
                .parent(this);
    }

    SpanContext openTracing() {
        return delegate;
    }
}
