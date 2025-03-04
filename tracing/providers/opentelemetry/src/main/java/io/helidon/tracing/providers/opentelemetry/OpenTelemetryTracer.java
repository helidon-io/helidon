/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.StringJoiner;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.SpanListener;
import io.helidon.tracing.Tracer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

class OpenTelemetryTracer implements Tracer {

    private static final TextMapGetter GETTER = new Getter();
    private static final TextMapSetter SETTER = new Setter();

    private static final LazyValue<List<SpanListener>> SPAN_LISTENERS =
            LazyValue.create(() -> HelidonServiceLoader.create(ServiceLoader.load(SpanListener.class)).asList());

    private final OpenTelemetry telemetry;
    private final io.opentelemetry.api.trace.Tracer delegate;
    private final boolean enabled;
    private final TextMapPropagator propagator;
    private final Map<String, String> tags;
    private final List<SpanListener> spanListeners = new ArrayList<>(SPAN_LISTENERS.get());

    OpenTelemetryTracer(OpenTelemetry telemetry, io.opentelemetry.api.trace.Tracer tracer, Map<String, String> tags) {
        this.telemetry = telemetry;
        this.delegate = tracer;
        this.enabled = !tracer.getClass().getSimpleName().equals("DefaultTracer");
        this.propagator = telemetry.getPropagators().getTextMapPropagator();
        this.tags = tags;
    }

    static OpenTelemetryTracerBuilder builder() {
        return new OpenTelemetryTracerBuilder();
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public Span.Builder<?> spanBuilder(String name) {
        OpenTelemetrySpanBuilder builder = new OpenTelemetrySpanBuilder(delegate.spanBuilder(name),
                                                                        spanListeners);
        Span.current().map(Span::context).ifPresent(builder::parent);
        tags.forEach(builder::tag);
        return builder;
    }

    @Override
    public Optional<SpanContext> extract(HeaderProvider headersProvider) {
        Context context = propagator.extract(Context.current(), headersProvider, GETTER);

        return Optional.ofNullable(context)
                .map(OpenTelemetrySpanContext::new);
    }

    @Override
    public void inject(SpanContext spanContext, HeaderProvider inboundHeadersProvider, HeaderConsumer outboundHeadersConsumer) {
        propagator.inject(((OpenTelemetrySpanContext) spanContext).openTelemetry(), outboundHeadersConsumer, SETTER);
    }

    @Override
    public <T> T unwrap(Class<T> tracerClass) {
        if (tracerClass.isAssignableFrom(delegate.getClass())) {
            return tracerClass.cast(delegate);
        }
        if (tracerClass.isInstance(this)) {
            return tracerClass.cast(this);
        }

        throw new IllegalArgumentException("Cannot provide an instance of " + tracerClass.getName()
                                                   + ", telemetry tracer is: " + delegate.getClass().getName());
    }

    @Override
    public Tracer register(SpanListener listener) {
        spanListeners.add(listener);
        return this;
    }

    @Override
    public Tracer unregister(SpanListener listener) {
        spanListeners.remove(listener);
        return this;
    }

    List<SpanListener> spanListeners() {
        return Collections.unmodifiableList(spanListeners);
    }

    private static class Getter implements TextMapGetter<HeaderProvider> {
        @Override
        public Iterable<String> keys(HeaderProvider headerProvider) {
            return headerProvider.keys();
        }

        @Override
        public String get(HeaderProvider headerProvider, String s) {
            StringJoiner joiner = new StringJoiner(",").setEmptyValue("");
            headerProvider.getAll(s).forEach(joiner::add);
            return joiner.toString();
        }
    }

    private static class Setter implements TextMapSetter<HeaderConsumer> {
        @Override
        public void set(HeaderConsumer carrier, String key, String value) {
            carrier.set(key, value);
        }
    }
}
