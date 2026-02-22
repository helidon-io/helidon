/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

import io.helidon.microprofile.testing.AddBean;
import io.helidon.microprofile.testing.AddConfig;
import io.helidon.webserver.cors.Cors;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS_NAME;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_HEADERS_NAME;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_METHODS_NAME;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN_NAME;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_MAX_AGE_NAME;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_REQUEST_HEADERS_NAME;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_REQUEST_METHOD_NAME;
import static io.helidon.http.HeaderNames.ORIGIN_NAME;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.isEmptyString;

/**
 * Class CrossOriginTest.
 */
@AddBean(CrossOriginTest.CorsResource0.class)
@AddBean(CrossOriginTest.CorsResource1.class)
@AddBean(CrossOriginTest.CorsResource2.class)
@AddBean(CrossOriginTest.CorsResource3.class)
@AddBean(CrossOriginTest.CorsResource4.class)
@AddConfig(key = "cors.paths.0.path-pattern", value = "/cors3")
@AddConfig(key = "cors.paths.0.allow-origins", value = "http://foo.bar,http://bar.foo")
@AddConfig(key = "cors.paths.0.allow-methods", value = "DELETE,PUT")
@AddConfig(key = "cors.paths.1.path-pattern", value = "/cors4")
@AddConfig(key = "cors.paths.1.allow-origins", value = "http://foo.bar,http://bar.foo")
@AddConfig(key = "cors.paths.1.allow-methods", value = "GET")
class CrossOriginTest extends BaseCrossOriginTest {
    @Inject
    private WebTarget target;

    @Test
    void test1PreFlightAllowedOrigin() {
        try (Response res = target.path("/cors1")
                .request()
                .header(ORIGIN_NAME, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD_NAME, "PUT")
                .options()) {
            assertThat(res.getStatusInfo(), is(Response.Status.NO_CONTENT));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN_NAME), is("*"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS_NAME), is("*"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS_NAME), is(nullValue()));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE_NAME), is("3600"));
        }
    }

    @Test
    void test1PreFlightAllowedHeaders1() {
        try (Response res = target.path("/cors1")
                .request()
                .header(ORIGIN_NAME, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD_NAME, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS_NAME, "X-foo")
                .options()) {
            assertThat(res.getStatusInfo(), is(Response.Status.NO_CONTENT));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN_NAME), is("*"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS_NAME), is("*"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS_NAME), is("X-foo"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE_NAME), is("3600"));
        }
    }

    @Test
    void test1PreFlightAllowedHeaders2() {
        try (Response res = target.path("/cors1")
                .request()
                .header(ORIGIN_NAME, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD_NAME, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS_NAME, "X-foo, X-bar")
                .options()) {
            assertThat(res.getStatusInfo(), is(Response.Status.NO_CONTENT));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN_NAME), is("*"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS_NAME), is("*"));
            assertThat(res.getHeaders().get(ACCESS_CONTROL_ALLOW_HEADERS_NAME),
                       contains("X-foo", "X-bar"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE_NAME), is("3600"));
        }
    }

    @Test
    void test2PreFlightForbiddenOrigin() {
        try (Response res = target.path("/cors2")
                .request()
                .header(ORIGIN_NAME, "http://not.allowed")
                .header(ACCESS_CONTROL_REQUEST_METHOD_NAME, "PUT")
                .options()) {
            assertThat(res.getStatusInfo(), is(Response.Status.FORBIDDEN));
            assertThat(res.readEntity(String.class), isEmptyString());
        }
    }

    @Test
    void test2PreFlightAllowedOrigin() {
        try (Response res = target.path("/cors2")
                .request()
                .header(ORIGIN_NAME, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD_NAME, "PUT")
                .options()) {
            assertThat(res.getStatusInfo(), is(Response.Status.NO_CONTENT));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN_NAME), is("http://foo.bar"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS_NAME), is("true"));
            assertThat(res.getHeaders().get(ACCESS_CONTROL_ALLOW_METHODS_NAME), hasItems("PUT"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS_NAME), is(nullValue()));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE_NAME), is(nullValue()));
        }
    }

    @Test
    void test2PreFlightForbiddenMethod() {
        try (Response res = target.path("/cors2")
                .request()
                .header(ORIGIN_NAME, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD_NAME, "POST")
                .options()) {
            assertThat(res.getStatusInfo(), is(Response.Status.FORBIDDEN));
            assertThat(res.readEntity(String.class), isEmptyString());
        }
    }

    @Test
    void test2PreFlightForbiddenHeader() {
        try (Response res = target.path("/cors2")
                .request()
                .header(ORIGIN_NAME, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD_NAME, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS_NAME, "X-foo, X-bar, X-oops")
                .options()) {
            assertThat(res.getStatusInfo(), is(Response.Status.FORBIDDEN));
            assertThat(res.readEntity(String.class), isEmptyString());
        }
    }

    @Test
    void test2PreFlightAllowedHeaders1() {
        try (Response res = target.path("/cors2")
                .request()
                .header(ORIGIN_NAME, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD_NAME, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS_NAME, "X-foo")
                .options()) {
            assertThat(res.getStatusInfo(), is(Response.Status.NO_CONTENT));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN_NAME), is("http://foo.bar"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS_NAME), is("true"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS_NAME), is("PUT"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS_NAME).toString(),
                       containsString("X-foo"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE_NAME), is(nullValue()));
        }
    }

    @Test
    void test2PreFlightAllowedHeaders2() {
        try (Response res = target.path("/cors2")
                .request()
                .header(ORIGIN_NAME, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD_NAME, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS_NAME, "X-foo, X-bar")
                .options()) {
            assertThat(res.getStatusInfo(), is(Response.Status.NO_CONTENT));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN_NAME), is("http://foo.bar"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS_NAME), is("true"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS_NAME), is("PUT"));
            assertThat(res.getHeaders().get(ACCESS_CONTROL_ALLOW_HEADERS_NAME),
                       contains("X-foo", "X-bar"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE_NAME), is(nullValue()));
        }
    }

    @Test
    void test2PreFlightAllowedHeaders3() {
        try (Response res = target.path("/cors2")
                .request()
                .header(ORIGIN_NAME, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD_NAME, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS_NAME, "X-foo, X-bar")
                .options()) {
            assertThat(res.getStatusInfo(), is(Response.Status.NO_CONTENT));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN_NAME), is("http://foo.bar"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS_NAME), is("true"));
            assertThat(res.getHeaders().get(ACCESS_CONTROL_ALLOW_METHODS_NAME), hasItems("PUT"));
            assertThat(res.getHeaders().get(ACCESS_CONTROL_ALLOW_HEADERS_NAME),
                       contains("X-foo", "X-bar"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE_NAME), is(nullValue()));
        }
    }

    @Test
    void test1ActualAllowedOrigin() {
        try (Response res = target.path("/cors1")
                .request()
                .header(ORIGIN_NAME, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD_NAME, "PUT")
                .put(Entity.entity("", MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(res.getStatusInfo(), is(Response.Status.OK));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN_NAME), is("*"));
        }
    }

    @Test
    void test2ActualAllowedOrigin() {
        try (Response res = target.path("/cors2")
                .request()
                .header(ORIGIN_NAME, "http://foo.bar")
                .put(Entity.entity("", MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(res.getStatusInfo(), is(Response.Status.OK));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN_NAME), is("http://foo.bar"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS_NAME), is("true"));
        }
    }

    @Test
    void test3PreFlightAllowedOrigin() {
        try (Response res = target.path("/cors3")
                .request()
                .header(ORIGIN_NAME, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD_NAME, "PUT")
                .options()) {
            assertThat(res.getStatusInfo(), is(Response.Status.OK));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN_NAME), is("http://foo.bar"));
            assertThat(res.getHeaders().get(ACCESS_CONTROL_ALLOW_METHODS_NAME), hasItems("PUT", "DELETE"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS_NAME), is(nullValue()));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE_NAME), is("3600"));
        }
    }

    @Test
    void test3ActualAllowedOrigin() {
        try (Response res = target.path("/cors3")
                .request()
                .header(ORIGIN_NAME, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD_NAME, "PUT")
                .put(Entity.entity("", MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(res.getStatusInfo(), is(Response.Status.OK));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN_NAME), is("http://foo.bar"));
        }
    }

    @Test
    void testMainPathInPresenceOfSubpath() {
        try (Response res = target.path("/cors0")
                .request()
                .header(ORIGIN_NAME, "http://foo.bar")
                .get()) {
            assertThat(res.getStatusInfo(), is(Response.Status.OK));
            assertThat(res.getHeaders().containsKey(ACCESS_CONTROL_ALLOW_ORIGIN_NAME), is(true));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN_NAME), is("*"));
        }
    }

    @Test
    void testSubPathPreflightAllowed() {
        try (Response res = target.path("/cors0/subpath")
                .request()
                .header(ORIGIN_NAME, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD_NAME, "PUT")
                .options()) {
            assertThat(res.getStatusInfo(), is(Response.Status.NO_CONTENT));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN_NAME), is("http://foo.bar"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS_NAME), is("PUT"));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS_NAME), is(nullValue()));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE_NAME), is("3600"));
        }
    }

    @Test
    void testSubPathActualAllowed() {
        try (Response res = target.path("/cors0/subpath")
                .request()
                .header(ORIGIN_NAME, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD_NAME, "PUT")
                .put(Entity.entity("", MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(res.getStatusInfo(), is(Response.Status.OK));
            assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN_NAME), is("http://foo.bar"));
        }
    }

    @Test
    void testEntityAndHeadersWhenForbidden() {
        try (Response res = target.path("/cors4")
                .request()
                .header(ORIGIN_NAME, "http://other.com")
                .header(ACCESS_CONTROL_REQUEST_METHOD_NAME, "GET")
                .get()) {
            assertThat("Status of rejection response", res.getStatusInfo(), is(Response.Status.FORBIDDEN));
            assertThat("Entity of rejection response", res.hasEntity(), is(false));
            assertThat("Headers of rejection response",
                       res.getHeaders().get(ACCESS_CONTROL_ALLOW_ORIGIN_NAME),
                       nullValue());
        }
    }

    @Path("/cors1")
    static public class CorsResource1 {

        @OPTIONS
        @Cors.Defaults
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
        @Cors.AllowOrigins({"http://foo.bar", "http://bar.foo"})
        @Cors.AllowHeaders({"X-foo", "X-bar"})
        @Cors.AllowMethods({HttpMethod.DELETE, HttpMethod.PUT})
        @Cors.AllowCredentials
        @Cors.MaxAgeSeconds(0)
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
        @Cors.AllowOrigins({"http://foo.bar", "http://bar.foo"})
        @Cors.AllowMethods(HttpMethod.PUT)
        @Path("/subpath")
        public void optionsForSubpath() {
        }

        @OPTIONS
        @Cors.Defaults
        public void optionsForMainPath() {
        }
    }

    @RequestScoped
    @Path("/cors4")
    static public class CorsResource4 {

        @GET
        public Response get() {
            return Response.ok().build();
        }
    }
}
