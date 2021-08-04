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

package io.helidon.security.examples.signatures;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.security.Security;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.security.providers.httpauth.HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD;
import static io.helidon.security.providers.httpauth.HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Actual unit tests are shared by config and builder example.
 */
public abstract class SignatureExampleTest {
    private static WebClient client;

    @BeforeAll
    public static void classInit() {
        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.builder().build())
                .build();

        client = WebClient.builder()
                .addService(WebClientSecurity.create(security))
                .build();
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


    private void testProtected(String uri,
                               String username,
                               String password,
                               Set<String> expectedRoles,
                               Set<String> invalidRoles,
                               String service) {
        client.get()
                .uri(uri)
                .property(EP_PROPERTY_OUTBOUND_USER, username)
                .property(EP_PROPERTY_OUTBOUND_PASSWORD, password)
                .request(String.class)
                .thenAccept(it -> {
                    // check login
                    assertThat(it, containsString("id='" + username + "'"));
                    // check roles
                    expectedRoles.forEach(role -> assertThat(it, containsString(":" + role)));
                    invalidRoles.forEach(role -> assertThat(it, not(containsString(":" + role))));

                    assertThat(it, containsString("id='" + service + "'"));
                })
                .await();
    }
}
