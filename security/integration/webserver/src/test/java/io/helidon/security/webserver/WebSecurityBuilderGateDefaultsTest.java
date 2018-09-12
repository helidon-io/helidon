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

package io.helidon.security.webserver;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.security.AuditEvent;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
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
 * Unit test for {@link WebSecurity}.
 */
public class WebSecurityBuilderGateDefaultsTest {
    private static final String SERVER_BASE = "http://localhost:" + WebSecurityTests.PORT;
    private static UnitTestAuditProvider myAuditProvider;
    private static WebServer server;
    private static Client authFeatureClient;
    private static Client client;

    @BeforeAll
    public static void initClass() throws InterruptedException {
        authFeatureClient = ClientBuilder.newClient()
                .register(HttpAuthenticationFeature.universalBuilder().build());
        client = ClientBuilder.newClient();

        WebSecurityTestUtil.auditLogFinest();
        myAuditProvider = new UnitTestAuditProvider();

        Config config = Config.create();

        Security security = Security.builderFromConfig(config)
                .addAuditProvider(myAuditProvider).build();

        Routing routing = Routing.builder()
                .register(WebSecurity.from(security).securityDefaults(WebSecurity.rolesAllowed("admin").audit()))
                // will only accept admin (due to gate defaults)
                .get("/noRoles", WebSecurity.enforce())
                .get("/user[/{*}]", WebSecurity.rolesAllowed("user"))
                .get("/admin", WebSecurity.rolesAllowed("admin"))
                // will also accept admin (due to gate defaults)
                .get("/deny", WebSecurity.rolesAllowed("deny"))
                // audit is on from gate defaults
                .get("/auditOnly", WebSecurity
                        .enforce()
                        .skipAuthentication()
                        .skipAuthorization()
                        .auditEventType("unit_test")
                        .auditMessageFormat(WebSecurityTests.AUDIT_MESSAGE_FORMAT)
                )
                .get("/{*}", (req, res) -> {
                    Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);
                    res.headers().contentType(MediaType.TEXT_PLAIN.withCharset("UTF-8"));
                    res.send("Hello, you are: \n" + securityContext
                            .map(ctx -> ctx.getUser().orElse(SecurityContext.ANONYMOUS).toString())
                            .orElse("Security context is null"));
                })
                .build();

        server = WebServer.create(ServerConfiguration.builder().port(WebSecurityTests.PORT).build(), routing);
        long t = System.currentTimeMillis();
        CountDownLatch cdl = new CountDownLatch(1);
        server.start().thenAccept(webServer -> {
            long time = System.currentTimeMillis() - t;
            System.out.println("Started server on localhost:" + webServer.port() + " in " + time + " millis");
            cdl.countDown();
        });

        //we must wait for server to start, so other tests are not triggered until it is ready!
        assertThat("Timeout while waiting for server to start!", cdl.await(5, TimeUnit.SECONDS), is(true));
    }

    @AfterAll
    public static void stopIt() throws InterruptedException {
        authFeatureClient.close();
        client.close();

        WebSecurityTestUtil.stopServer(server);
    }

    @Test
    public void basicTestJohn() {
        String username = "john";
        String password = "password";

        testForbidden(SERVER_BASE + "/noRoles", username, password);
        testForbidden(SERVER_BASE + "/user", username, password);
        testForbidden(SERVER_BASE + "/admin", username, password);
        testForbidden(SERVER_BASE + "/deny", username, password);
    }

    @Test
    public void basicTestJack() {
        String username = "jack";
        String password = "jackIsGreat";

        testProtected(SERVER_BASE + "/noRoles",
                      username,
                      password,
                      CollectionsHelper.setOf("user", "admin"),
                      CollectionsHelper.setOf());
        testProtected(SERVER_BASE + "/user",
                      username,
                      password,
                      CollectionsHelper.setOf("user", "admin"),
                      CollectionsHelper.setOf());
        testProtected(SERVER_BASE + "/admin",
                      username,
                      password,
                      CollectionsHelper.setOf("user", "admin"),
                      CollectionsHelper.setOf());
        testProtected(SERVER_BASE + "/deny",
                      username,
                      password,
                      CollectionsHelper.setOf("user", "admin"),
                      CollectionsHelper.setOf());
    }

    @Test
    public void basicTestJill() {
        String username = "jill";
        String password = "password";

        testForbidden(SERVER_BASE + "/noRoles", username, password);
        testProtected(SERVER_BASE + "/user",
                      username,
                      password,
                      CollectionsHelper.setOf("user"),
                      CollectionsHelper.setOf("admin"));
        testForbidden(SERVER_BASE + "/admin", username, password);
        testForbidden(SERVER_BASE + "/deny", username, password);
    }

    @Test
    public void basicTest401() {
        // here we call the endpoint
        Response response = client.target(SERVER_BASE + "/noRoles")
                .request()
                .get();

        assertThat(response.getStatus(), is(401));
        String authHeader = response.getHeaderString(Http.Header.WWW_AUTHENTICATE);
        assertThat(authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), is("basic realm=\"mic\""));

        response = callProtected(SERVER_BASE + "/noRoles", "invalidUser", "invalidPassword");
        assertThat(response.getStatus(), is(401));
        authHeader = response.getHeaderString(Http.Header.WWW_AUTHENTICATE);
        assertThat(authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), is("basic realm=\"mic\""));
    }

    @Test
    public void testCustomizedAudit() throws InterruptedException {
        // even though I send username and password, this MUST NOT require authentication
        // as then audit is called twice - first time with 401 (challenge) and second time with 200 (correct request)
        // and that intermittently breaks this test
        Response response = client.target(SERVER_BASE + "/auditOnly").request().get();

        assertThat(response.getStatus(), is(200));

        // audit
        AuditEvent auditEvent = myAuditProvider.getAuditEvent();
        assertThat(auditEvent, notNullValue());
        assertThat(auditEvent.toString(), auditEvent.getMessageFormat(), is(WebSecurityTests.AUDIT_MESSAGE_FORMAT));
        assertThat(auditEvent.toString(), auditEvent.getSeverity(), is(AuditEvent.AuditSeverity.SUCCESS));
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
