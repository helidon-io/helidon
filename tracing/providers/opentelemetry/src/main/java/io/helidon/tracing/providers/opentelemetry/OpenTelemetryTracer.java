/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.StringJoiner;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.SpanListener;
import io.helidon.tracing.Tracer;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

class OpenTelemetryTracer implements RuntimeType.Api<OpenTelemetryTracerConfig>, Tracer {

    private static final System.Logger LOGGER = System.getLogger(OpenTelemetryTracer.class.getName());
    private static final TextMapGetter<HeaderProvider> GETTER = new Getter();
    private static final TextMapSetter<HeaderConsumer> SETTER = new Setter();

    private static final LazyValue<List<SpanListener>> AUTO_LOADED_SPAN_LISTENERS =
            LazyValue.create(() -> HelidonServiceLoader.create(ServiceLoader.load(SpanListener.class)).asList());

    private final List<SpanListener> spanListeners = new ArrayList<>();
    private final OpenTelemetryTracerConfig config;

    OpenTelemetryTracer(OpenTelemetryTracerConfig config) {
        this.config = config;
        spanListeners.addAll(AUTO_LOADED_SPAN_LISTENERS.get());
        spanListeners.addAll(config.spanListeners());

        if (config.enabled() && config.registerGlobal()) {
            try {
                GlobalOpenTelemetry.set(config.openTelemetry());
                Tracer.global(this);
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING, "Failed to set global OpenTelemetry as requested by tracing settings", e);
            }
        }
    }

    static OpenTelemetryTracer create(OpenTelemetryTracerConfig config) {
        return new OpenTelemetryTracer(config);
    }

    static OpenTelemetryTracerConfig.Builder builder() {
        return OpenTelemetryTracerConfig.builder();
    }

    static OpenTelemetryTracer create(java.util.function.Consumer<OpenTelemetryTracerConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    @Override
    public Tracer register(SpanListener listener) {
        spanListeners.add(listener);
        return this;
    }

    @Override
    public boolean enabled() {
        return config.enabled();
    }

    @Override
    public Span.Builder<?> spanBuilder(String name) {

        OpenTelemetrySpanBuilder builder = new OpenTelemetrySpanBuilder(delegate().spanBuilder(name),
                                                                        spanListeners);

        Span.current().map(Span::context).ifPresent(builder::parent);

        config.intTracerTags().forEach(builder::tag);
        config.booleanTracerTags().forEach(builder::tag);
        config.tracerTags().forEach(builder::tag);

        return builder;
    }

    @Override
    public void inject(SpanContext spanContext, HeaderProvider inboundHeadersProvider, HeaderConsumer outboundHeadersConsumer) {
        config.propagator()
                .inject(((OpenTelemetrySpanContext) spanContext).openTelemetry(), outboundHeadersConsumer, SETTER);
    }

    @Override
    public Optional<SpanContext> extract(HeaderProvider headersProvider) {
        Context context = config.propagator().extract(Context.current(), headersProvider, GETTER);
        return Optional.ofNullable(context)
                .map(OpenTelemetrySpanContext::new);
    }

    @Override
    public OpenTelemetryTracerConfig prototype() {
        return config;
    }

    @Override
    public <T> T unwrap(Class<T> tracerClass) {
        if (tracerClass.isInstance(prototype().delegate())) {
            return tracerClass.cast(prototype().delegate());
        }

        if (tracerClass.isInstance(this)) {
            return tracerClass.cast(this);
        }

        throw new IllegalArgumentException("Cannot provide an instance of " + tracerClass.getName()
                                                   + ", telemetry tracer is: " + prototype().delegate().getClass().getName());
    }

    List<SpanListener> spanListeners() {
        return Collections.unmodifiableList(spanListeners);
    }

    io.opentelemetry.api.trace.Tracer delegate() {
        return config.delegate();
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
