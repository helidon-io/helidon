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

import io.helidon.tracing.providers.opentelemetry.HelidonOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.server.Uri;


/**
 * A simple JAX-RS resource to greet you. Examples:
 *
 * Get default greeting message:
 * curl -X GET http://localhost:8080/greet
 *
 * Get Span information:
 * curl -X GET http://localhost:8080/greet/span
 *
 *  Call secondary service:
 *  curl -X GET http://localhost:8080/greet/outbound
 */
@Path("/greet")
public class GreetResource {

    private Span span;

    private Tracer tracer;

    private io.helidon.tracing.Tracer helidonTracerInjected;


    @Uri("http://localhost:8081/secondary")
    private WebTarget target;

    @Inject
    GreetResource(Span span, Tracer tracer, io.helidon.tracing.Tracer helidonTracerInjected) {
        this.span = span;
        this.tracer = tracer;
        this.helidonTracerInjected = helidonTracerInjected;
    }

    /**
     * Return a worldly greeting message.
     *
     * @return {@link String}
     */
    @GET
    @WithSpan("default")
    public String getDefaultMessage() {
        return "Hello World";
    }

    /**
     *  Create an internal custom span and return its description.
     * @return {@link GreetingMessage}
     */
    @GET
    @Path("custom")
    @Produces(MediaType.APPLICATION_JSON)
    @WithSpan
    public GreetingMessage useCustomSpan() {
        Span span = tracer.spanBuilder("custom")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("attribute", "value")
                .startSpan();
        span.end();

        return new GreetingMessage("Custom Span" + span);
    }

    /**
     *  Create a helidon mixed span and return its description.
     * @return {@link GreetingMessage}
     */
    @GET
    @Path("mixed")
    @Produces(MediaType.APPLICATION_JSON)
    @WithSpan("mixed_parent")
    public GreetingMessage mixedSpan() {

        io.helidon.tracing.Tracer helidonTracer = io.helidon.tracing.Tracer.global();
        io.helidon.tracing.Span mixedSpan = helidonTracer.spanBuilder("mixed")
                .kind(io.helidon.tracing.Span.Kind.SERVER)
                .tag("attribute", "value")
                .start();
        mixedSpan.end();

        return new GreetingMessage("Mixed Span" + span);
    }

    /**
     *  Create a helidon mixed span and return its description.
     * @return {@link GreetingMessage}
     */
    @GET
    @Path("mixed_injected")
    @Produces(MediaType.APPLICATION_JSON)
    @WithSpan("mixed_parent_injected")
    public GreetingMessage mixedSpanInjected() {
        io.helidon.tracing.Span mixedSpan = helidonTracerInjected.spanBuilder("mixed_injected")
                .kind(io.helidon.tracing.Span.Kind.SERVER)
                .tag("attribute", "value")
                .start();
        mixedSpan.end();

        return new GreetingMessage("Mixed Span Injected" + span);
    }

    /**
     * Get Span info.
     *
     * @return {@link GreetingMessage}
     */
    @GET
    @Path("span")
    @Produces(MediaType.APPLICATION_JSON)
    @WithSpan
    public GreetingMessage getSpanInfo() {
        return new GreetingMessage("Span " + span.toString());
    }

    /**
     * Call the secondary service running on port 8081.
     *
     * @return String from the secondary service.
     */
    @GET
    @Path("/outbound")
    @WithSpan("outbound")
    public String outbound() {
        return target.request().accept(MediaType.TEXT_PLAIN).get(String.class);
    }

}
