/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.observe.metrics;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.http.Status;
import io.helidon.openapi.OpenApiFeature;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Security;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.security.providers.httpauth.SecureUserStore;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.security.SecurityFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ObserveSecurityTest {
    private static final Map<String, MyUser> USERS = new HashMap<>();

    private final Http1Client client;
    private final Security security;

    ObserveSecurityTest(URI uri) {
        USERS.put("jack", new MyUser("jack", "password".toCharArray(), Set.of("user")));

        security = Security.builder()
                .addProvider(HttpBasicAuthProvider.builder())
                .build();

        WebClientSecurity securityService = WebClientSecurity.create(security);

        client = Http1Client.builder()
                .baseUri(uri)
                .addService(securityService)
                .build();
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        server.featuresDiscoverServices(false)
                .addFeature(ContextFeature.create())
                .addFeature(SecurityFeature.builder()
                                    .security(buildWebSecurity())
                                    .defaults(SecurityFeature.authenticate())
                                    .build())
                .addFeature(ObserveFeature.create())
                .addFeature(OpenApiFeature.create())
                .routing(routing -> routing
                        .get("/observe/metrics", SecurityFeature.rolesAllowed("user"))
                        .get("/openapi", SecurityFeature.rolesAllowed("user")));
    }

    @Test
    void testMetrics() {
        testSecureEndpoint("/observe/metrics");
    }

    @Test
    void testOpenApi() {
        testSecureEndpoint("/openapi");
    }

    void testSecureEndpoint(String uri) {
        try (Http1ClientResponse response = client.get().uri(uri).request()) {
            assertThat(response.status(), is(Status.UNAUTHORIZED_401));
        }

        try (Http1ClientResponse response = client.get()
                .uri(uri)
                .property(EndpointConfig.PROPERTY_OUTBOUND_ID, "jack")
                .property(EndpointConfig.PROPERTY_OUTBOUND_SECRET, "password")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    private static Security buildWebSecurity() {
        return Security.builder()
                .addAuthenticationProvider(
                        HttpBasicAuthProvider.builder()
                                .realm("helidon")
                                .userStore(buildUserStore()),
                        "http-basic-auth")
                .build();
    }

    private static SecureUserStore buildUserStore() {
        return login -> Optional.ofNullable(USERS.get(login));
    }

    private record MyUser(String login, char[] password, Set<String> roles) implements SecureUserStore.User {

        @Override
        public boolean isPasswordValid(char[] password) {
            return Arrays.equals(password(), password);
        }
    }
}
