/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.telemetry;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.helidon.common.Errors;
import io.helidon.tracing.providers.opentelemetry.HelidonOpenTelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * Provides proxies for OpenTelemetry types.
 * <p>
 * The proxies allow users to inject {@link io.opentelemetry.api.trace.Tracer} and {@link io.opentelemetry.api.trace.Span} and
 * invoke state-changing operations on them and on related types ({@link io.opentelemetry.context.Scope}) in a way that notifies
 * developer-provided {@link io.helidon.tracing.SpanListener} objects that have been registered with the Helidon tracer.
 */
class OtelProxy {

    static OpenTelemetry openTelemetry(OpenTelemetry openTelemetry) {
        return (OpenTelemetry) Proxy.newProxyInstance(OtelProxy.class.getClassLoader(),
                                                      openTelemetry.getClass().getInterfaces(),
                                                      new OpenTelemetryHandler(openTelemetry));
    }

    static Tracer tracer(OpenTelemetry openTelemetry, Tracer otelTracer) {
        Class<?>[] allInterfaces = Arrays.copyOf(otelTracer.getClass().getInterfaces(),
                                                 otelTracer.getClass().getInterfaces().length + 1);
        allInterfaces[allInterfaces.length - 1] = ProxiedTracer.class;
        return (Tracer) Proxy.newProxyInstance(OtelProxy.class.getClassLoader(),
                                               allInterfaces,
                                               new TracerHandler(otelTracer, HelidonOpenTelemetry.create(openTelemetry,
                                                                                                         otelTracer,
                                                                                                         Map.of())));
    }

    static Span span(io.helidon.tracing.Span.Extended<Scope> helidonSpan) {
        Span delegate = helidonSpan.unwrap(Span.class);
        return (Span) Proxy.newProxyInstance(OtelProxy.class.getClassLoader(),
                                             delegate.getClass().getInterfaces(),
                                             new SpanHandler(delegate, helidonSpan));
    }

    interface ProxiedTracer {
        io.helidon.tracing.Tracer helidonTracer();
    }

    private static SpanBuilder spanBuilder(io.helidon.tracing.Span.Builder<?> helidonSpanBuilder) {
        SpanBuilder delegate = helidonSpanBuilder.unwrap(SpanBuilder.class);
        return (SpanBuilder) Proxy.newProxyInstance(OtelProxy.class.getClassLoader(),
                                                    delegate.getClass().getInterfaces(),
                                                    new SpanBuilderHandler(delegate, helidonSpanBuilder));
    }

    private static Scope scope(io.helidon.tracing.Scope helidonScope,
                               Scope delegate) {
        return (Scope) Proxy.newProxyInstance(OtelProxy.class.getClassLoader(),
                                              delegate.getClass().getInterfaces(),
                                              new ScopeHandler(delegate, helidonScope));
    }

    private OtelProxy() {
    }

    private record OpenTelemetryHandler(OpenTelemetry openTelemetry) implements InvocationHandler {

            private static final Method TRACER_WITH_NAME;
            private static final Method TRACER_WITH_NAME_AND_VERSION;

            static {
                try (MethodHelper methodHelper = new MethodHelper(OpenTelemetry.class)) {
                    TRACER_WITH_NAME = methodHelper.method("getTracer", String.class);
                    TRACER_WITH_NAME_AND_VERSION = methodHelper.method("getTracer", String.class, String.class);
                }
            }

        @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (TRACER_WITH_NAME.equals(method) || TRACER_WITH_NAME_AND_VERSION.equals(method)) {
                    return OtelProxy.tracer(openTelemetry, (Tracer) method.invoke(openTelemetry, args));
                } else {
                    return method.invoke(openTelemetry, args);
                }
            }
        }

    private record TracerHandler(Tracer otelTracer, io.helidon.tracing.Tracer helidonTracer) implements InvocationHandler {

            // Preparing the methods of interest as constants allows fail-fast if the API changes.
            private static final Method SPAN_BUILDER;
            private static final Method HELIDON_TRACER;

            static {
                try (MethodHelper methodHelper = new MethodHelper(Tracer.class)) {
                    SPAN_BUILDER = methodHelper.method("spanBuilder", String.class);
                }
                try (MethodHelper methodHelper = new MethodHelper(ProxiedTracer.class)) {
                    HELIDON_TRACER = methodHelper.method("helidonTracer");
                }
            }

        @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.equals(SPAN_BUILDER)) {
                    io.helidon.tracing.Span.Builder<?> helidonSpanBuilder = helidonTracer.spanBuilder((String) args[0]);
                    return OtelProxy.spanBuilder(helidonSpanBuilder);
                } else if (method.equals(HELIDON_TRACER)) {
                    return helidonTracer;
                } else {
                    return method.invoke(otelTracer, args);
                }
            }
        }

    private static class SpanBuilderHandler implements InvocationHandler {

        private static final Method SET_START_TIME_INSTANT;
        private static final Method SET_START_TIME_TIME_UNIT;
        private static final Method START_SPAN;

        static {
            try (MethodHelper methodHelper = new MethodHelper(SpanBuilder.class)) {
                SET_START_TIME_INSTANT = methodHelper.method("setStartTimestamp", Instant.class);
                SET_START_TIME_TIME_UNIT = methodHelper.method("setStartTimestamp", long.class, TimeUnit.class);
                START_SPAN = methodHelper.method("startSpan");
            }
        }

        private final SpanBuilder otelSpanBuilder;
        private final io.helidon.tracing.Span.Builder<?> helidonSpanBuilder;
        private Instant startInstant;

        private SpanBuilderHandler(SpanBuilder otelSpanBuilder, io.helidon.tracing.Span.Builder<?> helidonSpanBuilder) {
            this.otelSpanBuilder = otelSpanBuilder;
            this.helidonSpanBuilder = helidonSpanBuilder;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (SET_START_TIME_INSTANT.equals(method)) {
                startInstant = (Instant) args[0];
            } else if (SET_START_TIME_TIME_UNIT.equals(method)) {
                startInstant = Instant.ofEpochMilli(((TimeUnit) args[1]).toMillis((Long) args[0]));
            } else if (START_SPAN.equals(method)) {
                if (startInstant == null) {
                    startInstant = Instant.now();
                }
                // The Helidon span builder will notify listeners and also delegate to the OTel span builder.
                io.helidon.tracing.Span helidonSpan = helidonSpanBuilder.start(startInstant);
                return OtelProxy.span((io.helidon.tracing.Span.Extended<Scope>) helidonSpan);
            }
            return method.invoke(otelSpanBuilder, args);
        }
    }

    private record SpanHandler(Span otelSpan, io.helidon.tracing.Span.Extended<Scope> helidonSpan) implements InvocationHandler {

            private static final Method END;
            private static final Method END_INSTANT;
            private static final Method END_UNIT;
            private static final Method MAKE_CURRENT;

            static {
                try (MethodHelper methodHelper = new MethodHelper(Span.class)) {
                    END = methodHelper.method("end");
                    END_INSTANT = methodHelper.method("end", Instant.class);
                    END_UNIT = methodHelper.method("end", long.class, TimeUnit.class);
                    MAKE_CURRENT = methodHelper.method("makeCurrent");
                }
            }

        @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (END.equals(method) || END_INSTANT.equals(method) || END_UNIT.equals(method)) {
                    // The Helidon span notifies listeners and invokes its delegate's end method.
                    helidonSpan.end();
                    return null;
                } else if (MAKE_CURRENT.equals(method)) {
                    // Activating the Helidon span using helidonSpan.activate() would make the native OTel span stored inside the
                    // Helidon span the current span for OTel. Normally that would be reasonable, but instead we need the current
                    // OTel span to be our proxy so our proxy will be injected as the current span. That way, if user code
                    // operates on an injected current OTel span it will actually operate on our proxy which will notify the
                    // registered listeners.
                    Scope otelScope = Context.current().with((Span) proxy).makeCurrent();
                    io.helidon.tracing.Scope helidonScope = helidonSpan.activate(otelScope);
                    return OtelProxy.scope(helidonScope, otelScope);
                }
                return method.invoke(otelSpan, args);
            }
        }

    private record ScopeHandler(Scope otelScope, io.helidon.tracing.Scope helidonScope) implements InvocationHandler {

            private static final Method CLOSE;

            static {
                try (MethodHelper methodHelper = new MethodHelper(Scope.class)) {
                    CLOSE = methodHelper.method("close");
                }
            }

        @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (CLOSE.equals(method)) {
                    helidonScope.close();
                    return null;
                } else {
                    return method.invoke(otelScope, args);
                }
            }
        }

    private static class MethodHelper implements AutoCloseable {

        private final Class<?> declaringClass;
        private final Errors.Collector collector = Errors.collector();

        private MethodHelper(Class<?> declaringClass) {
            this.declaringClass = declaringClass;
        }

        private Method method(String name, Class<?>... args) {
            Method result = null;
            try {
                result = declaringClass.getMethod(name, args);
            } catch (NoSuchMethodException e) {
                collector.fatal(name + List.of(args));
            }
            return result;
        }

        @Override
        public void close() {
            collector.collect().checkValid();
        }
    }
}
