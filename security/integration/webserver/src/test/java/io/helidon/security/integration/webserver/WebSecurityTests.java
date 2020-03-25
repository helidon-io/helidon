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

package io.helidon.security.integration.webserver;

import java.util.Set;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import io.helidon.common.http.Http;
import io.helidon.security.AuditEvent;
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
 * A set of tests that are used both by configuration based
 * and programmatic tests.
 */
public abstract class WebSecurityTests {
    static final String HEADER_BEARER_TOKEN = "BEARER_TOKEN";
    static final String HEADER_NAME = "NAME_FROM_REQUEST";

    static final String AUDIT_MESSAGE_FORMAT = "Unit test message format";
    static UnitTestAuditProvider myAuditProvider;
    static WebServer server;
    private static Client authFeatureClient;
    private static Client client;

    @BeforeAll
    static void buildClients() {
        authFeatureClient = ClientBuilder.newClient()
                .register(HttpAuthenticationFeature.universalBuilder().build());
        client = ClientBuilder.newClient();
    }

    @AfterAll
    static void stopIt() throws InterruptedException {
        authFeatureClient.close();
        client.close();

        WebSecurityTestUtil.stopServer(server);
    }

    abstract String serverBaseUri();

    @Test
    void basicTestJohn() {
        String username = "john";
        String password = "password";

        testProtected(serverBaseUri() + "/noRoles", username, password, Set.of(), Set.of());
        // this user has no roles, all requests should fail except for public
        testForbidden(serverBaseUri() + "/user", username, password);
        testForbidden(serverBaseUri() + "/admin", username, password);
        testForbidden(serverBaseUri() + "/deny", username, password);
    }

    @Test
    void basicTestJack() {
        String username = "jack";
        String password = "jackIsGreat";

        testProtected(serverBaseUri() + "/noRoles",
                      username,
                      password,
                      Set.of("user", "admin"),
                      Set.of());
        testProtected(serverBaseUri() + "/user",
                      username,
                      password,
                      Set.of("user", "admin"),
                      Set.of());
        testProtected(serverBaseUri() + "/admin",
                      username,
                      password,
                      Set.of("user", "admin"),
                      Set.of());
        testForbidden(serverBaseUri() + "/deny", username, password);
    }

    @Test
    void basicTestJill() {
        String username = "jill";
        String password = "password";

        testProtected(serverBaseUri() + "/noRoles",
                      username,
                      password,
                      Set.of("user"),
                      Set.of("admin"));
        testProtected(serverBaseUri() + "/user",
                      username,
                      password,
                      Set.of("user"),
                      Set.of("admin"));
        testForbidden(serverBaseUri() + "/admin", username, password);
        testForbidden(serverBaseUri() + "/deny", username, password);
    }

    @Test
    void basicTest401() {
        // here we call the endpoint
        Response response = client.target(serverBaseUri() + "/noRoles")
                .request()
                .get();

        assertThat(response.getStatus(), is(401));
        String authHeader = response.getHeaderString(Http.Header.WWW_AUTHENTICATE);
        assertThat(authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), is("basic realm=\"mic\""));

        response = callProtected(serverBaseUri() + "/noRoles", "invalidUser", "invalidPassword");
        assertThat(response.getStatus(), is(401));
        authHeader = response.getHeaderString(Http.Header.WWW_AUTHENTICATE);
        assertThat(authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), is("basic realm=\"mic\""));
    }

    @Test
    void testCustomizedAudit() throws InterruptedException {
        Response response = client.target(serverBaseUri() + "/auditOnly").request().get();

        assertThat(response.getStatus(), is(200));

        // audit
        AuditEvent auditEvent = myAuditProvider.getAuditEvent();
        assertThat(auditEvent, notNullValue());
        assertThat(auditEvent.messageFormat(), is(AUDIT_MESSAGE_FORMAT));
        assertThat(auditEvent.severity(), is(AuditEvent.AuditSeverity.SUCCESS));
    }

    private void testForbidden(String uri, String username, String password) {
        Response response = callProtected(uri, username, password);
        assertThat(uri + " for user " + username + " should be forbidden",
                   response.getStatus(),
                   is(Http.Status.FORBIDDEN_403.code()));
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
        assertThat(entity, containsString("id='" + username + "'"));
        // check roles
        expectedRoles.forEach(role -> assertThat(entity, containsString(":" + role)));
        invalidRoles.forEach(role -> assertThat(entity, not(containsString(":" + role))));
    }

    private Response callProtected(String uri, String username, String password) {
        // here we call the endpoint
        return authFeatureClient.target(uri)
                .request()
                .property(HTTP_AUTHENTICATION_USERNAME, username)
                .property(HTTP_AUTHENTICATION_PASSWORD, password)
                .get();
    }

}
