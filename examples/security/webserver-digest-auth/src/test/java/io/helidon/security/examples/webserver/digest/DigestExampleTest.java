/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.examples.webserver.digest;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import io.helidon.webserver.WebServer;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.glassfish.jersey.client.authentication.HttpAuthenticationFeature.HTTP_AUTHENTICATION_PASSWORD;
import static org.glassfish.jersey.client.authentication.HttpAuthenticationFeature.HTTP_AUTHENTICATION_USERNAME;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Abstract class with tests for this example (used by programmatic and config based tests).
 */
public abstract class DigestExampleTest {
    private static Client client;
    private static Client authFeatureClient;

    @BeforeAll
    public static void classInit() {
        client = ClientBuilder.newClient();
        authFeatureClient = ClientBuilder.newClient()
                .register(HttpAuthenticationFeature.digest());
    }

    @AfterAll
    public static void classDestroy() {
        client.close();
        authFeatureClient.close();
    }

    static void stopServer(WebServer server) throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(1);
        long t = System.nanoTime();
        server.shutdown().thenAccept(webServer -> {
            long time = System.nanoTime() - t;
            System.out.println("Server shutdown in " + TimeUnit.NANOSECONDS.toMillis(time) + " ms");
            cdl.countDown();
        });

        if (!cdl.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Failed to shutdown server within 5 seconds");
        }
    }

    abstract String getServerBase();

    //now for the tests
    @Test
    public void testPublic() {
        //Must be accessible without authentication
        try (Response response = client.target(getServerBase() + "/public").request().get()) {
            assertThat(response.getStatus(), is(200));
            String entity = response.readEntity(String.class);
            assertThat(entity, containsString("<ANONYMOUS>"));
        }
    }

    @Test
    public void testNoRoles() {
        String url = getServerBase() + "/noRoles";

        testNotAuthorized(client, url);

        //Must be accessible with authentication - to everybody
        testProtected(url, "jack", "password", Set.of("admin", "user"), Set.of());
        testProtected(url, "jill", "password", Set.of("user"), Set.of("admin"));
        testProtected(url, "john", "password", Set.of(), Set.of("admin", "user"));
    }

    @Test
    public void testUserRole() {
        String url = getServerBase() + "/user";

        testNotAuthorized(client, url);

        //Jack and Jill allowed (user role)
        testProtected(url, "jack", "password", Set.of("admin", "user"), Set.of());
        testProtected(url, "jill", "password", Set.of("user"), Set.of("admin"));
        testProtectedDenied(url, "john", "password");
    }

    @Test
    public void testAdminRole() {
        String url = getServerBase() + "/admin";

        testNotAuthorized(client, url);

        //Only jack is allowed - admin role...
        testProtected(url, "jack", "password", Set.of("admin", "user"), Set.of());
        testProtectedDenied(url, "jill", "password");
        testProtectedDenied(url, "john", "password");
    }

    @Test
    public void testDenyRole() {
        String url = getServerBase() + "/deny";

        testNotAuthorized(client, url);

        // nobody has the correct role
        testProtectedDenied(url, "jack", "password");
        testProtectedDenied(url, "jill", "password");
        testProtectedDenied(url, "john", "password");
    }

    @Test
    public void getNoAuthn() {
        String url = getServerBase() + "/noAuthn";
        //Must NOT be accessible without authentication
        try (Response response = client.target(url).request().get()) {
            // authentication is optional, so we are not challenged, only forbidden, as the role can never be there...
            assertThat(response.getStatus(), is(403));

            // doesn't matter, we are never challenged
            testProtectedDenied(url, "jack", "password");
            testProtectedDenied(url, "jill", "password");
            testProtectedDenied(url, "john", "password");
        }
    }

    private void testNotAuthorized(Client client, String uri) {
        //Must NOT be accessible without authentication
        try (Response response = client.target(uri).request().get()) {
            assertThat(response.getStatus(), is(401));
            String header = response.getHeaderString("WWW-Authenticate");
            assertThat(header, notNullValue());
            assertThat(header.toLowerCase(), containsString("digest"));
            assertThat(header, containsString("mic"));
        }
    }

    private Response callProtected(String uri, String username, String password) {
        // here we call the endpoint
        return authFeatureClient.target(uri)
                .request()
                .property(HTTP_AUTHENTICATION_USERNAME, username)
                .property(HTTP_AUTHENTICATION_PASSWORD, password)
                .get();
    }

    private void testProtectedDenied(String uri,
                                     String username,
                                     String password) {

        try (Response response = callProtected(uri, username, password)) {
            assertThat(response.getStatus(), is(403));
        }
    }

    private void testProtected(String uri,
                               String username,
                               String password,
                               Set<String> expectedRoles,
                               Set<String> invalidRoles) {

        try (Response response = callProtected(uri, username, password)) {
            String entity = response.readEntity(String.class);

            assertThat(response.getStatus(), is(200));

            // check login
            assertThat(entity, containsString("id='" + username + "'"));
            // check roles
            expectedRoles.forEach(role -> assertThat(entity, containsString(":" + role)));
            invalidRoles.forEach(role -> assertThat(entity, not(containsString(":" + role))));
        }
    }
}
