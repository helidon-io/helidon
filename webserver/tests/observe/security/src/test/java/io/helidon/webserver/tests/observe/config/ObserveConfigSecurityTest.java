/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.observe.config;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.http.Status;
import io.helidon.json.JsonObject;
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
import io.helidon.webserver.observe.config.ConfigObserver;
import io.helidon.webserver.security.SecurityFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ObserveConfigSecurityTest {
    private static final String USERNAME = "jack";
    private static final String PASSWORD = "password";
    private static final String USER_ROLE = "user";
    private static final String MASKED_VALUE = "*********";
    private static final String RAW_MASTER_PASSWORD_KEY = "SECURE_CONFIG_AES_MASTER_PWD";
    private static final Map<String, MyUser> USERS = Map.of(
            USERNAME, new MyUser(USERNAME, PASSWORD.toCharArray(), Set.of(USER_ROLE)));
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            RAW_MASTER_PASSWORD_KEY,
            "secure.config.aes.master.pwd",
            "app.clientSecret",
            "app.credential",
            "app.auth-token",
            "app.api-key",
            "app.access-key",
            "app.connection-url",
            "app.privateKey");

    private final Http1Client client;

    ObserveConfigSecurityTest(URI uri) {
        Security security = Security.builder()
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
                .addFeature(ObserveFeature.just(ConfigObserver.builder()
                                                        .permitAll(true)
                                                        .unsafeValues(true)
                                                        .build()))
                .routing(routing -> routing
                        .get("/observe/config/values", SecurityFeature.rolesAllowed(USER_ROLE))
                        .get("/observe/config/values/{name}", SecurityFeature.rolesAllowed(USER_ROLE)));
    }

    @Test
    void userRoleReadsPlainConfigValue() {
        assertConfigValue("app.text", "should be seen");
    }

    @Test
    void userRoleCannotReadSensitiveConfigValues() {
        for (String sensitiveKey : SENSITIVE_KEYS) {
            assertConfigValue(sensitiveKey, MASKED_VALUE);
        }
    }

    @Test
    void userRoleCannotReadSensitiveConfigValuesFromBulkValues() {
        try (Http1ClientResponse response = authenticatedGet("/observe/config/values")) {
            assertThat(response.status(), is(Status.OK_200));

            JsonObject entity = response.as(JsonObject.class);
            assertThat(entity.stringValue("app.text").orElse(null), is("should be seen"));
            for (String sensitiveKey : SENSITIVE_KEYS) {
                assertThat(entity.stringValue(sensitiveKey).orElse(null), is(MASKED_VALUE));
            }
        }
    }

    private void assertConfigValue(String key, String expectedValue) {
        try (Http1ClientResponse response = authenticatedGet("/observe/config/values/" + key)) {
            assertThat(response.status(), is(Status.OK_200));

            JsonObject entity = response.as(JsonObject.class);
            assertThat(entity.stringValue("value").orElse(null), is(expectedValue));
        }
    }

    private Http1ClientResponse authenticatedGet(String path) {
        return client.get()
                .uri(path)
                .property(EndpointConfig.PROPERTY_OUTBOUND_ID, USERNAME)
                .property(EndpointConfig.PROPERTY_OUTBOUND_SECRET, PASSWORD)
                .request();
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
        public boolean isPasswordValid(char[] candidate) {
            return Arrays.equals(password(), candidate);
        }
    }
}
