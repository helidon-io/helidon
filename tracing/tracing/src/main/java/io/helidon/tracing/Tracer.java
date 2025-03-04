/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

import java.util.Optional;

/**
 * Tracer abstraction.
 * Tracer is the central point that collects tracing spans, and (probably) pushes them to backend.
 */
public interface Tracer {
    /**
     * Create a no-op tracer. All spans created from this tracer are not doing anything.
     *
     * @return no-op tracer
     */
    static Tracer noOp() {
        return NoOpTracer.instance();
    }

    /**
     * Get the currently registered global tracer.
     *
     * @return global tracer
     */
    static Tracer global() {
        return TracerProviderHelper.global();
    }

    /**
     * Register a global tracer, behavior depends on implementation.
     *
     * @param tracer tracer to use as a global tracer
     */

    static void global(Tracer tracer) {
        TracerProviderHelper.global(tracer);
    }

    /**
     * Whether this tracer is enabled or not.
     * A no op tracer is disabled.
     *
     * @return {@code true} if this tracer is enabled
     */
    boolean enabled();

    /**
     * A new span builder to construct {@link io.helidon.tracing.Span}.
     *
     * @param name name of the operation
     * @return a new span builder
     */
    Span.Builder<?> spanBuilder(String name);

    /**
     * Extract parent span context from inbound request, such as from HTTP headers.
     *
     * @param headersProvider provider of headers
     * @return span context of inbound parent span, or empty optional if no span context can be found
     */
    Optional<SpanContext> extract(HeaderProvider headersProvider);

    /**
     * Inject current span as a parent for outbound request, such as when invoking HTTP request from a client.
     *
     * @param spanContext current span context
     * @param inboundHeadersProvider provider of inbound headers, may be {@link HeaderProvider#empty()} or headers from original
     *                               request (if any)
     * @param outboundHeadersConsumer consumer of headers that should be propagated to remote endpoint
     */
    void inject(SpanContext spanContext, HeaderProvider inboundHeadersProvider, HeaderConsumer outboundHeadersConsumer);

    /**
     * Access the underlying tracer by specific type.
     * This is a dangerous operation that will succeed only if the tracer is of expected type. This practically
     * removes abstraction capabilities of this API.
     *
     * @param tracerClass type to access
     * @return instance of the tracer
     * @param <T> type of the tracer
     * @throws java.lang.IllegalArgumentException in case the tracer cannot provide the expected type
     */
    default <T> T unwrap(Class<T> tracerClass) {
        try {
            return tracerClass.cast(this);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("This tracer is not compatible with " + tracerClass.getName());
        }
    }

    /**
     * Registers with the tracer a {@linkplain io.helidon.tracing.SpanListener lifecycle event listener} to receive events from
     * span builders, spans, and scopes derived from this tracer.
     *
     * @param listener the {@link SpanListener} to register
     * @return the updated {@code Tracer}
     */
    Tracer register(SpanListener listener);

    /**
     * Unregisters the specified {@link io.helidon.tracing.SpanListener} from the {@code Tracer}.
     *
     * @param listener the {@code SpanListener} to unregister
     * @return the {@code Tracer}
     */
    default Tracer unregister(SpanListener listener) {
        return this;
    }
}
