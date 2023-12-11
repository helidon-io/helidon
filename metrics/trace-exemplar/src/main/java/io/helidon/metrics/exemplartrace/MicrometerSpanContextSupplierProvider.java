/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.exemplartrace;

import io.helidon.common.context.Contexts;
import io.helidon.tracing.SpanContext;

import io.prometheus.client.exemplars.tracer.common.SpanContextSupplier;

/**
 * Full-featured implementation of provider for trace information to support exemplars.
 */
public class MicrometerSpanContextSupplierProvider
        implements io.helidon.metrics.providers.micrometer.spi.SpanContextSupplierProvider {
    @Override
    public SpanContextSupplier get() {
        return new SpanContextSupplierImpl();
    }

    private record SpanContextSupplierImpl() implements SpanContextSupplier {

        @Override
        public String getTraceId() {
            return spanContext() != null ? spanContext().traceId() : null;
        }

        @Override
        public String getSpanId() {
            return spanContext() != null ? spanContext().spanId() : null;
        }

        @Override
        public boolean isSampled() {
            return spanContext() != null;
        }

        public SpanContext spanContext() {
            return Contexts.context()
                    .flatMap(c -> c.get(SpanContext.class))
                    .orElse(null);
        }
    }
}
