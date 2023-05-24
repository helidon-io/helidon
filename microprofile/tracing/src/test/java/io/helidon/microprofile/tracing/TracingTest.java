/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.tracing;

import java.util.Set;
import java.util.UUID;

import io.helidon.microprofile.server.Server;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tracing.jersey.client.ClientTracingFilter.X_OT_SPAN_CONTEXT;
import static io.helidon.tracing.jersey.client.ClientTracingFilter.X_REQUEST_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test that tracing is correctly handled.
 */
public class TracingTest {
    private static Server server;
    private static WebTarget target;
    private static WebTarget hellWorldTarget;
    private static Client client;

    @BeforeAll
    static void initClass() {
        server = Server.builder()
                .port(0)
                .addApplication(MyApp.class)
                .build()
                .start();

        client = ClientBuilder.newClient();

        WebTarget mainTarget = client.target("http://localhost:" + server.port());

        target = mainTarget.path("/test");
        hellWorldTarget = mainTarget.path("/hello");
    }

    @AfterAll
    static void destroyClass() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testTracingPropagation() {
        String xRequestId = UUID.randomUUID().toString();
        String xOtSpanContext = "span-" + xRequestId;

        // simple request - check that
        Response response = target.request()
                // these two headers are automatically propagated from request
                .header(X_REQUEST_ID, xRequestId)
                .header(X_OT_SPAN_CONTEXT, xOtSpanContext)
                .get();

        // make sure that the operation is as expected (e.g. correctly propagated)
        String headerValue = (String) response.getHeaders().getFirst("X-FRONT-X-TEST-TRACER-OPERATION");
        assertThat(headerValue, startsWith("GET"));
        headerValue = (String) response.getHeaders().getFirst("X-FRONT-" + X_REQUEST_ID);
        assertThat(headerValue, is(xRequestId));
        headerValue = (String) response.getHeaders().getFirst("X-FRONT-" + X_OT_SPAN_CONTEXT);
        assertThat(headerValue, is(xOtSpanContext));

        headerValue = (String) response.getHeaders().getFirst("X-HELLO-X-TEST-TRACER-OPERATION");
        assertThat(headerValue, startsWith("GET"));
        headerValue = (String) response.getHeaders().getFirst("X-HELLO-" + X_REQUEST_ID);
        assertThat(headerValue, is(xRequestId));
        headerValue = (String) response.getHeaders().getFirst("X-HELLO-" + X_OT_SPAN_CONTEXT);
        assertThat(headerValue, is(xOtSpanContext));

        String responseMessage = response.readEntity(String.class);
        assertThat(responseMessage, is("Hello World"));
    }

    private static void addHeaders(Response.ResponseBuilder builder, HttpHeaders headers, String prefix) {
        headers.getRequestHeaders().forEach((key, values) -> {
            values.forEach(value -> builder.header("X-" + prefix + "-" + key, value));
        });
    }

    public static class MyApp extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(
                    HelloWorld.class,
                    MyResource.class);
        }
    }

    @Path("/hello")
    public static class HelloWorld {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public Response getIt(@Context HttpHeaders headers) {
            Response.ResponseBuilder builder = Response.ok("Hello World");

            addHeaders(builder, headers, "HELLO");

            return builder.build();
        }
    }

    @Path("/test")
    public static class MyResource {
        @GET
        public Response getIt(@Context HttpHeaders headers) {

            Response response = hellWorldTarget
                    .request()
                    .accept(MediaType.TEXT_PLAIN)
                    .get();

            Response.ResponseBuilder builder = Response.fromResponse(response);
            addHeaders(builder, headers, "FRONT");
            return builder.build();
        }
    }
}
