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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.MatrixParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JaxRsApplicationMatrixEntityTest {

    private static final String FOO = "foo";
    private static final String BAR = "bar";
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
    void defaultValueField() {
        String getResponse = client.target("http://localhost:" + port)
                .path("/field").request().get(String.class);
        assertThat(getResponse, is(equalTo(FOO)));
    }

    @Test
    void customValueField() {
        String getResponse = client.target("http://localhost:" + port)
                .path("/field;matrix=" + BAR).request().get(String.class);
        assertThat(getResponse, is(equalTo(BAR)));
    }

    @Test
    void customAndDefaultValueField() {
        String getResponse = client.target("http://localhost:" + port)
                .path("/field").request().get(String.class);
        assertThat(getResponse, is(equalTo(FOO)));
        getResponse = client.target("http://localhost:" + port)
                .path("/field;matrix=" + BAR).request().get(String.class);
        assertThat(getResponse, is(equalTo(BAR)));
    }

    @Test
    void defaultValueParam() {
        String getResponse = client.target("http://localhost:" + port)
                .path("/param").request().get(String.class);
        assertThat(getResponse, is(equalTo(FOO)));
    }

    @Test
    void customValueParam() {
        String getResponse = client.target("http://localhost:" + port)
                .path("/param;matrix=" + BAR).request().get(String.class);
        assertThat(getResponse, is(equalTo(BAR)));
    }

    @ApplicationPath("/")
    static class MyApplication extends Application {

        @Override
        public java.util.Set<java.lang.Class<?>> getClasses() {
            Set<Class<?>> resources = new HashSet<Class<?>>();
            resources.add(TestResource.class);
            return resources;
        }
    }

    @Path("/")
    public static class TestResource {

        @BeanParam
        MatrixBeanParamEntity entity;

        @GET
        @Path("field")
        @Produces(MediaType.TEXT_PLAIN)
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        public String field() {
          return entity.field.value;
        }

        @GET
        @Path("param")
        @Produces(MediaType.TEXT_PLAIN)
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        public String param(@BeanParam MatrixBeanParamEntity entity) {
          return entity.field.value;
        }

    }

    public static class MatrixBeanParamEntity {

        @DefaultValue(FOO)
        @MatrixParam("matrix")
        public FieldStr field;

    }

    public static class FieldStr {

        private final String value;

        public FieldStr(String value) {
          this.value = value;
        }

    }
}
