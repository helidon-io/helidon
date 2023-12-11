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

package io.helidon.examples.microprofile.telemetry;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * A simple JAX-RS resource to use `io.opentelemetry` and `io.helidon.api` in mixed mode. Examples:
 *
 * Get mixed traces with Global tracer:
 * curl -X GET http://localhost:8080/mixed
 *
 * Get mixed traces with an injected Helidon tracer:
 * curl -X GET http://localhost:8080/mixed/injected
 *
 * Explore traces in Jaeger UI.
 */
@Path("/mixed")
public class MixedTelemetryGreetResource {

    private io.helidon.tracing.Tracer helidonTracerInjected;

    @Inject
    MixedTelemetryGreetResource(io.helidon.tracing.Tracer helidonTracerInjected) {
        this.helidonTracerInjected = helidonTracerInjected;
    }


    /**
     * Create a helidon mixed span using Helidon Global tracer.
     * @return {@link GreetingMessage}
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @WithSpan("mixed_parent")
    public GreetingMessage mixedSpan() {

        io.helidon.tracing.Tracer helidonTracer = io.helidon.tracing.Tracer.global();
        io.helidon.tracing.Span mixedSpan = helidonTracer.spanBuilder("mixed_inner")
                .kind(io.helidon.tracing.Span.Kind.SERVER)
                .tag("attribute", "value")
                .start();
        mixedSpan.end();

        return new GreetingMessage("Mixed Span");
    }

    /**
     * Create a helidon mixed span using injected Helidon Tracer.
     * @return {@link GreetingMessage}
     */
    @GET
    @Path("injected")
    @Produces(MediaType.APPLICATION_JSON)
    @WithSpan("mixed_parent_injected")
    public GreetingMessage mixedSpanInjected() {
        io.helidon.tracing.Span mixedSpan = helidonTracerInjected.spanBuilder("mixed_injected_inner")
                .kind(io.helidon.tracing.Span.Kind.SERVER)
                .tag("attribute", "value")
                .start();
        mixedSpan.end();

        return new GreetingMessage("Mixed Span Injected");
    }
}
