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

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;

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

import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_MAX_AGE;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_REQUEST_METHOD;
import static io.helidon.http.HeaderNames.ORIGIN;
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
@AddBean(DeprecatedCrossOriginTest.CorsResource0.class)
@AddBean(DeprecatedCrossOriginTest.CorsResource1.class)
@AddBean(DeprecatedCrossOriginTest.CorsResource2.class)
@AddBean(DeprecatedCrossOriginTest.CorsResource3.class)
@AddBean(DeprecatedCrossOriginTest.CorsResource4.class)
@AddConfig(key = "cors.paths.0.path-pattern", value = "/cors3")
@AddConfig(key = "cors.paths.0.allow-origins", value = "http://foo.bar,http://bar.foo")
@AddConfig(key = "cors.paths.0.allow-methods", value = "DELETE,PUT")
@AddConfig(key = "cors.paths.1.path-pattern", value = "/cors4")
@AddConfig(key = "cors.paths.1.allow-origins", value = "http://foo.bar,http://bar.foo")
@AddConfig(key = "cors.paths.1.allow-methods", value = "GET")
@Deprecated(forRemoval = true, since = "4.4.0")
class DeprecatedCrossOriginTest extends BaseCrossOriginTest {
    @Inject
    private WebTarget target;

    @SuppressWarnings("removal")
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

    @SuppressWarnings("removal")
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

    @SuppressWarnings("removal")
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

    @RequestScoped
    @Path("/cors4")
    static public class CorsResource4 {

        @GET
        public Response get() {
            return Response.ok().build();
        }
    }

    @Test
    void test1PreFlightAllowedOrigin() {
        Response res = target.path("/cors1")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD.defaultCase(), "PUT")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.NO_CONTENT));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase()), is("*"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS.defaultCase()), is("*"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS.defaultCase()), is(nullValue()));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE.defaultCase()), is("3600"));
    }

    @Test
    void test1PreFlightAllowedHeaders1() {
        Response res = target.path("/cors1")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD.defaultCase(), "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS.defaultCase(), "X-foo")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.NO_CONTENT));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase()), is("*"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS.defaultCase()), is("*"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS.defaultCase()), is("X-foo"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE.defaultCase()), is("3600"));
    }

    @Test
    void test1PreFlightAllowedHeaders2() {
        Response res = target.path("/cors1")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD.defaultCase(), "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS.defaultCase(), "X-foo, X-bar")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.NO_CONTENT));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase()), is("*"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS.defaultCase()), is("*"));
        assertThat(res.getHeaders().get(ACCESS_CONTROL_ALLOW_HEADERS.defaultCase()),
                contains("X-foo", "X-bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE.defaultCase()), is("3600"));
    }

    @Test
    void test2PreFlightForbiddenOrigin() {
        Response res = target.path("/cors2")
                .request()
                .header(ORIGIN.defaultCase(), "http://not.allowed")
                .header(ACCESS_CONTROL_REQUEST_METHOD.defaultCase(), "PUT")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.FORBIDDEN));
        assertThat(res.readEntity(String.class), isEmptyString());
    }

    @Test
    void test2PreFlightAllowedOrigin() {
        Response res = target.path("/cors2")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD.defaultCase(), "PUT")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.NO_CONTENT));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase()), is("http://foo.bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS.defaultCase()), is("true"));
        assertThat(res.getHeaders().get(ACCESS_CONTROL_ALLOW_METHODS.defaultCase()), hasItems("PUT"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS.defaultCase()), is(nullValue()));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE.defaultCase()), is(nullValue()));
    }

    @Test
    void test2PreFlightForbiddenMethod() {
        Response res = target.path("/cors2")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD.defaultCase(), "POST")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.FORBIDDEN));
        assertThat(res.readEntity(String.class), isEmptyString());
    }

    @Test
    void test2PreFlightForbiddenHeader() {
        Response res = target.path("/cors2")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD.defaultCase(), "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS.defaultCase(), "X-foo, X-bar, X-oops")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.FORBIDDEN));
        assertThat(res.readEntity(String.class), isEmptyString());
    }

    @Test
    void test2PreFlightAllowedHeaders1() {
        Response res = target.path("/cors2")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD.defaultCase(), "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS.defaultCase(), "X-foo")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.NO_CONTENT));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase()), is("http://foo.bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS.defaultCase()), is("true"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS.defaultCase()), is("PUT"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS.defaultCase()).toString(),
                containsString("X-foo"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE.defaultCase()), is(nullValue()));
    }

    @Test
    void test2PreFlightAllowedHeaders2() {
        Response res = target.path("/cors2")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD.defaultCase(), "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS.defaultCase(), "X-foo, X-bar")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.NO_CONTENT));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase()), is("http://foo.bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS.defaultCase()), is("true"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS.defaultCase()), is("PUT"));
        assertThat(res.getHeaders().get(ACCESS_CONTROL_ALLOW_HEADERS.defaultCase()),
                   contains("X-foo", "X-bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE.defaultCase()), is(nullValue()));
    }

    @Test
    void test2PreFlightAllowedHeaders3() {
        Response res = target.path("/cors2")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD.defaultCase(), "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS.defaultCase(), "X-foo, X-bar")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.NO_CONTENT));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase()), is("http://foo.bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS.defaultCase()), is("true"));
        assertThat(res.getHeaders().get(ACCESS_CONTROL_ALLOW_METHODS.defaultCase()), hasItems("PUT"));
        assertThat(res.getHeaders().get(ACCESS_CONTROL_ALLOW_HEADERS.defaultCase()),
                   contains("X-foo", "X-bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE.defaultCase()), is(nullValue()));
    }

    @Test
    void test1ActualAllowedOrigin() {
        Response res = target.path("/cors1")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD.defaultCase(), "PUT")
                .put(Entity.entity("", MediaType.TEXT_PLAIN_TYPE));
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase()), is("*"));
    }

    @Test
    void test2ActualAllowedOrigin() {
        Response res = target.path("/cors2")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .put(Entity.entity("", MediaType.TEXT_PLAIN_TYPE));
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase()), is("http://foo.bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS.defaultCase()), is("true"));
    }

    @Test
    void test3PreFlightAllowedOrigin() {
        Response res = target.path("/cors3")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD.defaultCase(), "PUT")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase()), is("http://foo.bar"));
        assertThat(res.getHeaders().get(ACCESS_CONTROL_ALLOW_METHODS.defaultCase()), hasItems("PUT", "DELETE"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS.defaultCase()), is(nullValue()));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE.defaultCase()), is("3600"));
    }

    @Test
    void test3ActualAllowedOrigin() {
        Response res = target.path("/cors3")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD.defaultCase(), "PUT")
                .put(Entity.entity("", MediaType.TEXT_PLAIN_TYPE));
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase()), is("http://foo.bar"));
    }

    @Test
    void testMainPathInPresenceOfSubpath() {
        Response res = target.path("/cors0")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .get();
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().containsKey(ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase()), is(true));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase()), is("*"));
    }

    @Test
    void testSubPathPreflightAllowed() {
        Response res = target.path("/cors0/subpath")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD.defaultCase(), "PUT")
                .options();
        assertThat(res.getStatusInfo(), is(Response.Status.NO_CONTENT));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase()), is("http://foo.bar"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS.defaultCase()), is("PUT"));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS.defaultCase()), is(nullValue()));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_MAX_AGE.defaultCase()), is("3600"));
    }

    @Test
    void testSubPathActualAllowed() {
        Response res = target.path("/cors0/subpath")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD.defaultCase(), "PUT")
                .put(Entity.entity("", MediaType.TEXT_PLAIN_TYPE));
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat(res.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase()), is("http://foo.bar"));
    }

    @Test
    void testEntityAndHeadersWhenForbidden() {
        Response res = target.path("/cors4")
                .request()
                .header(ORIGIN.defaultCase(), "http://other.com")
                .header(ACCESS_CONTROL_REQUEST_METHOD.defaultCase(), "GET")
                .get();
        assertThat("Status of rejection response", res.getStatusInfo(), is(Response.Status.FORBIDDEN));
        assertThat("Entity of rejection response", res.hasEntity(), is(false));
        assertThat("Headers of rejection response", res.getHeaders().get(ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase()), nullValue());

    }
}
