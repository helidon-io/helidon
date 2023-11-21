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

import jakarta.annotation.Priority;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JaxRsApplicationOrderFiltersTest {

    private static Server server;
    private static Client client;
    private static int port;

    @BeforeAll
    static void beforeAll() {
        server = Server.builder().addApplication(MyApplication.class).build();
        server.start();
        port = server.port();
        client = ClientBuilder.newClient();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
        client.close();
    }

    @Test
    void changeOrderTest() {
        Response response = client.target("http://localhost:" + port)
                .path("/echo").register(SecondFilter.class, 100)
                .register(FirstFilter.class, 200)
                .request().post(Entity.text("test"));
        assertThat(response.readEntity(String.class), is(FirstFilter.class.getName()));
    }

    @Test
    void priorityOrderTest() {
        Response response = client.target("http://localhost:" + port)
                .path("/echo").register(SecondFilter.class)
                .register(FirstFilter.class)
                .request().post(Entity.text("test"));
        assertThat(response.readEntity(String.class), is(SecondFilter.class.getName()));
    }

    @ApplicationPath("/")
    static class MyApplication extends Application {

        @Override
        public java.util.Set<java.lang.Class<?>> getClasses() {
            Set<Class<?>> resources = new HashSet<Class<?>>();
            resources.add(Resource.class);
            return resources;
        }
    }

    @Path("/")
    public static class Resource {
        @POST
        @Path("echo")
        public String echo(String value) {
            return value;
        }
    }

    @Provider
    @Priority(100)
    public static class FirstFilter implements ClientRequestFilter {

        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
            requestContext.setEntity(getClass().getName(), null, MediaType.WILDCARD_TYPE);
        }

    }

    @Provider
    @Priority(200)
    public static class SecondFilter implements ClientRequestFilter {

        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
            requestContext.setEntity(getClass().getName(), null, MediaType.WILDCARD_TYPE);
        }

    }
}
