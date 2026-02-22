/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.microprofile.testing.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.webserver.cors.Cors;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddBean(ArchetypeTest.TestResource.class)
public class ArchetypeTest {
    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    @Inject
    private WebTarget target;

    @Test
    void testAnonymousGreetWithCors() {
        Response r = target.path("/test")
                .request()
                .header(HeaderNames.ORIGIN.defaultCase(), "http://foo.com")
                .header(HeaderNames.HOST.defaultCase(), "here.com")
                .get();

        /*
        We have a path with CORS configured, and it only allows PUT and DELETE
         */

        assertThat("HTTP response", r.getStatus(), is(403));
    }

    @Test
    void testAnonymousGreetWithCorsWrongOrigin() {
        Response r = target.path("/test")
                .request()
                .header(HeaderNames.ORIGIN.defaultCase(), "http://foos.com")
                .header(HeaderNames.HOST.defaultCase(), "here.com")
                .get();

        /*
        We have a path with CORS configured, and it only allows PUT and DELETE
         */

        assertThat("HTTP response", r.getStatus(), is(403));
    }

    @Test
    void testCustomGreetingWithCors() {
        Response r = target.path("/test")
                .request()
                .header(HeaderNames.ORIGIN.defaultCase(), "http://foo.com")
                .header(HeaderNames.HOST.defaultCase(), "here.com")
                .header("Access-Control-Request-Method", "PUT")
                .options();

        // status is 204, as that is the status provided by Jersey on void methods
        assertThat("pre-flight status", r.getStatus(), is(204));
        MultivaluedMap<String, Object> responseHeaders = r.getHeaders();
        assertThat("Header " + HeaderNames.ACCESS_CONTROL_ALLOW_METHODS_NAME,
                   r.getHeaders().get(HeaderNames.ACCESS_CONTROL_ALLOW_METHODS_NAME),
                   hasItems("PUT", "DELETE"));
        assertThat("Header " + HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN_NAME,
                   r.getHeaders().getFirst(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN_NAME),
                   is("http://foo.com"));

        Invocation.Builder builder = target.path("/test")
                .request()
                .headers(responseHeaders)
                .header(HeaderNames.ORIGIN.defaultCase(), "http://foo.com")
                .header(HeaderNames.HOST.defaultCase(), "here.com");

        r = putResponse("Cheers", builder);
        assertThat("HTTP response3", r.getStatus(), is(200));
        assertThat("Header " + HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN_NAME,
                   r.getHeaders().getFirst(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN_NAME),
                   is("http://foo.com"));
        assertThat(fromPayload(r), containsString("Cheers World!"));
    }

    @Test
    void testGreetingChangeWithCorsAndOtherOrigin() {
        Invocation.Builder builder = target.path("/test")
                .request()
                .header(HeaderNames.ORIGIN.defaultCase(), "http://other.com")
                .header(HeaderNames.HOST.defaultCase(), "here.com");

        Response r = putResponse("Ahoy", builder);
        boolean isOverriding = Config.global().get("cors").exists();
        assertThat("HTTP response3", r.getStatus(), is(isOverriding ? 200 : 403));
    }

    private static String fromPayload(Response response) {
        return response.readEntity(String.class);
    }

    private static Response putResponse(String message, Invocation.Builder builder) {
        return builder.put(Entity.entity(message, MediaType.TEXT_PLAIN_TYPE));
    }

    @Path("/test")
    public static class TestResource {

        /**
         * Return a worldly greeting message.
         *
         * @return a message
         */
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String getDefaultMessage() {
            return "Hello World!";
        }

        @PUT
        public Response customMessage(String greeting) {
            String msg = String.format("%s %s!", greeting, "World");
            return Response.ok(msg).build();
        }

        /**
         * CORS set-up.
         */
        @OPTIONS
        @Cors.AllowOrigins({"http://foo.com", "http://there.com"})
        @Cors.AllowMethods({HttpMethod.PUT, HttpMethod.DELETE})
        public void customMessageOptions() {
        }

    }
}
