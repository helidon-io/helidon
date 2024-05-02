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

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanListener;

import io.opentracing.Tracer;

/**
 * Open Tracing factory methods to create wrappers for Open Tracing types.
 */
public final class OpenTracing {

    static final String UNSUPPORTED_OPERATION_MESSAGE = "Span listener attempted to invoke an illegal operation";

    private static final LazyValue<List<SpanListener>> SPAN_LISTENERS =
            LazyValue.create(() -> HelidonServiceLoader.create(ServiceLoader.load(SpanListener.class)).asList());


    private OpenTracing() {
    }

    /**
     * Wrap an open tracing tracer.
     *
     * @param tracer tracer
     * @return Helidon {@link io.helidon.tracing.Tracer}
     */
    public static io.helidon.tracing.Tracer create(Tracer tracer) {
        return OpenTracingTracer.create(tracer);
    }

    /**
     * Wrap an open tracing span.
     *
     * @param tracer the tracer that created the span
     * @param span open telemetry span
     * @return Helidon {@link io.helidon.tracing.Span}
     */
    public static Span create(Tracer tracer, io.opentracing.Span span) {
        return new OpenTracingSpan(tracer, span, SPAN_LISTENERS.get());
    }

    static void invokeListeners(List<SpanListener> spanListeners, System.Logger logger, Consumer<SpanListener> operation) {
        if (spanListeners.isEmpty()) {
            return;
        }
        List<Throwable> throwables = new ArrayList<>();
        for (SpanListener listener : spanListeners) {
            try {
                operation.accept(listener);
            } catch (Throwable t) {
                throwables.add(t);
            }
        }

        Throwable throwableToLog = null;
        if (throwables.size() == 1) {
            // If only one exception is present, propagate that one in the log record.
            throwableToLog = throwables.getFirst();
        } else if (!throwables.isEmpty()) {
            // Propagate a RuntimeException with multiple suppressed exceptions in the log record.
            throwableToLog = new RuntimeException();
            throwables.forEach(throwableToLog::addSuppressed);
        }
        if (throwableToLog != null) {
            logger.log(System.Logger.Level.WARNING, "Error(s) from listener(s)", throwableToLog);
        }
    }

}
