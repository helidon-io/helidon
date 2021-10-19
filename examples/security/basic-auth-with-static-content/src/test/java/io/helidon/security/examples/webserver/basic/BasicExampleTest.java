/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.examples.webserver.basic;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Http;
import io.helidon.security.Security;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Abstract class with tests for this example (used by programmatic and config based tests).
 */
public abstract class BasicExampleTest {
    private static WebClient client;

    @BeforeAll
    public static void classInit() {
        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.builder()
                                     .build())
                .build();

        WebClientSecurity securityService = WebClientSecurity.create(security);

        client = WebClient.builder()
                .addService(securityService)
                .build();
    }

    static void stopServer(WebServer server) {
        long t = System.nanoTime();
        server.shutdown().await(5, TimeUnit.SECONDS);

        long time = System.nanoTime() - t;
        System.out.println("Server shutdown in " + TimeUnit.NANOSECONDS.toMillis(time) + " ms");
    }

    abstract String getServerBase();

    //now for the tests
    @Test
    public void testPublic() {
        //Must be accessible without authentication
        WebClientResponse response = client.get()
                .uri(getServerBase() + "/public")
                .request()
                .await(10, TimeUnit.SECONDS);

        assertThat(response.status(), is(Http.Status.OK_200));
        String entity = response.content().as(String.class).await(10, TimeUnit.SECONDS);
        assertThat(entity, containsString("<ANONYMOUS>"));
    }

    @Test
    public void testNoRoles() {
        String url = getServerBase() + "/noRoles";

        testNotAuthorized(url);

        //Must be accessible with authentication - to everybody
        testProtected(url, "jack", "password", Set.of("admin", "user"), Set.of());
        testProtected(url, "jill", "password", Set.of("user"), Set.of("admin"));
        testProtected(url, "john", "password", Set.of(), Set.of("admin", "user"));
    }

    @Test
    public void testUserRole() {
        String url = getServerBase() + "/user";

        testNotAuthorized(url);

        //Jack and Jill allowed (user role)
        testProtected(url, "jack", "password", Set.of("admin", "user"), Set.of());
        testProtected(url, "jill", "password", Set.of("user"), Set.of("admin"));
        testProtectedDenied(url, "john", "password");
    }

    @Test
    public void testAdminRole() {
        String url = getServerBase() + "/admin";

        testNotAuthorized(url);

        //Only jack is allowed - admin role...
        testProtected(url, "jack", "password", Set.of("admin", "user"), Set.of());
        testProtectedDenied(url, "jill", "password");
        testProtectedDenied(url, "john", "password");
    }

    @Test
    public void testDenyRole() {
        String url = getServerBase() + "/deny";

        testNotAuthorized(url);

        // nobody has the correct role
        testProtectedDenied(url, "jack", "password");
        testProtectedDenied(url, "jill", "password");
        testProtectedDenied(url, "john", "password");
    }

    @Test
    public void getNoAuthn() {
        String url = getServerBase() + "/noAuthn";
        //Must NOT be accessible without authentication
        WebClientResponse response = client.get().uri(url).request().await(5, TimeUnit.SECONDS);

        // authentication is optional, so we are not challenged, only forbidden, as the role can never be there...
        assertThat(response.status(), is(Http.Status.FORBIDDEN_403));
    }

    private void testNotAuthorized(String uri) {
        //Must NOT be accessible without authentication
        WebClientResponse response = client.get().uri(uri).request().await(5, TimeUnit.SECONDS);

        assertThat(response.status(), is(Http.Status.UNAUTHORIZED_401));
        String header = response.headers().first("WWW-Authenticate").get();
        assertThat(header.toLowerCase(), containsString("basic"));
        assertThat(header, containsString("helidon"));
    }

    private WebClientResponse callProtected(String uri, String username, String password) {
        // here we call the endpoint
        return client.get()
                .uri(uri)
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER, username)
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD, password)
                .request()
                .await(5, TimeUnit.SECONDS);
    }

    private void testProtectedDenied(String uri,
                                     String username,
                                     String password) {

        WebClientResponse response = callProtected(uri, username, password);
        assertThat(response.status(), is(Http.Status.FORBIDDEN_403));
    }

    private void testProtected(String uri,
                               String username,
                               String password,
                               Set<String> expectedRoles,
                               Set<String> invalidRoles) {

        WebClientResponse response = callProtected(uri, username, password);

        String entity = response.content().as(String.class).await(5, TimeUnit.SECONDS);

        assertThat(response.status(), is(Http.Status.OK_200));

        // check login
        assertThat(entity, containsString("id='" + username + "'"));
        // check roles
        expectedRoles.forEach(role -> assertThat(entity, containsString(":" + role)));
        invalidRoles.forEach(role -> assertThat(entity, not(containsString(":" + role))));
    }
}
