/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.Set;

import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpMediaTypes;
import io.helidon.http.Status;
import io.helidon.security.AuditEvent;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link SecurityHttpFeature}.
 */
@ServerTest
class WebSecurityBuilderGateDefaultsTest {

    private final UnitTestAuditProvider myAuditProvider;
    private final WebClient securityClient;
    private final Http1Client webClient;

    WebSecurityBuilderGateDefaultsTest(WebServer server, Http1Client webClient) {
        this.myAuditProvider = server.context().get(UnitTestAuditProvider.class).orElseThrow();
        // security for outbound in client
        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.builder().build())
                .build();
        this.securityClient = WebClient.builder()
                .baseUri("http://localhost:" + server.port())
                .servicesDiscoverServices(false)
                .addService(WebClientSecurity.create(security))
                .build();
        this.webClient = webClient;
    }

    @SetUpServer
    public static void setup(WebServerConfig.Builder serverBuilder) {
        WebSecurityTestUtil.auditLogFinest();
        UnitTestAuditProvider myAuditProvider = new UnitTestAuditProvider();

        Config config = Config.just(ConfigSources.classpath("security-application.yaml"));

        Security security = Security.builder(config.get("security"))
                .addAuditProvider(myAuditProvider)
                .build();

        Contexts.context()
                .orElseGet(Contexts::globalContext)
                .register(myAuditProvider);

        serverBuilder
                .featuresDiscoverServices(false)
                .addFeature(SecurityFeature.builder()
                                    .security(security)
                                    .defaults(SecurityFeature.rolesAllowed("admin"))
                                    .build())
                .addFeature(ContextFeature.create())
                .routing(builder -> builder
                        // will only accept admin (due to gate defaults)
                        .get("/noRoles", SecurityFeature.authenticate())
                        .get("/user/*", SecurityFeature.rolesAllowed("user"))
                        .get("/admin", SecurityFeature.rolesAllowed("admin"))
                        // will also accept admin (due to gate defaults)
                        .get("/deny", SecurityFeature.rolesAllowed("deny"))
                        // audit is on from gate defaults
                        .get("/auditOnly", SecurityFeature
                                .enforce()
                                .skipAuthentication()
                                .skipAuthorization()
                                .auditEventType("unit_test")
                                .auditMessageFormat(WebSecurityTests.AUDIT_MESSAGE_FORMAT))
                        .get("/*", (req, res) -> {
                            Optional<SecurityContext> securityContext = Contexts.context()
                                    .flatMap(it -> it.get(SecurityContext.class));
                            res.headers().contentType(HttpMediaTypes.PLAINTEXT_UTF_8);
                            res.send("Hello, you are: \n" + securityContext
                                    .map(ctx -> ctx.user().orElse(SecurityContext.ANONYMOUS).toString())
                                    .orElse("Security context is null"));
                        }))
                .build();
    }

    @Test
    public void testCustomizedAudit() {
        // even though I send username and password, this MUST NOT require authentication
        // as then audit is called twice - first time with 401 (challenge) and second time with 200 (correct request)
        // and that intermittently breaks this test
        try (Http1ClientResponse response = webClient.get("/auditOnly").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        // audit
        AuditEvent auditEvent = myAuditProvider.getAuditEvent();
        assertThat(auditEvent, notNullValue());
        assertThat(auditEvent.toString(), auditEvent.messageFormat(), is(WebSecurityTests.AUDIT_MESSAGE_FORMAT));
        assertThat(auditEvent.toString(), auditEvent.severity(), is(AuditEvent.AuditSeverity.SUCCESS));
    }

    @Test
    void basicTestJohn() {
        String username = "john";
        String password = "password";

        testForbidden("/noRoles", username, password);
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
        testProtected("/deny", username, password, Set.of("user", "admin"), Set.of());
    }

    @Test
    void basicTestJill() {
        String username = "jill";
        String password = "password";

        testForbidden("/noRoles", username, password);
        testProtected("/user", username, password, Set.of("user"), Set.of("admin"));
        testForbidden("/admin", username, password);
        testForbidden("/deny", username, password);
    }

    @Test
    void basicTest401() {
        try (Http1ClientResponse response = webClient.get("/noRoles").request()) {

            if (response.status() != Status.UNAUTHORIZED_401) {
                assertThat("Response received: " + response.entity().as(String.class),
                           response.status(),
                           is(Status.UNAUTHORIZED_401));
            }

            assertThat(response.headers().first(HeaderNames.WWW_AUTHENTICATE),
                       optionalValue(is("Basic realm=\"mic\"")));
        }

        try (HttpClientResponse response = callProtected("/noRoles", "invalidUser", "invalidPassword")) {
            assertThat(response.status(), is(Status.UNAUTHORIZED_401));
            assertThat(response.headers().first(HeaderNames.WWW_AUTHENTICATE),
                       optionalValue(is("Basic realm=\"mic\"")));
        }

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

        String entity;
        try (HttpClientResponse response = callProtected(uri, username, password)) {
            assertThat(response.status(), is(Status.OK_200));

            entity = response.entity().as(String.class);

            // check login
            assertThat(entity, containsString("id='" + username + "'"));

            // check roles
            expectedRoles.forEach(role -> assertThat(entity, containsString(":" + role)));
            invalidRoles.forEach(role -> assertThat(entity, not(containsString(":" + role))));
        }
    }

    private HttpClientResponse callProtected(String uri, String username, String password) {
        // here we call the endpoint
        return securityClient.get(uri)
                .property(EndpointConfig.PROPERTY_OUTBOUND_ID, username)
                .property(EndpointConfig.PROPERTY_OUTBOUND_SECRET, password)
                .request();
    }
}
