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

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

public class OpenTelemetryTracer implements Tracer {
    private static final TextMapGetter GETTER = new Getter();
    private static final TextMapSetter SETTER = new Setter();
    private final OpenTelemetry telemetry;
    private final io.opentelemetry.api.trace.Tracer delegate;
    private final boolean enabled;
    private final TextMapPropagator propagator;

    OpenTelemetryTracer(OpenTelemetry telemetry, io.opentelemetry.api.trace.Tracer tracer) {
        this.telemetry = telemetry;
        this.delegate = tracer;
        this.enabled = !tracer.getClass().getSimpleName().equals("DefaultTracer");
        this.propagator = telemetry.getPropagators().getTextMapPropagator();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public Span.Builder<?> spanBuilder(String name) {
        return new OpenTelemetrySpanBuilder(delegate.spanBuilder(name));
    }

    @Override
    public Optional<SpanContext> extract(HeaderProvider headersProvider) {
        Context context = propagator.extract(Context.current(), headersProvider, GETTER);
        context.makeCurrent();

        return Optional.ofNullable(io.opentelemetry.api.trace.Span.current())
                .map(io.opentelemetry.api.trace.Span::getSpanContext)
                .map(OpenTelemetrySpanContext::new);
    }

    @Override
    public void inject(SpanContext spanContext, HeaderProvider inboundHeadersProvider, HeaderConsumer outboundHeadersConsumer) {
        io.opentelemetry.api.trace.Span.wrap(((OpenTelemetrySpanContext) spanContext).openTelemetry()).makeCurrent();
        propagator.inject(Context.current(), outboundHeadersConsumer, SETTER);
    }

    public static class Builder implements TracerBuilder<Builder> {
        private OpenTelemetry ot;
        private String serviceName = "helidon-service";
        private boolean registerGlobal;

        @Override
        public Tracer build() {
            if (ot == null) {
                ot = GlobalOpenTelemetry.get();
            }
            io.opentelemetry.api.trace.Tracer tracer = ot.getTracer(serviceName);
            Tracer result = new OpenTelemetryTracer(ot, tracer);
            if (registerGlobal) {
                Tracer.global(result);
            }
            return result;
        }

        public Builder openTelemetry(OpenTelemetry ot) {
            this.ot = ot;
            return this;
        }

        @Override
        public Builder serviceName(String name) {
            this.serviceName = serviceName;
            return this;
        }

        @Override
        public Builder collectorProtocol(String protocol) {
            return this;
        }

        @Override
        public Builder collectorPort(int port) {
            return this;
        }

        @Override
        public Builder collectorHost(String host) {
            return this;
        }

        @Override
        public Builder collectorPath(String path) {
            return this;
        }

        @Override
        public Builder addTracerTag(String key, String value) {
            return this;
        }

        @Override
        public Builder addTracerTag(String key, Number value) {
            return this;
        }

        @Override
        public Builder addTracerTag(String key, boolean value) {
            return this;
        }

        @Override
        public Builder config(Config config) {
            return this;
        }

        @Override
        public Builder enabled(boolean enabled) {
            return this;
        }

        @Override
        public Builder registerGlobal(boolean global) {
            this.registerGlobal = global;
            return this;
        }

        @Override
        public <B> B unwrap(Class<B> builderClass) {
            if (builderClass.isAssignableFrom(getClass())) {
                return builderClass.cast(this);
            }
            throw new IllegalArgumentException("Cannot unwrap " + builderClass + " from Opentelmetry tracer builder.");
        }
    }

    private static class Getter implements TextMapGetter<HeaderProvider> {
        @Override
        public Iterable<String> keys(HeaderProvider headerProvider) {
            return headerProvider.keys();
        }

        @Override
        public String get(HeaderProvider headerProvider, String s) {
            return headerProvider.get(s).orElse(null);
        }
    }

    private static class Setter implements TextMapSetter<HeaderConsumer> {
        @Override
        public void set(HeaderConsumer carrier, String key, String value) {
            carrier.set(key, value);
        }
    }
}
