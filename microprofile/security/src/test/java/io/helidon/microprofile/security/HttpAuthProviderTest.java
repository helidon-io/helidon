/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.security;

import java.util.Set;

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.Configuration;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.security.providers.httpauth.HttpDigestAuthProvider;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
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
@HelidonTest
@AddBean(TestResource.class)
@Configuration(configSources = "http-auth.yaml")
class HttpAuthProviderTest {

    private static final Client authFeatureClient = ClientBuilder.newClient()
            .register(HttpAuthenticationFeature.universalBuilder().build());
    private static final Client client = ClientBuilder.newClient();
    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";

    private final String serverBase;
    private final String digestUri;
    private final String digestOldUri;

    @Inject
    HttpAuthProviderTest(WebTarget target) {
        serverBase = target.getUri().toString();
        digestUri = serverBase + "/digest";
        digestOldUri = serverBase + "/digest_old";
    }

    @AfterAll
    static void stopIt() {
        authFeatureClient.close();
    }

    @Test
    void basicTestJack() {
        testProtected(serverBase, "jack", "jackIsGreat", Set.of("user", "admin"), Set.of());
    }

    @Test
    void basicTestJill() {
        testProtected(serverBase, "jill", "password", Set.of("user"), Set.of("admin"));
    }

    @Test
    void digestTestJack() {
        testProtected(digestUri, "jack", "jackIsGreat", Set.of("user", "admin"), Set.of());
    }

    @Test
    void digestTestJill() {
        testProtected(digestUri, "jill", "password", Set.of("user"), Set.of("admin"));
    }

    @Test
    void digestOldTestJack() {
        testProtected(digestOldUri, "jack", "jackIsGreat", Set.of("user", "admin"), Set.of());
    }

    @Test
    void digestOldTestJill() {
        testProtected(digestOldUri, "jill", "password", Set.of("user"), Set.of("admin"));
    }

    @Test
    void basicTest401() {
        // here we call the endpoint
        Response response = client.target(serverBase)
                .request()
                .get();

        assertThat(response.getStatus(), is(401));
        String authHeader = response.getHeaderString(WWW_AUTHENTICATE);
        assertThat("We should have received " + WWW_AUTHENTICATE + " header", authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), is("basic realm=\"mic\""));
    }

    @Test
    void digestTest401() {
        // here we call the endpoint
        Response response = client.target(digestUri)
                .request()
                .get();

        assertThat(response.getStatus(), is(401));
        String authHeader = response.getHeaderString(WWW_AUTHENTICATE);
        assertThat(authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), startsWith("digest realm=\"mic\""));
        assertThat(authHeader.toLowerCase(), containsString("qop="));
    }

    @Test
    @Disabled
    void digestOldTest401() {
        // here we call the endpoint
        Response response = client.target(digestOldUri)
                .request()
                .get();

        assertThat(response.getStatus(), is(401));
        String authHeader = response.getHeaderString(WWW_AUTHENTICATE);
        assertThat(authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), startsWith("digest realm=\"mic\""));
        assertThat(authHeader.toLowerCase(), not(containsString("qop=")));
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

}
