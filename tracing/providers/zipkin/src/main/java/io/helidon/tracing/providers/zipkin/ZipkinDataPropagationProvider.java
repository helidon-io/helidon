/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import io.helidon.common.context.Contexts;
import io.helidon.common.context.spi.DataPropagationProvider;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

/**
 * Data propagation provider for the Helidon Zipkin tracing provider.
 */
public class ZipkinDataPropagationProvider implements DataPropagationProvider<ZipkinDataPropagationProvider.ZipkinContext> {

    private static final System.Logger LOGGER = System.getLogger(ZipkinDataPropagationProvider.class.getName());

    @Override
    public ZipkinContext data() {
        Tracer tracer = Contexts.context()
                .flatMap(ctx -> ctx.get(Tracer.class))
                .orElseGet(GlobalTracer::get);
        Span span = tracer.activeSpan();
        return new ZipkinContext(tracer, span);
    }

    @Override
    public void propagateData(ZipkinContext data) {
        if (data != null && data.span != null) {
            data.scope = data.tracer.activateSpan(data.span);
        }
    }

    @Override
    public void clearData(ZipkinContext data) {
        if (data != null && data.scope != null) {
            try {
                data.scope.close();
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.TRACE, "Cannot close tracing span", e);
            }
        }
    }

    /**
     * Zipkin-specific propagation context.
     */
    public static class ZipkinContext {

        private final Tracer tracer;
        private final Span span;
        private Scope scope;

        private ZipkinContext(Tracer tracer, Span span) {
            this.tracer = tracer;
            this.span = span;
        }
    }
}
