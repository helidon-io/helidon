/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

package io.helidon.tracing.providers.zipkin;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.tracing.SpanLifeCycleListener;
import io.helidon.tracing.Tag;

import brave.opentracing.BraveTracer;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

/**
 * The ZipkinTracer delegates to {@link BraveTracer} while creating {@link ZipkinSpanBuilder}
 * instead of {@link brave.opentracing.BraveSpanBuilder}.
 * This class should not be sued directly, use either
 * {@link io.helidon.tracing.TracerBuilder} or {@link ZipkinTracerBuilder}.
 *
 * @see <a href="http://zipkin.io/pages/instrumenting.html#core-data-structures">Zipkin Attributes</a>
 * @see <a href="https://github.com/openzipkin/zipkin/issues/962">Zipkin Missing Service Name</a>
 * @see ZipkinSpanBuilder
 */
public class ZipkinTracer implements Tracer {

    private static final LazyValue<List<SpanLifeCycleListener>> SPAN_LIFE_CYCLE_LISTENERS =
            LazyValue.create(() -> HelidonServiceLoader.create(ServiceLoader.load(SpanLifeCycleListener.class)).asList());

    private final BraveTracer tracer;
    private final List<Tag<?>> tags;

    private final List<SpanLifeCycleListener> spanLifeCycleListeners = new ArrayList<>(SPAN_LIFE_CYCLE_LISTENERS.get());

    /**
     * Create a zipkin tracer from the delegate (BraveTracer) and
     * tags to be used by default for all traces.
     *
     * @param tracer tracer to wrap
     * @param tags list of tags to be automatically added to each span
     */
    public ZipkinTracer(BraveTracer tracer, List<Tag<?>> tags) {
        this.tracer = tracer;
        this.tags = tags;
    }

    @Override
    public SpanBuilder buildSpan(String operationName) {
        return new ZipkinSpanBuilder(this,
                                     tracer.buildSpan(operationName),
                                     tags,
                                     spanLifeCycleListeners);
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
        return new ZipkinScopeManager(tracer.scopeManager(),
                                      spanLifeCycleListeners);
    }

    @Override
    public Span activeSpan() {
        return tracer.activeSpan();
    }

    @Override
    public void close() {
        tracer.close();
    }

    @Override
    public Scope activateSpan(Span span) {
        if (span instanceof ZipkinSpan) {
            return tracer.activateSpan(((ZipkinSpan) span).unwrap());
        } else {
            return tracer.activateSpan(span);
        }
    }
}
