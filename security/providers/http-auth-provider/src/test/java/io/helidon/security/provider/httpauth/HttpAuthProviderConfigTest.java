/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.provider.httpauth;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.ExceptionMapper;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config;
import io.helidon.security.Security;
import io.helidon.security.jersey.SecurityFeature;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
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
    private static final int PORT = 9900;
    private static final String SERVER_BASE = "http://localhost:" + PORT;
    private static final Client authFeatureClient = ClientBuilder.newClient()
            .register(HttpAuthenticationFeature.universalBuilder().build());
    private static final Client client = ClientBuilder.newClient();
    private static final String DIGEST_URI = SERVER_BASE + "/digest";
    private static final String DIGEST_OLD_URI = SERVER_BASE + "/digest_old";

    private static HttpServer grizzlyServerInstance;

    @BeforeAll
    public static void startIt() {
        Config config = Config.create();

        Security security = Security.fromConfig(config);

        URI baseUri = UriBuilder.fromUri("http://localhost/").port(PORT).build();
        ResourceConfig resourceConfig = new ResourceConfig()
                // register JAX-RS resource
                .register(TestResource.class)
                // integrate security
                .register(SecurityFeature.builder(security).authorizeAnnotatedOnly(true).build())
                .register(new ExceptionMapper<Exception>() {
                    @Override
                    public Response toResponse(Exception exception) {
                        exception.printStackTrace();
                        return Response.serverError().build();
                    }
                });

        // create container (Grizzly)
        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(baseUri, resourceConfig);

        // and start the whole fun
        try {
            server.start();
            //only assign after start, so test can check for it
            grizzlyServerInstance = server;
        } catch (IOException e) {
            throw new RuntimeException("Failed to start server", e);
        }
    }

    @AfterAll
    public static void stopIt() {
        grizzlyServerInstance.shutdownNow();
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
        testProtected(SERVER_BASE, "jack", "jackIsGreat", CollectionsHelper.setOf("user", "admin"), CollectionsHelper.setOf());
    }

    @Test
    public void basicTestJill() {
        testProtected(SERVER_BASE, "jill", "password", CollectionsHelper.setOf("user"), CollectionsHelper.setOf("admin"));
    }

    @Test
    public void digestTestJack() {
        testProtected(DIGEST_URI, "jack", "jackIsGreat", CollectionsHelper.setOf("user", "admin"), CollectionsHelper.setOf());
    }

    @Test
    public void digestTestJill() {
        testProtected(DIGEST_URI, "jill", "password", CollectionsHelper.setOf("user"), CollectionsHelper.setOf("admin"));
    }

    @Test
    public void digestOldTestJack() {
        testProtected(DIGEST_OLD_URI, "jack", "jackIsGreat", CollectionsHelper.setOf("user", "admin"), CollectionsHelper.setOf());
    }

    @Test
    public void digestOldTestJill() {
        testProtected(DIGEST_OLD_URI, "jill", "password", CollectionsHelper.setOf("user"), CollectionsHelper.setOf("admin"));
    }

    @Test
    public void basicTest401() {
        // here we call the endpoint
        Response response = client.target(SERVER_BASE)
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
        Response response = client.target(DIGEST_URI)
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
        Response response = client.target(DIGEST_OLD_URI)
                .request()
                .get();

        assertThat(response.getStatus(), is(401));
        String authHeader = response.getHeaderString(HttpBasicAuthProvider.HEADER_AUTHENTICATION_REQUIRED);
        assertThat(authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), startsWith("digest realm=\"mic\""));
        assertThat(authHeader.toLowerCase(), not(containsString("qop=")));
    }

}
