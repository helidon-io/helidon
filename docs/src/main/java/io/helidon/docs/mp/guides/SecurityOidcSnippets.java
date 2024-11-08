/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.mp.guides;

import java.util.Base64;

import io.helidon.security.annotations.Authenticated;

import jakarta.annotation.security.RolesAllowed;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("ALL")
class SecurityOidcSnippets {

    // stub
    static Message createResponse(String str) {
        return null;
    }

    // stub
    record Message() {
        String getMessage() {
            return "";
        }
    }

    // tag::snippet_1[]
    @Authenticated
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Message getDefaultMessage() {
        return createResponse("World");
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @Test
    void testHelloWorld() {
    }
    // end::snippet_2[]

    void snippet_5(WebTarget target) {
        // tag::snippet_3[]
        try (Response r = target
                .path("greet")
                .request()
                .get()) {
            assertThat(r.getStatus(), is(401));
        }
        // end::snippet_3[]
    }

    void snippet_6(WebTarget target) {
        // tag::snippet_4[]
        String encoding = Base64.getEncoder().encodeToString("jack:changeit".getBytes());
        Message jsonMessage = target
                .path("greet")
                .request()
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encoding)
                .get(GreetingMessage.class);

        assertThat(jsonMessage.getMessage(), is("Hello World!"));
        // end::snippet_4[]
    }

    class Snippet8 {

        // tag::snippet_5[]
        @RolesAllowed("admin")
        class GreetResource {
        }
        // end::snippet_5[]
    }

    void snippet_9(WebTarget target) {
        // tag::snippet_6[]
        String encoding = Base64.getEncoder().encodeToString("john:changeit".getBytes());

        try (Response r = target
                .path("greet")
                .request()
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encoding)
                .get()) {
            assertThat(r.getStatus(), is(403));
        }
        // end::snippet_6[]
    }
}
