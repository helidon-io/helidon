/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.tracing.jaeger;

import io.helidon.common.context.Contexts;
import io.helidon.common.context.spi.DataPropagationProvider;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

/**
 * A data propagation provider for Jaeger. Makes sure span are properly propagated
 * across threads managed by {@link io.helidon.common.context.ContextAwareExecutorService}.
 */
public class JaegerDataPropagationProvider implements DataPropagationProvider<JaegerDataPropagationProvider.JaegerContext> {

    static class JaegerContext {
        private final Span span;
        private final Tracer tracer;
        private Scope scope;

        JaegerContext(Tracer tracer, Span span) {
            this.tracer = tracer;
            this.span = span;
        }

        Scope scope() {
            return scope;
        }
    }

    /**
     * Closes scope in primary thread and returns a context to activate
     * new scope in secondary thread.
     *
     * @return active span.
     */
    @Override
    public JaegerContext data() {
        return Contexts.context().map(context -> {
            context.get(Scope.class).ifPresent(Scope::close);
            return context.get(Span.class).map(span -> {
                Tracer tracer = context.get(Tracer.class).orElseGet(GlobalTracer::get);
                return new JaegerContext(tracer, span);
            }).orElse(null);
        }).orElse(null);
    }

    /**
     * Activates scope in secondary thread.
     *
     * @param context the context.
     */
    @Override
    public void propagateData(JaegerContext context) {
        if (context != null) {
            context.scope = context.tracer.scopeManager().activate(context.span);
        }
    }

    /**
     * Closes scope in secondary thread.
     */
    @Override
    public void clearData(JaegerContext context) {
        if (context != null && context.scope != null) {
            context.scope.close();
        }
    }
}
