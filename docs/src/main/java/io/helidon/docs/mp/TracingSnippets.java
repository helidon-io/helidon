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

import java.net.URI;
import java.util.Map;

import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.Uri;

import static io.helidon.tracing.jersey.client.ClientTracingFilter.CURRENT_SPAN_CONTEXT_PROPERTY_NAME;
import static io.helidon.tracing.jersey.client.ClientTracingFilter.TRACER_PROPERTY_NAME;

@SuppressWarnings("ALL")
class TracingSnippets {

    // stub
    static class GreetingProvider {
        String getMessage() {
            return "";
        }
    }

    // tag::snippet_1[]
    @Path("/greet")
    @RequestScoped
    public class GreetResource {

        @Uri("http://localhost:8081/greet")
        private WebTarget target; // <1>

        private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
        private final GreetingProvider greetingProvider;

        @Inject
        public GreetResource(GreetingProvider greetingConfig) {
            this.greetingProvider = greetingConfig;
        }

        @GET
        @Produces(MediaType.APPLICATION_JSON)
        public JsonObject getDefaultMessage() {
            return createResponse("World");
        }

        @GET
        @Path("/outbound") // <2>
        public JsonObject outbound() {
            return target.request().accept(MediaType.APPLICATION_JSON_TYPE).get(JsonObject.class);
        }

        private JsonObject createResponse(String who) {
            String msg = String.format("%s %s!", greetingProvider.getMessage(), who);

            return JSON.createObjectBuilder().add("message", msg).build();
        }
    }
    // end::snippet_1[]

    void snippet_2(Client client, URI serviceEndpoint, Tracer tracer, SpanContext spanContext) {
        // tag::snippet_2[]

        Response response = client.target(serviceEndpoint)
                .request()
                // tracer should be provided unless available as GlobalTracer
                .property(TRACER_PROPERTY_NAME, tracer)
                .property(CURRENT_SPAN_CONTEXT_PROPERTY_NAME, spanContext)
                .get();
        // end::snippet_2[]
    }
}
