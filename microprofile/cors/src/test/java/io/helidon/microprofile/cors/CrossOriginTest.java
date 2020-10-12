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

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;

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
@HelidonTest
@AddBean(CrossOriginTest.CorsResource0.class)
@AddBean(CrossOriginTest.CorsResource1.class)
@AddBean(CrossOriginTest.CorsResource2.class)
@AddBean(CrossOriginTest.CorsResource3.class)
@AddConfig(key = "cors.paths.0.path-pattern", value = "/cors3")
@AddConfig(key = "cors.paths.0.allow-origins", value = "http://foo.bar, http://bar.foo")
@AddConfig(key = "cors.paths.0.allow-methods", value = "DELETE, PUT")
class CrossOriginTest {
    @Inject
    private WebTarget target;

    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

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
    @Path("/cors3")
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

    @RequestScoped
    @Path("/cors0")
    static public class CorsResource0 {

        @PUT
        @Path("/subpath")
        public Response put() {
            return Response.ok().build();
        }

        @GET
        public Response get() {
            return Response.ok().build();
        }

        @OPTIONS
        @CrossOrigin(value = {"http://foo.bar", "http://bar.foo"},
                allowMethods = {"PUT"})
        @Path("/subpath")
        public void optionsForSubpath() {
        }

        @OPTIONS
        @CrossOrigin()
        public void optionsForMainPath() {
        }
    }

    @Test
    void test1PreFlightAllowedOrigin() {
        Response res = target.path("/cors1")
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
        Response res = target.path("/cors1")
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
        Response res = target.path("/cors1")
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
        Response res = target.path("/cors2")
                .request()
                .header(ORIGIN, "http://not.allowed")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.FORBIDDEN));
    }

    @Test
    void test2PreFlightAllowedOrigin() {
        Response res = target.path("/cors2")
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
        Response res = target.path("/cors2")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.FORBIDDEN));
    }

    @Test
    void test2PreFlightForbiddenHeader() {
        Response res = target.path("/cors2")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar, X-oops")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.FORBIDDEN));
    }

    @Test
    void test2PreFlightAllowedHeaders1() {
        Response res = target.path("/cors2")
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
        Response res = target.path("/cors2")
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
        Response res = target.path("/cors2")
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
        Response res = target.path("/cors1")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .put(Entity.entity("", MediaType.TEXT_PLAIN_TYPE));
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("*"));
    }

    @Test
    void test2ActualAllowedOrigin() {
        Response res = target.path("/cors2")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .put(Entity.entity("", MediaType.TEXT_PLAIN_TYPE));
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://foo.bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS), is("true"));
    }

    @Test
    void test3PreFlightAllowedOrigin() {
        Response res = target.path("/cors3")
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
        Response res = target.path("/cors3")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .put(Entity.entity("", MediaType.TEXT_PLAIN_TYPE));
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://foo.bar"));
    }

    @Test
    void testErrorResponse() {
        Response res = target.path("/notfound")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .put(Entity.entity("", MediaType.TEXT_PLAIN_TYPE));
        assertThat(res.getStatusInfo(), is(Response.Status.NOT_FOUND));
        assertThat(res.getHeaders().containsKey(ACCESS_CONTROL_ALLOW_ORIGIN), is(false));
    }

    @Test
    void testMainPathInPresenceOfSubpath() {
        Response res = target.path("/cors0")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .get();
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().containsKey(ACCESS_CONTROL_ALLOW_ORIGIN), is(true));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("*"));
    }

    @Test
    void testSubPathPreflightAllowed() {
        Response res = target.path("/cors0/subpath")
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
    void testSubPathActualAllowed() {
        Response res = target.path("/cors0/subpath")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .put(Entity.entity("", MediaType.TEXT_PLAIN_TYPE));
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://foo.bar"));
    }
}
