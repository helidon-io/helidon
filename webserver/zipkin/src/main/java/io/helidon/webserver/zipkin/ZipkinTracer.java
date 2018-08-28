/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.zipkin;

import brave.opentracing.BraveTracer;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

/**
 * The ZipkinTracer delegates to {@link BraveTracer} while creating {@link ZipkinSpanBuilder}
 * instead of {@link brave.opentracing.BraveSpanBuilder}.
 *
 * @see <a href="http://zipkin.io/pages/instrumenting.html#core-data-structures">Zipkin Attributes</a>
 * @see <a href="https://github.com/openzipkin/zipkin/issues/962">Zipkin Missing Service Name</a>
 * @see ZipkinSpanBuilder
 */
class ZipkinTracer implements Tracer {
    private final BraveTracer tracer;

    ZipkinTracer(BraveTracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public SpanBuilder buildSpan(String operationName) {
        return new ZipkinSpanBuilder(tracer.buildSpan(operationName));
    }

    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        tracer.inject(spanContext, format, carrier);
    }

    @Override
    public <C> SpanContext extract(Format<C> format, C carrier) {
        return tracer.extract(format, carrier);
    }

    @Override
    public ScopeManager scopeManager() {
        return tracer.scopeManager();
    }

    @Override
    public Span activeSpan() {
        return tracer.activeSpan();
    }
}
