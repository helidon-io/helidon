/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Encoded;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class JaxRsApplicationPathTest {

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
        if (server != null) {
            server.stop();
            client.close();
        }
    }

    @Test
    void okTest() {
        String getResponse = client.target("http://localhost:" + port)
                .path("/ApplicationPath!/Resource").request().get(String.class);
        assertThat(getResponse, is("ok"));
    }

    @Test
    void nokTest() {
        int status = client.target("http://localhost:" + port)
                .path("/Resource").request().get().getStatus();
        assertThat(status, is(404));
    }

    @Test
    void emptyParamTest() {
        String getResponse = client.target("http://localhost:" + port)
                .path("/ApplicationPath!/Resource/pathparam1/%20/%2010").request().get(String.class);
        assertThat(getResponse, is("a= b= 10"));
    }

    @Test
    public void testEncoded() {
        String getResponse = client.target("http://localhost:" + port)
                .path("/ApplicationPath!/Resource/encoded").queryParam("query", "%dummy23+a")
                .request()
                .get(String.class);
        assertThat(getResponse, is("true:%25dummy23%2Ba"));
    }

    @Test
    public void testDecoded() {
        String getResponse = client.target("http://localhost:" + port)
                .path("/ApplicationPath!/Resource/decoded").queryParam("query", "%dummy23+a")
                .request().get(String.class);
        assertThat(getResponse, is("true:%dummy23+a"));
    }

    @Test
    void encodedEntityTest() {
        String getResponse = client.target("http://localhost:" + port)
                .path("/ApplicationPath!/Resource/ParamEntityWithFromString/test%21/test!/test!/test%21")
                .request().get(String.class);
        assertThat(getResponse, is("test%21test!test!test!"));
    }

    @ApplicationPath("/ApplicationPath%21")
    static class MyApplication extends Application {

        @Override
        public java.util.Set<java.lang.Class<?>> getClasses() {
            Set<Class<?>> resources = new HashSet<Class<?>>();
            resources.add(TestResource.class);
            return resources;
        }
    }

    @Path("/Resource")
    public static class TestResource {

        @GET
        public String param() {
          return "ok";
        }

        @GET
        @Path("/pathparam1/{a}/{b}")
        public String pathparamTest1(@Context UriInfo info) {
          StringBuilder buf = new StringBuilder();
          for (String param : info.getPathParameters(true).keySet()) {
            buf.append(param + "=" + info.getPathParameters(true).getFirst(param));
          }
          return buf.toString();
        }

        @GET
        @Path("/ParamEntityWithFromString/{encoded}/{notEncoded}/{encoded2}/{notEncoded2}")
        @Produces(MediaType.TEXT_PLAIN)
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        public String stringParamHandlingFromString(
            @BeanParam PathBeanParamEntity bean) {
          return bean.encoded + bean.notEncoded + bean.encoded2 + bean.notEncoded2;
        }

        @GET
        @Path("encoded")
        public String getEncoded(@Encoded @QueryParam("query") String queryParam) {
            return queryParam.equals("%25dummy23%2Ba") + ":" + queryParam;
        }

        @GET
        @Path("decoded")
        public String getDecoded(@QueryParam("query") String queryParam) {
            return queryParam.equals("%dummy23+a") + ":" + queryParam;
        }
    }

    static class PathBeanParamEntity {
        @Encoded
        @PathParam("encoded")
        public String encoded;
        @PathParam("notEncoded")
        public String notEncoded;
        @Encoded
        @PathParam("encoded2")
        public String encoded2;
        @PathParam("notEncoded2")
        public String notEncoded2;
    }
}
