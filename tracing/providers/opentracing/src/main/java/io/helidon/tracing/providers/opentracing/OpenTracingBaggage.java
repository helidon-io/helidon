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
package io.helidon.tracing.providers.opentracing;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

import io.helidon.tracing.Baggage;
import io.helidon.tracing.WritableBaggage;

import io.opentracing.Span;
import io.opentracing.SpanContext;

/**
 * Implements the read-only Helidon {@link io.helidon.tracing.Baggage} interface using a private map loaded from an
 * OpenTracing {@code SpanContext} baggage contents.
 */
class OpenTracingBaggage implements Baggage {

    private final Map<String, String> baggage = new HashMap<>();

    OpenTracingBaggage(OpenTracingContext context) {
        SpanContext spanContext = context.openTracing();
        StreamSupport.stream(spanContext.baggageItems().spliterator(), false)
                .forEach(entry -> baggage.put(entry.getKey(), entry.getValue()));
    }

    static Writable create(OpenTracingContext context, Span openTracingSpan) {
        return new Writable(context, openTracingSpan);
    }

    static OpenTracingBaggage create(OpenTracingContext context) {
        return new OpenTracingBaggage(context);
    }

    @Override
    public Optional<String> get(String key) {
        Objects.requireNonNull(key, "baggage key cannot be null");
        return Optional.ofNullable(baggage.get(key));
    }

    @Override
    public Set<String> keys() {
        return Collections.unmodifiableSet(baggage.keySet());
    }

    @Override
    public boolean containsKey(String key) {
        Objects.requireNonNull(key, "baggage key cannot be null");
        return baggage.containsKey(key);
    }

    protected void setValue(String key, String value) {
        Objects.requireNonNull(key, "baggage key cannot be null");
        Objects.requireNonNull(value, "baggage value cannot be null");
        baggage.put(key, value);
    }

    /**
     * Implementation of Helidon's {@link io.helidon.tracing.WritableBaggage} interface based on OpenTracing baggage retrieved
     * from an OpenTracing context and also linked to an existing OpenTracing {@link io.opentracing.Span}.
     */
    static class Writable extends OpenTracingBaggage implements WritableBaggage {

        private final Span span;

        Writable(OpenTracingContext context, Span openTracingSpan) {
            super(context);
            span = openTracingSpan;
        }

        @Override
        public WritableBaggage set(String key, String value) {
            super.setValue(key, value);
            span.setBaggageItem(key, value);
            return this;
        }
    }
}
