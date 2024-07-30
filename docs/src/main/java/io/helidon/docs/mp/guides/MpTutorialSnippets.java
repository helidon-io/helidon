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

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;
import io.helidon.config.PollingStrategies;
import io.helidon.microprofile.server.Server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.metrics.annotation.Timed;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

@SuppressWarnings("ALL")
class MpTutorialSnippets {

    // stub
    static JsonObject createResponse(String str) {
        return null;
    }

    // stub
    class GreetingProvider {
        String getMessage() {
            return "";
        }

        void setMessage(String str) {
        }
    }

    class Snippet1 {

        // tag::snippet_1[]
        @Path("/greet") // <1>
        @RequestScoped // <2>
        public class GreetResource {

            private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

            @GET
            @Produces(MediaType.APPLICATION_JSON)
            public JsonObject getDefaultMessage() { // <3>
                return JSON.createObjectBuilder()
                        .add("message", "Hello World")
                        .build(); // <4>
            }

        }
        // end::snippet_1[]
    }

    class Snippet2 {

        // tag::snippet_2[]
        public final class Main {

            private Main() {
            } // <1>

            public static void main(final String[] args) throws IOException {
                Server server = startServer();
                System.out.println("http://localhost:" + server.port() + "/greet");
            }

            static Server startServer() {
                return Server.create().start(); // <2>
            }

        }
        // end::snippet_2[]
    }

    class Snippet3 {

        // tag::snippet_3[]
        @ApplicationScoped // <1>
        public class GreetingProvider {
            private final AtomicReference<String> message = new AtomicReference<>(); // <2>

            @Inject // <3>
            public GreetingProvider(@ConfigProperty(name = "app.greeting") String message) {
                this.message.set(message);
            }

            String getMessage() {
                return message.get();
            }

            void setMessage(String message) {
                this.message.set(message);
            }
        }
        // end::snippet_3[]
    }

    class Snippet4 {

        // tag::snippet_4[]
        @Path("/greet")
        @RequestScoped
        public class GreetResource {

            private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
            private final GreetingProvider greetingProvider;

            @Inject // <1>
            public GreetResource(GreetingProvider greetingConfig) {
                this.greetingProvider = greetingConfig;
            }

            @GET
            @Produces(MediaType.APPLICATION_JSON)
            public JsonObject getDefaultMessage() {
                return createResponse("World"); // <2>
            }

            private JsonObject createResponse(String who) { // <3>
                String msg = String.format("%s %s!", greetingProvider.getMessage(), who);
                return JSON.createObjectBuilder()
                        .add("message", msg)
                        .build();
            }
        }
        // end::snippet_4[]
    }

    class Snippet5 {

        // tag::snippet_5[]
        public final class Main {

            private Main() {
            }

            public static void main(final String[] args) throws IOException {
                Server server = startServer();
                System.out.println("http://localhost:" + server.port() + "/greet");
            }

            private static Config buildConfig() {
                return Config.builder()
                        .sources(
                                file("conf/mp.yaml") // <1>
                                        .pollingStrategy(PollingStrategies.regular(Duration.ofMinutes(1)))
                                        .optional(),
                                classpath("META-INF/microprofile-config.properties"))
                        .build();
            }

            static Server startServer() {
                return Server.builder()
                        .config(buildConfig()) // <2>
                        .build()
                        .start();
            }

        }
        // end::snippet_5[]
    }

    class Snippet6 {

        GreetingProvider greetingProvider;

        // tag::snippet_6[]
        @Path("/{name}")
        @GET
        @Produces(MediaType.APPLICATION_JSON)
        public JsonObject getMessage(@PathParam("name") String name) { // <1>
            return createResponse(name);
        }

        @Path("/greeting")
        @PUT
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        public Response updateGreeting(JsonObject jsonObject) { // <2>

            if (!jsonObject.containsKey("greeting")) {
                JsonObject entity = Json.createObjectBuilder()
                        .add("error", "No greeting provided")
                        .build();
                return Response.status(Response.Status.BAD_REQUEST).entity(entity).build();
            }

            String newGreeting = jsonObject.getString("greeting");

            greetingProvider.setMessage(newGreeting);
            return Response.status(Response.Status.NO_CONTENT).build();
        }
        // end::snippet_6[]
    }

    class Snippet7 {

        // tag::snippet_7[]
        @GET
        @Produces(MediaType.APPLICATION_JSON)
        @Timed // <1>
        public JsonObject getDefaultMessage() {
            return createResponse("World");
        }
        // end::snippet_7[]
    }

    class Snippet8 {

        // tag::snippet_8[]
        @Liveness // <1>
        @ApplicationScoped // <2>
        public class GreetHealthcheck implements HealthCheck {

            private GreetingProvider provider;

            @Inject // <3>
            public GreetHealthcheck(GreetingProvider provider) {
                this.provider = provider;
            }

            @Override
            public HealthCheckResponse call() { // <4>
                String message = provider.getMessage();
                return HealthCheckResponse.named("greeting") // <5>
                        .status("Hello".equals(message))
                        .withData("greeting", message)
                        .build();
            }
        }
        // end::snippet_8[]
    }

}
