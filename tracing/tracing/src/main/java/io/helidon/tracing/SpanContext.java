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
package io.helidon.tracing;

/**
 * Context of a tracing {@link io.helidon.tracing.Span}.
 */
public interface SpanContext {
    /**
     * Trace ID of the associated span.
     *
     * @return trace id
     */
    String traceId();

    /**
     * Span ID of the associated span.
     *
     * @return span id
     */
    String spanId();

    /**
     * Configure this context as a parent of the provided builder.
     *
     * @param spanBuilder span builder to update, it will be a child of this span context
     */
    void asParent(Span.Builder<?> spanBuilder);

    /**
     * Returns the baggage extractable from the span context.
     *
     * @return {@link io.helidon.tracing.Baggage} instance; empty if no baggage is available from the span context
     */
    Baggage baggage();
}
