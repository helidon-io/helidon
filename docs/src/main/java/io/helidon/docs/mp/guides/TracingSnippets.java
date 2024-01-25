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
package io.helidon.docs.mp.guides;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.opentracing.Traced;
import org.glassfish.jersey.server.Uri;

@SuppressWarnings("ALL")
class TracingSnippets {

    class Snippet1 {

        AtomicReference<String> message  = new AtomicReference<>();

        // tag::snippet_1[]
        class MyClass {
            @Traced // <1>
            String getMessage() {
                return message.get();
            }
        }
        // end::snippet_1[]
    }

    class Snippet2 {

        AtomicReference<String> message  = new AtomicReference<>();

        // tag::snippet_2[]
        @Traced // <1>
        @ApplicationScoped
        public class GreetingProvider {

            String getMessage() { // <2>
                return message.get();
            }
        }
        // end::snippet_2[]
    }

    class Snippet3 {

        // tag::snippet_3[]
        @ApplicationScoped
        public class GreetingProvider {
            private final AtomicReference<String> message = new AtomicReference<>();

            @Inject
            public GreetingProvider(@ConfigProperty(name = "app.greeting") String message) {
                this.message.set(message);
            }

            @Traced // <1>
            String getMessage() {
                return getMessage2();
            }

            @Traced // <2>
            String getMessage2() {
                return message.get();
            }

            void setMessage(String message) {
                this.message.set(message);
            }
        }
        // end::snippet_3[]
    }

    static class GreetingProvider {
        String getMessage() {
            return "";
        }
    }

    class Snippet4 {

        // tag::snippet_4[]
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
        // end::snippet_4[]
    }
}
