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

package io.helidon.webserver.security;

import java.util.Set;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.security.AuditEvent;
import io.helidon.security.Security;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static io.helidon.security.EndpointConfig.PROPERTY_OUTBOUND_ID;
import static io.helidon.security.EndpointConfig.PROPERTY_OUTBOUND_SECRET;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * A set of tests that are used both by configuration based
 * and programmatic tests.
 */
abstract class WebSecurityTests {
    static final String AUDIT_MESSAGE_FORMAT = "Unit test message format";

    private final UnitTestAuditProvider myAuditProvider;
    private final WebClient securityClient;
    private final Http1Client webClient;

    WebSecurityTests(WebServer server, Http1Client webClient) {
        this.myAuditProvider = server.context().get(UnitTestAuditProvider.class).orElseThrow();
        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.builder().build())
                .build();
        this.securityClient = WebClient.builder()
                .servicesDiscoverServices(false)
                .baseUri("http://localhost:" + server.port())
                .addService(WebClientSecurity.create(security))
                .build();
        this.webClient = webClient;
    }

    @Test
    void basicTestJohn() {
        String username = "john";
        String password = "password";

        testProtected("/noRoles", username, password, Set.of(), Set.of());
        // this user has no roles, all requests should fail except for public
        testForbidden("/user", username, password);
        testForbidden("/admin", username, password);
        testForbidden("/deny", username, password);
    }

    @Test
    void basicTestJack() {
        String username = "jack";
        String password = "jackIsGreat";

        testProtected("/noRoles", username, password, Set.of("user", "admin"), Set.of());
        testProtected("/user", username, password, Set.of("user", "admin"), Set.of());
        testProtected("/admin", username, password, Set.of("user", "admin"), Set.of());
        testForbidden("/deny", username, password);
    }

    @Test
    void basicTestJill() {
        String username = "jill";
        String password = "password";

        testProtected("/noRoles", username, password, Set.of("user"), Set.of("admin"));
        testProtected("/user", username, password, Set.of("user"), Set.of("admin"));
        testForbidden("/admin", username, password);
        testForbidden("/deny", username, password);
    }

    @Test
    void basicTest401() {
        try (Http1ClientResponse response = webClient.get("/noRoles").request()) {
            assertThat(response.status(), is(Status.UNAUTHORIZED_401));

            String header = response.headers()
                    .first(HeaderNames.WWW_AUTHENTICATE)
                    .orElseThrow(() -> new IllegalStateException(
                            "Header " + HeaderNames.WWW_AUTHENTICATE + " is" + " not present in response!"));

            assertThat(header.toLowerCase(), is("basic realm=\"mic\""));
        }

        try (HttpClientResponse response = callProtected("/noRoles", "invalidUser", "invalidPassword")) {
            assertThat(response.status(), is(Status.UNAUTHORIZED_401));

            String header = response.headers()
                    .first(HeaderNames.WWW_AUTHENTICATE)
                    .orElseThrow(() -> new IllegalStateException(
                            "Header " + HeaderNames.WWW_AUTHENTICATE + " is" + " not present in response!"));
            assertThat(header.toLowerCase(), is("basic realm=\"mic\""));
        }
    }

    @Test
    void testCustomizedAudit() {
        try (Http1ClientResponse response = webClient.get("/auditOnly").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        // audit
        AuditEvent auditEvent = myAuditProvider.getAuditEvent();
        assertThat(auditEvent, notNullValue());
        assertThat(auditEvent.messageFormat(), is(AUDIT_MESSAGE_FORMAT));
        assertThat(auditEvent.severity(), is(AuditEvent.AuditSeverity.SUCCESS));
    }

    private void testForbidden(String uri, String username, String password) {
        try (HttpClientResponse response = callProtected(uri, username, password)) {
            assertThat(uri + " for user " + username + " should be forbidden",
                    response.status(),
                    is(Status.FORBIDDEN_403));
        }
    }

    private void testProtected(String uri,
                               String username,
                               String password,
                               Set<String> expectedRoles,
                               Set<String> invalidRoles) {

        try (HttpClientResponse response = callProtected(uri, username, password)) {

            assertThat(response.status(), is(Status.OK_200));

            String entity = response.entity().as(String.class);

            // check login
            assertThat(entity, containsString("id='" + username + "'"));
            // check roles
            expectedRoles.forEach(role -> assertThat(entity, containsString(":" + role)));
            invalidRoles.forEach(role -> assertThat(entity, not(containsString(":" + role))));
        }
    }

    private HttpClientResponse callProtected(String uri, String username, String password) {
        return securityClient.get(uri)
                .property(PROPERTY_OUTBOUND_ID, username)
                .property(PROPERTY_OUTBOUND_SECRET, password)
                .request();
    }

}
