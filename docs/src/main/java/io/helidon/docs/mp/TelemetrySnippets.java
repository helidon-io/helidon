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
package io.helidon.docs.mp;

import io.helidon.microprofile.telemetry.spi.HelidonTelemetryClientFilterHelper;
import io.helidon.microprofile.telemetry.spi.HelidonTelemetryContainerFilterHelper;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.Uri;

@SuppressWarnings("ALL")
class TelemetrySnippets {

    // stub
    static class GreetingMessage {

        GreetingMessage(String msg) {
        }
    }

    // tag::snippet_1[]
    @ApplicationScoped
    class HelidonBean {

        @WithSpan // <1>
        void doSomethingWithinSpan() {
            // do something here
        }

        @WithSpan("name") // <2>
        void complexSpan(@SpanAttribute(value = "arg") String arg) {
            // do something here
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @Path("/")
    public class HelidonEndpoint {

        @Inject
        Tracer tracer; // <1>

        @GET
        @Path("/span")
        public Response span() {
            Span span = tracer.spanBuilder("new") // <2>
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute("someAttribute", "someValue")
                    .startSpan();

            span.end();

            return Response.ok().build();
        }
    }
    // end::snippet_2[]

    static class GreetResource {
        // tag::snippet_3[]
        private io.helidon.tracing.Tracer helidonTracerInjected;

        @Inject
        GreetResource(io.helidon.tracing.Tracer helidonTracerInjected) {
            this.helidonTracerInjected = helidonTracerInjected; // <1>
        }

        @GET
        @Path("mixed_injected")
        @Produces(MediaType.APPLICATION_JSON)
        @WithSpan("mixed_parent_injected")
        public GreetingMessage mixedSpanInjected() {
            io.helidon.tracing.Span mixedSpan = helidonTracerInjected.spanBuilder("mixed_injected") // <2>
                    .kind(io.helidon.tracing.Span.Kind.SERVER)
                    .tag("attribute", "value")
                    .start();
            mixedSpan.end();

            return new GreetingMessage("Mixed Span Injected" + mixedSpan);
        }
        // end::snippet_3[]

        // tag::snippet_4[]
        @GET
        @Path("mixed")
        @Produces(MediaType.APPLICATION_JSON)
        @WithSpan("mixed_parent")
        public GreetingMessage mixedSpan() {
            io.helidon.tracing.Tracer helidonTracer = io.helidon.tracing.Tracer.global(); // <1>
            io.helidon.tracing.Span mixedSpan = helidonTracer.spanBuilder("mixed") // <2>
                    .kind(io.helidon.tracing.Span.Kind.SERVER)
                    .tag("attribute", "value")
                    .start();
            mixedSpan.end();

            return new GreetingMessage("Mixed Span" + mixedSpan);
        }
        // end::snippet_4[]
    }

    class Snippet5 {

        // tag::snippet_5[]
        @Path("/")
        public class HelidonEndpoint {
            @Inject
            Span span; // <1>

            @GET
            @Path("/current")
            public Response currentSpan() {
                return Response.ok(span).build(); // <2>
            }

            @GET
            @Path("/current/static")
            public Response currentSpanStatic() {
                return Response.ok(Span.current()).build(); // <3>
            }
        }
        // end::snippet_5[]
    }

    class Snippet6 {

        // tag::snippet_6[]
        @Path("/")
        public class HelidonEndpoint {
            @Inject
            Baggage baggage; // <1>

            @GET
            @Path("/current")
            public Response currentBaggage() {
                return Response.ok(baggage.getEntryValue("baggageKey")).build(); // <2>
            }

            @GET
            @Path("/current/static")
            public Response currentBaggageStatic() {
                return Response.ok(Baggage.current().getEntryValue("baggageKey")).build(); // <3>
            }
        }
        // end::snippet_6[]
    }

    class Snippet7_10 {

        // tag::snippet_7[]
        @Path("/greet")
        public class GreetResource {

            @GET
            @WithSpan("default") // <1>
            public String getDefaultMessage() {
                return "Hello World";
            }
        }
        // end::snippet_7[]

        // tag::snippet_8[]
        @Inject
        private Tracer tracer; // <1>

        @GET
        @Path("custom")
        @Produces(MediaType.APPLICATION_JSON)
        @WithSpan // <2>
        public JsonObject useCustomSpan() {
            Span span = tracer.spanBuilder("custom") // <3>
                    .setSpanKind(SpanKind.INTERNAL)
                    .setAttribute("attribute", "value")
                    .startSpan();
            span.end(); // <4>

            return Json.createObjectBuilder()
                    .add("Custom Span", span.toString())
                    .build();
        }
        // end::snippet_8[]

        // tag::snippet_9[]
        @Uri("http://localhost:8081/secondary")
        private WebTarget target; // <1>

        @GET
        @Path("/outbound")
        @WithSpan("outbound") // <2>
        public String outbound() {
            return target.request().accept(MediaType.TEXT_PLAIN).get(String.class); // <3>
        }
        // end::snippet_9[]

        // tag::snippet_10[]
        @GET
        @WithSpan // <1>
        public String getSecondaryMessage() {
            return "Secondary"; // <2>
        }
        // end::snippet_10[]
    }

    class FilterHelperSnippets_11_to_12 {

        // tag::snippet_11[]
        public class CustomRestRequestFilterHelper implements HelidonTelemetryContainerFilterHelper {

            @Override
            public boolean shouldStartSpan(ContainerRequestContext containerRequestContext) {

                // Allows automatic spans for incoming requests for the default greeting but not for
                // personalized greetings or the PUT request to update the greeting message.
                return containerRequestContext.getUriInfo().getPath().endsWith("greet");
            }
        }
        // end::snippet_11[]

        // tag::snippet_12[]
        public class CustomRestClientRequestFilterHelper implements HelidonTelemetryClientFilterHelper {

            @Override
            public boolean shouldStartSpan(ClientRequestContext clientRequestContext) {

                // Allows automatic spans for outgoing requests for the default greeting but not for
                // personalized greetings or the PUT request to update the greeting message.
                return clientRequestContext.getUri().getPath().endsWith("greet");
            }
        }
        // end::snippet_12[]
    }

}
