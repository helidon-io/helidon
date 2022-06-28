package io.helidon.tracing.opentracing;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;

import io.opentracing.noop.NoopScopeManager;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapAdapter;

class OpenTracingTracer implements Tracer {
    private final io.opentracing.Tracer delegate;
    private final boolean enabled;

    private OpenTracingTracer(io.opentracing.Tracer delegate, boolean enabled) {
        this.delegate = delegate;
        this.enabled = enabled;
    }

    static Tracer create(io.opentracing.Tracer tracer) {
        return new OpenTracingTracer(tracer, !(tracer.scopeManager() instanceof NoopScopeManager));
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public Span.Builder<?> spanBuilder(String name) {
        return new OpenTracingSpanBuilder(delegate, delegate.buildSpan(name));
    }

    @Override
    public Optional<SpanContext> extract(HeaderProvider headersProvider) {
        Map<String, String> headers = new HashMap<>();
        for (String key : headersProvider.keys()) {
            headers.put(key, headersProvider.get(key).orElse(null));
        }
        io.opentracing.SpanContext context = delegate.extract(Format.Builtin.HTTP_HEADERS,
                                                              new TextMapAdapter(headers));
        if (context == null) {
            return Optional.empty();
        } else {
            return Optional.of(new OpenTracingContext(context));
        }
    }

    @Override
    public void inject(SpanContext spanContext,
                       HeaderProvider inboundHeadersProvider,
                       HeaderConsumer outboundHeadersConsumer) {

        if (spanContext instanceof OpenTracingContext otc) {
            delegate.inject(otc.openTracing(), Format.Builtin.HTTP_HEADERS, new TextMap() {
                @Override
                public Iterator<Map.Entry<String, String>> iterator() {
                    throw new UnsupportedOperationException(
                            "TextMapInjectAdapter should only be used with Tracer.inject()");
                }

                @Override
                public void put(String key, String value) {
                    outboundHeadersConsumer.set(key, value);
                }
            });
            OpenTracingProviderHelper.provider()
                    .updateOutboundHeaders(delegate,
                                           otc.openTracing(),
                                           inboundHeadersProvider,
                                           outboundHeadersConsumer);
        }
    }

    @Override
    public <T> T unwrap(Class<T> tracerClass) {
        if (tracerClass.isAssignableFrom(delegate.getClass())) {
            return tracerClass.cast(delegate);
        }
        throw new IllegalArgumentException("Cannot provide an instance of " + tracerClass.getName()
                                                   + ", open tracing tracer is: " + delegate.getClass().getName());
    }

    io.opentracing.Tracer openTracing() {
        return delegate;
    }

    public static class Builder implements TracerBuilder<Builder> {
        private final OpenTracingTracerBuilder<?> delegate = OpenTracingProviderHelper.findTracerBuilder();

        private Builder() {
        }

        @Override
        public Tracer build() {
            return new OpenTracingTracer(delegate.build(), delegate.enabled());
        }

        @Override
        public Builder serviceName(String name) {
            delegate.serviceName(name);
            return this;
        }

        @Override
        public Builder collectorProtocol(String protocol) {
            delegate.collectorProtocol(protocol);
            return this;
        }

        @Override
        public Builder collectorPort(int port) {
            delegate.collectorPort(port);
            return this;
        }

        @Override
        public Builder collectorHost(String host) {
            delegate.collectorHost(host);
            return this;
        }

        @Override
        public Builder collectorPath(String path) {
            delegate.collectorPath(path);
            return this;
        }

        @Override
        public Builder addTracerTag(String key, String value) {
            delegate.addTracerTag(key, value);
            return this;
        }

        @Override
        public Builder addTracerTag(String key, Number value) {
            delegate.addTracerTag(key, value);
            return this;
        }

        @Override
        public Builder addTracerTag(String key, boolean value) {
            delegate.addTracerTag(key, value);
            return this;
        }

        @Override
        public Builder config(Config config) {
            delegate.config(config);
            return this;
        }

        @Override
        public Builder enabled(boolean enabled) {
            delegate.enabled(enabled);
            return this;
        }

        @Override
        public Builder registerGlobal(boolean global) {
            delegate.registerGlobal(global);
            return this;
        }

        @Override
        public <T> T unwrap(Class<T> builderClass) {
            if (OpenTracingTracer.class == builderClass) {
                return builderClass.cast(this);
            }

            return delegate.unwrap(builderClass);
        }
    }
}
