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
import java.util.function.Consumer;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.tracing.SpanListener;
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

    static final String UNSUPPORTED_OPERATION_MESSAGE = "Span listener attempted to invoke an illegal operation";

    private static final LazyValue<List<SpanListener>> SPAN_LISTENERS =
            LazyValue.create(() -> HelidonServiceLoader.create(ServiceLoader.load(SpanListener.class)).asList());

    private final BraveTracer tracer;
    private final List<Tag<?>> tags;

    private final List<SpanListener> spanListeners = new ArrayList<>(SPAN_LISTENERS.get());

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
                                     spanListeners);
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
                                      spanListeners);
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
