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

import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Providers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class JaxRsApplicationVisibilityTest {

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

    @Disabled("Failing, it is invoking the empty Resource constructor")
    @Test
    void okTest() {
        Response response = client.target("http://localhost:" + port).path("/Application/Resource/mostAttributes")
                .request().get();
        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
    }

    @ApplicationPath("/Application")
    static class MyApplication extends Application {

        @Override
        public java.util.Set<java.lang.Class<?>> getClasses() {
            Set<Class<?>> resources = new HashSet<Class<?>>();
            resources.add(Resource.class);
            return resources;
        }
    }

    @Path("/Resource")
    public static class Resource {

        private HttpHeaders headers;
        private UriInfo info;
        private Application application;
        private Request request;
        private Providers provider;

        public Resource() {
        }

        public Resource(@Context HttpHeaders headers) {
          this.headers = headers;
        }

        public Resource(@Context HttpHeaders headers, @Context UriInfo info) {
          this.headers = headers;
          this.info = info;
        }

        public Resource(@Context HttpHeaders headers, @Context UriInfo info,
            @Context Application application) {
          this.application = application;
          this.headers = headers;
          this.info = info;
        }

        public Resource(@Context HttpHeaders headers, @Context UriInfo info,
            @Context Application application, @Context Request request) {
          this.application = application;
          this.headers = headers;
          this.info = info;
          this.request = request;
        }

        protected Resource(@Context HttpHeaders headers, @Context UriInfo info,
            @Context Application application, @Context Request request,
            @Context Providers provider) {
          this.application = application;
          this.headers = headers;
          this.info = info;
          this.request = request;
          this.provider = provider;
        }

        @GET
        @Path("mostAttributes")
        public Response isUsedConstructorWithMostAttributes() {
            boolean ok = application != null;
            ok &= headers != null;
            ok &= info != null;
            ok &= request != null;
            ok &= provider == null;
            Status status = ok ? Status.OK : Status.NOT_ACCEPTABLE;
            return Response.status(status).build();
        }
    }
}
