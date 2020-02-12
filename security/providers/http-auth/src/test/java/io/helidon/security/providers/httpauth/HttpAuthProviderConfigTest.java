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

package io.helidon.security.providers.httpauth;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import io.helidon.config.Config;
import io.helidon.security.Security;
import io.helidon.security.integration.jersey.SecurityFeature;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jersey.JerseySupport;

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
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link HttpBasicAuthProvider} and {@link HttpDigestAuthProvider}.
 */
public class HttpAuthProviderConfigTest {

    private static final Client authFeatureClient = ClientBuilder.newClient()
            .register(HttpAuthenticationFeature.universalBuilder().build());
    private static final Client client = ClientBuilder.newClient();

    private static String serverBase;
    private static String digestUri;
    private static String digestOldUri;

    private static WebServer server;

    @BeforeAll
    public static void startIt() throws Throwable {
        startServer(Security.create(Config.create().get("security")));

        serverBase = "http://localhost:" + server.port();
        digestUri = serverBase + "/digest";
        digestOldUri = serverBase + "/digest_old";
    }

    private static void startServer(Security security) throws Throwable {
        server = Routing.builder()
                .register(JerseySupport.builder()
                                  .register(TestResource.class)
                                  .register(SecurityFeature.builder(security).authorizeAnnotatedOnly(true).build())
                                  .register(new ExceptionMapper<Exception>() {
                                      @Override
                                      public Response toResponse(Exception exception) {
                                          exception.printStackTrace();
                                          return Response.serverError().build();
                                      }
                                  })
                                  .build())
                .build()
                .createServer();
        CountDownLatch cdl = new CountDownLatch(1);
        AtomicReference<Throwable> th = new AtomicReference<>();
        server.start().whenComplete((webServer, throwable) -> {
            th.set(throwable);
            cdl.countDown();
        });

        cdl.await();

        if (th.get() != null) {
            throw th.get();
        }
    }

    @AfterAll
    public static void stopIt() throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(1);

        server.shutdown()
                .whenComplete((webServer, throwable) -> {
                    cdl.countDown();
                    ;
                });

        cdl.await(10, TimeUnit.SECONDS);
        authFeatureClient.close();
    }

    private Response callProtected(String uri, String username, String password) {
        // here we call the endpoint
        return authFeatureClient.target(uri)
                .request()
                .property(HTTP_AUTHENTICATION_USERNAME, username)
                .property(HTTP_AUTHENTICATION_PASSWORD, password)
                .get();
    }

    private void testProtected(String uri,
                               String username,
                               String password,
                               Set<String> expectedRoles,
                               Set<String> invalidRoles) {

        Response response = callProtected(uri, username, password);

        String entity = response.readEntity(String.class);

        assertThat(response.getStatus(), is(200));

        // check login
        assertThat(entity, containsString("user: " + username));
        // check roles
        expectedRoles.forEach(role -> assertThat(entity, containsString(":" + role)));
        invalidRoles.forEach(role -> assertThat(entity, not(containsString(":" + role))));
    }

    @Test
    public void basicTestJack() {
        testProtected(serverBase, "jack", "jackIsGreat", Set.of("user", "admin"), Set.of());
    }

    @Test
    public void basicTestJill() {
        testProtected(serverBase, "jill", "password", Set.of("user"), Set.of("admin"));
    }

    @Test
    public void digestTestJack() {
        testProtected(digestUri, "jack", "jackIsGreat", Set.of("user", "admin"), Set.of());
    }

    @Test
    public void digestTestJill() {
        testProtected(digestUri, "jill", "password", Set.of("user"), Set.of("admin"));
    }

    @Test
    public void digestOldTestJack() {
        testProtected(digestOldUri, "jack", "jackIsGreat", Set.of("user", "admin"), Set.of());
    }

    @Test
    public void digestOldTestJill() {
        testProtected(digestOldUri, "jill", "password", Set.of("user"), Set.of("admin"));
    }

    @Test
    public void basicTest401() {
        // here we call the endpoint
        Response response = client.target(serverBase)
                .request()
                .get();

        assertThat(response.getStatus(), is(401));
        String authHeader = response.getHeaderString(HttpBasicAuthProvider.HEADER_AUTHENTICATION_REQUIRED);
        assertThat(authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), is("basic realm=\"mic\""));
    }

    @Test
    public void digestTest401() {
        // here we call the endpoint
        Response response = client.target(digestUri)
                .request()
                .get();

        assertThat(response.getStatus(), is(401));
        String authHeader = response.getHeaderString(HttpBasicAuthProvider.HEADER_AUTHENTICATION_REQUIRED);
        assertThat(authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), startsWith("digest realm=\"mic\""));
        assertThat(authHeader.toLowerCase(), containsString("qop="));
    }

    @Test
    public void digestOldTest401() {
        // here we call the endpoint
        Response response = client.target(digestOldUri)
                .request()
                .get();

        assertThat(response.getStatus(), is(401));
        String authHeader = response.getHeaderString(HttpBasicAuthProvider.HEADER_AUTHENTICATION_REQUIRED);
        assertThat(authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), startsWith("digest realm=\"mic\""));
        assertThat(authHeader.toLowerCase(), not(containsString("qop=")));
    }

}
