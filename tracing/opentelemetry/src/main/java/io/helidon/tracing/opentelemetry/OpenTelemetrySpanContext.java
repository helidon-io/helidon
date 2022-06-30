/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.tracing.opentelemetry;

import io.helidon.tracing.SpanContext;

class OpenTelemetrySpanContext implements SpanContext {
    private final io.opentelemetry.api.trace.SpanContext delegate;

    OpenTelemetrySpanContext(io.opentelemetry.api.trace.SpanContext context) {
        this.delegate = context;
    }

    @Override
    public String traceId() {
        return delegate.getTraceId();
    }

    @Override
    public String spanId() {
        return delegate.getSpanId();
    }

    io.opentelemetry.api.trace.SpanContext openTelemetry() {
        return delegate;
    }
}
