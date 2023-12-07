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

package io.helidon.microprofile.server;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JaxRsApplicationCodesTest {

    private static Server server;
    private static Client client;
    private static int port;

    @BeforeAll
    static void beforeAll() {
        server = Server.builder().addApplication(MyApplication.class).build();
        server.start();
        port = server.port();
        client = ClientBuilder.newBuilder().build();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
        client.close();
    }

    @Test
    void codesTest() {
        for (Response.Status status : Response.Status.values()) {
            testChecks(status);
        }
    }

    @Test
    void issue304Test() {
        testChecks(Response.Status.NO_CONTENT);
        testChecks(Response.Status.NOT_MODIFIED);
        testChecks(Response.Status.OK);
    }

    private void testChecks(Response.Status status) {
        Entity<String> entity = Entity.text(String.valueOf(status.getStatusCode()));
        int statusCode = client.target("http://localhost:" + port)
                .path("/resource/setstatus")
                .request()
                .post(entity)
                .getStatus();
        assertThat(statusCode, is(status.getStatusCode()));
    }
    
    @ApplicationPath("/")
    static class MyApplication extends Application {

        @Override
        public java.util.Set<java.lang.Class<?>> getClasses() {
            Set<Class<?>> resources = new HashSet<Class<?>>();
            resources.add(TestResource.class);
            resources.add(TestFilter.class);
            return resources;
        }
    }

    @Path("/resource")
    public static class TestResource {

        @Context
        UriInfo info;

        @POST
        @Path("setstatus")
        public Response setStatus(String status) {
            ResponseBuilder builder = createResponseWithHeader();
            Response response = builder.entity(status).build();
            return response;
        }

        private ResponseBuilder createResponseWithHeader() {
            Response.ResponseBuilder builder = Response.ok();
            // set a header with ContextOperation so that the filter knows what to do
            builder = builder.header("OPERATION", "SETSTATUS");
            return builder;
        }
    }

    @Provider
    public static class TestFilter implements ContainerResponseFilter {

        @Override
        public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
                throws IOException {
            String entity = (String) responseContext.getEntity();
            int status = Integer.parseInt(entity);
            responseContext.setStatus(status);
            resetStatusEntity(status, responseContext);
        }

        private void resetStatusEntity(int status, ContainerResponseContext responseContext) {
            switch (status) {
            case 204:
            case 304:
            case 205:
                responseContext.setEntity(null);
                break;
            }
        }
    }

}
