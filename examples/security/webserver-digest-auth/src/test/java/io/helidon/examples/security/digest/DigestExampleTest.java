/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.security.digest;

import java.net.URI;
import java.util.Set;

import io.helidon.http.HeaderNames;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;

import org.junit.jupiter.api.Test;

import static io.helidon.examples.security.digest.WebClientAuthenticationService.HTTP_AUTHENTICATION_PASSWORD;
import static io.helidon.examples.security.digest.WebClientAuthenticationService.HTTP_AUTHENTICATION_USERNAME;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Abstract class with tests for this example (used by programmatic and config based tests).
 */
public abstract class DigestExampleTest {

    private final Http1Client client;
    private final Http1Client authClient;

    DigestExampleTest(Http1Client client, URI uri) {
        this.client = client;
        this.authClient = Http1Client.builder()
                .baseUri(uri)
                .addService(new WebClientAuthenticationService())
                .build();
    }

    //now for the tests
    @Test
    public void testPublic() {
        //Must be accessible without authentication
        try (Http1ClientResponse response = client.get().path("/public").request()) {
            assertThat(response.status().code(), is(200));
            String entity = response.entity().as(String.class);
            assertThat(entity, containsString("<ANONYMOUS>"));
        }
    }

    @Test
    public void testNoRoles() {
        String uri = "/noRoles";

        testNotAuthorized(uri);

        //Must be accessible with authentication - to everybody
        testProtected(uri, "jack", "changeit", Set.of("admin", "user"), Set.of());
        testProtected(uri, "jill", "changeit", Set.of("user"), Set.of("admin"));
        testProtected(uri, "john", "changeit", Set.of(), Set.of("admin", "user"));
    }

    @Test
    public void testUserRole() {
        String uri = "/user";

        testNotAuthorized(uri);

        //Jack and Jill allowed (user role)
        testProtected(uri, "jack", "changeit", Set.of("admin", "user"), Set.of());
        testProtected(uri, "jill", "changeit", Set.of("user"), Set.of("admin"));
        testProtectedDenied(uri, "john", "changeit");
    }

    @Test
    public void testAdminRole() {
        String uri = "/admin";

        testNotAuthorized(uri);

        //Only jack is allowed - admin role...
        testProtected(uri, "jack", "changeit", Set.of("admin", "user"), Set.of());
        testProtectedDenied(uri, "jill", "changeit");
        testProtectedDenied(uri, "john", "changeit");
    }

    @Test
    public void testDenyRole() {
        String uri = "/deny";

        testNotAuthorized(uri);

        // nobody has the correct role
        testProtectedDenied(uri, "jack", "changeit");
        testProtectedDenied(uri, "jill", "changeit");
        testProtectedDenied(uri, "john", "changeit");
    }

    @Test
    public void getNoAuthn() {
        String uri = "/noAuthn";
        //Must NOT be accessible without authentication
        try (Http1ClientResponse response = client.get(uri).request()) {
            // authentication is optional, so we are not challenged, only forbidden, as the role can never be there...
            assertThat(response.status().code(), is(403));

            // doesn't matter, we are never challenged
            testProtectedDenied(uri, "jack", "changeit");
            testProtectedDenied(uri, "jill", "changeit");
            testProtectedDenied(uri, "john", "changeit");
        }
    }

    private void testNotAuthorized(String uri) {
        //Must NOT be accessible without authentication
        try (Http1ClientResponse response = client.get().path(uri).request()) {
            assertThat(response.status().code(), is(401));
            String header = response.headers().first(HeaderNames.create("WWW-Authenticate")).orElse(null);
            assertThat(header, notNullValue());
            assertThat(header.toLowerCase(), containsString("digest"));
            assertThat(header, containsString("mic"));
        }
    }

    private Http1ClientResponse callProtected(String uri, String username, String password) {
        // here we call the endpoint
        return authClient
                .get(uri)
                .property(HTTP_AUTHENTICATION_USERNAME, username)
                .property(HTTP_AUTHENTICATION_PASSWORD, password)
                .request();
    }

    private void testProtectedDenied(String uri, String username, String password) {
        try (Http1ClientResponse response = callProtected(uri, username, password)) {
            assertThat(response.status().code(), is(403));
        }
    }

    private void testProtected(String uri,
                               String username,
                               String password,
                               Set<String> expectedRoles,
                               Set<String> invalidRoles) {

        try (Http1ClientResponse response = callProtected(uri, username, password)) {

            assertThat(response.status().code(), is(200));

            String entity = response.entity().as(String.class);

            // check login
            assertThat(entity, containsString("id='" + username + "'"));
            // check roles
            expectedRoles.forEach(role -> assertThat(entity, containsString(":" + role)));
            invalidRoles.forEach(role -> assertThat(entity, not(containsString(":" + role))));
        }
    }

}
