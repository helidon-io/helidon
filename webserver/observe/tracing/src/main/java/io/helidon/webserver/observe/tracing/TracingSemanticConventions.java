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

package io.helidon.webserver.observe.tracing;

import io.helidon.tracing.Span;

/**
 * Applies a particular set of semantic conventions to spans created automatically for requests.
 * <p>
 * Helidon creates a new instance of this interface for each request it traces by invoking
 * {@linkplain io.helidon.webserver.observe.tracing.spi.TracingSemanticConventionsProvider#create(io.helidon.tracing.config.SpanTracingConfig, String, io.helidon.webserver.http.RoutingRequest, io.helidon.webserver.http.RoutingResponse)
 * TracingSemanticConventionsProvider#create}.
 */
public interface TracingSemanticConventions {

    /**
     * Provides the span name Helidon should use to construct the {@link io.helidon.tracing.Span.Builder}.
     *
     * @return span name
     */
    String spanName();

    /**
     * Applies semantic conventions to the {@link io.helidon.tracing.Span.Builder} just prior to Helidon using the span
     * builder to create the span for the request.
     *
     * @param spanBuilder span builder to update
     */
    void beforeStart(Span.Builder<?> spanBuilder);

    /**
     * Applies semantic conventions to the {@link io.helidon.tracing.Span} after Helidon has
     * successfully processed the request and prepared the response and just before it ends the span; that is, Helidon has set
     * the response status and entity (if any) and no exception escaped from the request processing.
     * <p>
     * The lack of an exception does not necessarily indicate the response has a successful status, only that
     * the request was processed and the response prepared without error.
     *
     * @param span span about to be ended
     */
    void beforeEnd(Span span);

    /**
     * Applies semantic conventions the {@link io.helidon.tracing.Span} after Helidon has attempted to process the request just
     * before Helidon ends the span unsuccessfully (with an exception). Implementations of this method should not assume that
     * the response status or entity (if appropriate for a given response) have been set,
     *
     * @param span span about to be ended
     * @param e exception thrown as Helidon processed the request
     */
    void beforeEnd(Span span, Exception e);
}
