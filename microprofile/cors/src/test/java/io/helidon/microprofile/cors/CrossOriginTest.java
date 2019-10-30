/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.cors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MultivaluedMap;
import java.util.Set;

import io.helidon.common.CollectionsHelper;
import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.microprofile.cors.CrossOrigin.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.helidon.microprofile.cors.CrossOrigin.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.microprofile.cors.CrossOrigin.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.microprofile.cors.CrossOrigin.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.microprofile.cors.CrossOrigin.ACCESS_CONTROL_EXPOSE_HEADERS;
import static io.helidon.microprofile.cors.CrossOrigin.ACCESS_CONTROL_MAX_AGE;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.nullValue;

/**
 * Class CrossOriginTest.
 */
public class CrossOriginTest {

    private static Client client;
    private static Server server;

    @BeforeAll
    static void initClass() {
        client = ClientBuilder.newClient();
        server = Server.builder()
                .addApplication("/app", new CorsApplication())
                .build();
        server.start();
    }

    @AfterAll
    static void destroyClass() {
        server.stop();
        client.close();
    }

    @ApplicationScoped
    static public class CorsApplication extends Application {

        @Override
        public Set<Class<?>> getClasses() {
            return CollectionsHelper.setOf(CorsResource1.class);
        }
    }

    @CrossOrigin
    @RequestScoped
    @Path("/cors")
    static public class CorsResource1 {

        @GET
        @Path("defaults")
        public String defaults() {
            return "defaults";
        }

        @GET
        @CrossOrigin(value = {"http://foo.bar", "http://bar.foo"},
                allowHeaders = {"X-foo", "X-bar"},
                allowMethods = {HttpMethod.GET, HttpMethod.PUT},
                allowCredentials = true,
                maxAge = -1)
        @Path("cors1")
        public String cors1() {
            return "cors1";
        }
    }

    @Test
    void testCorsDefaults() {
        WebTarget target = client.target("http://localhost:" + server.port());
        MultivaluedMap<String, Object> headers = target.path("/app/cors/defaults").request().get().getHeaders();
        assertThat(headers.getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("*"));
        assertThat(headers.getFirst(ACCESS_CONTROL_ALLOW_METHODS), is("*"));
        assertThat(headers.getFirst(ACCESS_CONTROL_ALLOW_HEADERS), is("*"));
        assertThat(headers.getFirst(ACCESS_CONTROL_MAX_AGE), is("3600"));
        assertThat(headers.getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS), is(nullValue()));
        assertThat(headers.getFirst(ACCESS_CONTROL_EXPOSE_HEADERS), is(nullValue()));
    }

    @Test
    void testCors1() {
        WebTarget target = client.target("http://localhost:" + server.port());
        MultivaluedMap<String, Object> headers = target.path("/app/cors/cors1").request().get().getHeaders();
        assertThat(headers.getFirst(ACCESS_CONTROL_ALLOW_ORIGIN),
                is("http://foo.bar, http://bar.foo"));
        assertThat(headers.getFirst(ACCESS_CONTROL_ALLOW_METHODS), is("GET, PUT"));
        assertThat(headers.getFirst(ACCESS_CONTROL_ALLOW_HEADERS), is("X-foo, X-bar"));
        assertThat(headers.getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS), is("true"));
        assertThat(headers.getFirst(ACCESS_CONTROL_EXPOSE_HEADERS), is(nullValue()));
        assertThat(headers.getFirst(ACCESS_CONTROL_MAX_AGE), is(nullValue()));
    }
}
