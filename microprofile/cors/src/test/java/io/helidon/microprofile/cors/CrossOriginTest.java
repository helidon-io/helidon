/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
import javax.ws.rs.DELETE;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Set;

import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.Header.ORIGIN;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_MAX_AGE;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_REQUEST_METHOD;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Class CrossOriginTest.
 */
public class CrossOriginTest {

    private static Client client;
    private static Server server;
    private static WebTarget target;

    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    @BeforeAll
    static void initClass() {
        server = Server.builder()
                .addApplication("/app", new CorsApplication())
                .build();
        server.start();
        client = ClientBuilder.newClient();
        target = client.target("http://localhost:" + server.port());
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
            return Set.of(CorsResource1.class, CorsResource2.class, CorsResource3.class);
        }
    }

    @RequestScoped
    @Path("/cors1")
    static public class CorsResource1 {

        @OPTIONS
        @CrossOrigin
        public void options() {
        }

        @DELETE
        public Response deleteCors() {
            return Response.ok().build();
        }

        @PUT
        public Response putCors() {
            return Response.ok().build();
        }
    }

    @RequestScoped
    @Path("/cors2")
    static public class CorsResource2 {

        @OPTIONS
        @CrossOrigin(value = {"http://foo.bar", "http://bar.foo"},
                allowHeaders = {"X-foo", "X-bar"},
                allowMethods = {HttpMethod.DELETE, HttpMethod.PUT},
                allowCredentials = true,
                maxAge = -1)
        public void options() {
        }

        @DELETE
        public Response deleteCors() {
            return Response.ok().build();
        }

        @PUT
        public Response putCors() {
            return Response.ok().build();
        }
    }

    @RequestScoped
    @Path("/cors3")     // Configured in META-INF/microprofile-config.properties
    static public class CorsResource3 {

        @DELETE
        public Response deleteCors() {
            return Response.ok().build();
        }

        @PUT
        public Response putCors() {
            return Response.ok().build();
        }
    }

    @Test
    void test1PreFlightAllowedOrigin() {
        Response res = target.path("/app/cors1")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://foo.bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS), is("PUT"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS), is(nullValue()));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE), is("3600"));
    }

    @Test
    void test1PreFlightAllowedHeaders1() {
        Response res = target.path("/app/cors1")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://foo.bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS), is("PUT"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS), is("X-foo"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE), is("3600"));
    }

    @Test
    void test1PreFlightAllowedHeaders2() {
        Response res = target.path("/app/cors1")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://foo.bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS), is("PUT"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS).toString(),
                containsString("X-foo"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS).toString(),
                containsString("X-bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE), is("3600"));
    }

    @Test
    void test2PreFlightForbiddenOrigin() {
        Response res = target.path("/app/cors2")
                .request()
                .header(ORIGIN, "http://not.allowed")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.FORBIDDEN));
    }

    @Test
    void test2PreFlightAllowedOrigin() {
        Response res = target.path("/app/cors2")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://foo.bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS), is("true"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS), is("PUT"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS), is(nullValue()));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE), is(nullValue()));
    }

    @Test
    void test2PreFlightForbiddenMethod() {
        Response res = target.path("/app/cors2")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.FORBIDDEN));
    }

    @Test
    void test2PreFlightForbiddenHeader() {
        Response res = target.path("/app/cors2")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar, X-oops")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.FORBIDDEN));
    }

    @Test
    void test2PreFlightAllowedHeaders1() {
        Response res = target.path("/app/cors2")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://foo.bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS), is("true"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS), is("PUT"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS).toString(),
                containsString("X-foo"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE), is(nullValue()));
    }

    @Test
    void test2PreFlightAllowedHeaders2() {
        Response res = target.path("/app/cors2")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://foo.bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS), is("true"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS), is("PUT"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS).toString(),
                containsString("X-foo"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS).toString(),
                containsString("X-bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE), is(nullValue()));
    }

    @Test
    void test2PreFlightAllowedHeaders3() {
        Response res = target.path("/app/cors2")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar")
                .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://foo.bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS), is("true"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS), is("PUT"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS).toString(),
                containsString("X-foo"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS).toString(),
                containsString("X-bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE), is(nullValue()));
    }

    @Test
    void test1ActualAllowedOrigin() {
        Response res = target.path("/app/cors1")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .put(Entity.entity("", MediaType.TEXT_PLAIN_TYPE));
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("*"));
    }

    @Test
    void test2ActualAllowedOrigin() {
        Response res = target.path("/app/cors2")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .put(Entity.entity("", MediaType.TEXT_PLAIN_TYPE));
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://foo.bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS), is("true"));
    }

    @Test
    void test3PreFlightAllowedOrigin() {
        Response res = target.path("/app/cors3")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://foo.bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS), is("PUT"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS), is(nullValue()));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE), is("3600"));
    }

    @Test
    void test3ActualAllowedOrigin() {
        Response res = target.path("/app/cors3")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .put(Entity.entity("", MediaType.TEXT_PLAIN_TYPE));
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://foo.bar"));
    }
}
