/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.examples.signatures;

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
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Actual unit tests are shared by config and builder example.
 */
public abstract class SignatureExampleTest {
    private static Client client;
    private static Client authFeatureClient;

    @BeforeAll
    public static void classInit() {
        client = ClientBuilder.newClient();
        authFeatureClient = ClientBuilder.newClient()
                .register(HttpAuthenticationFeature.basicBuilder().nonPreemptive().build());
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

    abstract int getService1Port();

    abstract int getService2Port();

    @Test
    public void testService1Hmac() {
        testProtected("http://localhost:" + getService1Port() + "/service1",
                      "jack",
                      "password",
                      Set.of("user", "admin"),
                      Set.of(),
                      "Service1 - HMAC signature");
    }

    @Test
    public void testService1Rsa() {
        testProtected("http://localhost:" + getService1Port() + "/service1-rsa",
                      "jack",
                      "password",
                      Set.of("user", "admin"),
                      Set.of(),
                      "Service1 - RSA signature");
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

        Response response = callProtected(uri, username, password);
        assertThat(response.getStatus(), is(403));
    }

    private void testProtected(String uri,
                               String username,
                               String password,
                               Set<String> expectedRoles,
                               Set<String> invalidRoles,
                               String service) {

        Response response = callProtected(uri, username, password);

        String entity = response.readEntity(String.class);

        assertThat(response.getStatus(), is(200));

        // check login
        assertThat(entity, containsString("id='" + username + "'"));
        // check roles
        expectedRoles.forEach(role -> assertThat(entity, containsString(":" + role)));
        invalidRoles.forEach(role -> assertThat(entity, not(containsString(":" + role))));

        assertThat(entity, containsString("id='" + service + "'"));
    }
}
