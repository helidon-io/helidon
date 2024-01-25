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
package io.helidon.tracing.providers.opentelemetry;

import io.helidon.tracing.SpanContext;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

class OpenTelemetrySpanContext implements SpanContext {
    private final Context context;

    OpenTelemetrySpanContext(Context context) {
        this.context = context;
    }

    @Override
    public String traceId() {
        return Span.fromContext(context).getSpanContext().getTraceId();
    }

    @Override
    public String spanId() {
        return Span.fromContext(context).getSpanContext().getSpanId();
    }

    @Override
    public void asParent(io.helidon.tracing.Span.Builder<?> spanBuilder) {
        ((OpenTelemetrySpanBuilder) spanBuilder).parent(context);
    }

    Context openTelemetry() {
        return context;
    }
}
