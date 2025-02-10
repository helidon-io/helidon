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

import java.io.IOException;
import java.net.URI;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@SuppressWarnings("ALL")
class RestclientSnippets {

    // stub
    static class GreetClientRequestFilter implements ClientRequestFilter {

        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
        }
    }

    // stub
    static class GreetClientExceptionMapper implements ExceptionMapper<Exception> {

        @Override
        public Response toResponse(Exception exception) {
            return Response.serverError().build();
        }
    }

    void snippet_1() {
        // tag::snippet_1[]
        GreetRestClient greetResource = RestClientBuilder.newBuilder()
                .baseUri(URI.create("http://localhost:8080/greet"))
                .build(GreetRestClient.class);
        greetResource.getDefaultMessage();
        // end::snippet_1[]
    }

    void snippet_2() {
        // tag::snippet_2[]
        GreetRestClient greetResource = RestClientBuilder.newBuilder()
                .baseUri(URI.create("http://localhost:8080"))
                .register(GreetClientRequestFilter.class)
                .register(GreetClientExceptionMapper.class)
                .build(GreetRestClient.class);
        greetResource.getDefaultMessage();
        // end::snippet_2[]
    }

    class Snippet3 {

        // tag::snippet_3[]
        @Path("/greet")
        @RegisterRestClient(baseUri = "http://localhost:8080")
        public interface GreetRestClient {
            // ...
        }
        // end::snippet_3[]
    }

    class Snippet4 {
        // tag::snippet_4[]
        @Path("/greet")
        @RegisterRestClient(baseUri = "http://localhost:8080")
        @RegisterProvider(GreetClientRequestFilter.class)
        @RegisterProvider(GreetClientExceptionMapper.class)
        public interface GreetRestClient {
            // ...
        }
        // end::snippet_4[]
    }

    // tag::snippet_5[]
    public class MyBean {
        @Inject
        @RestClient
        GreetRestClient client;

        void myMethod() {
            client.getMessage("Helidon");
        }
    }
    // end::snippet_5[]

    // tag::snippet_6[]
    @Path("/greet")
    interface GreetRestClient {

        @GET
        JsonObject getDefaultMessage();

        @Path("/{name}")
        @GET
        JsonObject getMessage(@PathParam("name") String name);

    }
    // end::snippet_6[]

}
