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
package io.helidon.docs.mp.restclient;

import java.net.URI;

import io.helidon.common.LazyValue;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import static io.helidon.docs.mp.restclient.RestclientMetricsSnippets.Snippet1.GreetRestClient;

@SuppressWarnings("ALL")
class RestclientMetricsSnippets {

    // stub
    static interface GreetingMessage { }


    class Snippet1 {

        // tag::snippet_1[]
        @Path("/greet")
        @Timed(name = "timedGreet", absolute = true) // <1>
        public interface GreetRestClient {

            @Counted                            // <2>
            @GET
            @Produces(MediaType.APPLICATION_JSON)
            GreetingMessage getDefaultMessage();

            @Path("/{name}")
            @GET
            @Produces(MediaType.APPLICATION_JSON)
            GreetingMessage getMessage(@PathParam("name") String name);

            @Path("/greeting")
            @PUT
            @Consumes(MediaType.APPLICATION_JSON)
            @Produces(MediaType.APPLICATION_JSON)
            Response updateGreeting(GreetingMessage message);
        }
        // end::snippet_1[]
    }

    class Snippet2 {

        // tag::snippet_2[]
        @Path("/delegate")
        public class DelegatingResource {

            private static LazyValue<GreetRestClient> greetRestClient = LazyValue.create(DelegatingResource::prepareClient); // <1>

            /**
             * Return a worldly greeting message.
             *
             * @return {@link GreetingMessage}
             */
            @GET
            @Produces(MediaType.APPLICATION_JSON)
            public GreetingMessage getDefaultMessage() {
                return greetRestClient.get().getDefaultMessage();           // <2>
            }

            /**
             * Return a greeting message using the name that was provided.
             *
             * @param name the name to greet
             * @return {@link GreetingMessage}
             */
            @Path("/{name}")
            @GET
            @Produces(MediaType.APPLICATION_JSON)
            public GreetingMessage getMessage(@PathParam("name") String name) {
                return greetRestClient.get().getMessage(name);
            }

            /**
             * Set the greeting to use in future messages.
             *
             * @param message JSON containing the new greeting
             * @return {@link jakarta.ws.rs.core.Response}
             */
            @Path("/greeting")
            @PUT
            @Consumes(MediaType.APPLICATION_JSON)
            @Produces(MediaType.APPLICATION_JSON)
            public Response updateGreeting(GreetingMessage message) {
                return greetRestClient.get().updateGreeting(message);
            }

            private static GreetRestClient prepareClient() {            // <3>
                Config config = ConfigProvider.getConfig();
                String serverHost = config.getOptionalValue("server.host", String.class).orElse("localhost");
                String serverPort = config.getOptionalValue("server.port", String.class).orElse("8080");
                return RestClientBuilder.newBuilder()
                        .baseUri(URI.create("http://" + serverHost + ":" + serverPort))
                        .build(GreetRestClient.class);
            }
        }
        // end::snippet_2[]
    }

}
