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

package io.helidon.security.providers.idcs.mapper;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.json.JsonObject;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Grant;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityException;
import io.helidon.security.Subject;
import io.helidon.security.integration.common.RoleMapTracing;
import io.helidon.security.integration.common.SecurityTracing;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdcsMtRoleMapperProviderTest {
    private static final String ASSERTER_SCHEMA = "urn:ietf:params:scim:schemas:oracle:idcs:Asserter";
    private static final URI IDENTITY_URI = URI.create("https://idcs.example.com/identity/uri");
    private static final URI TOKEN_ENDPOINT_URI = URI.create("https://idcs.example.com/token/endpoint/uri");
    private static final URI AUTHORIZATION_ENDPOINT_URI =
            URI.create("https://idcs.example.com/authorization/endpoint/uri");
    private static final URI TENANT_ASSERT_URI = URI.create("https://tenant.example.com/admin/v1/Asserter");
    private static final URI TENANT_TOKEN_URI = URI.create("https://tenant.example.com/oauth2/v1/token");

    @Test
    void testGetGrantsFromServerSubmitsJsonObjectEntity() {
        IdcsMtRoleMapperProvider.Builder<?> builder = IdcsMtRoleMapperProvider.builder();
        builder.oidcConfig(oidcConfig())
                .multitenantEndpoints(new TestEndpoints());
        CapturingMtProvider provider = new CapturingMtProvider(builder, "tenant-app-token");

        provider.getGrantsFromServer("tenant-1",
                                     "tenant-app",
                                     Subject.builder()
                                             .principal(Principal.create("test-user"))
                                             .build());

        HttpClientRequest request = provider.request();
        assertThat(request, notNullValue());
        assertThat(request.resolvedUri().host(), is("tenant.example.com"));
        assertThat(request.resolvedUri().path().path(), is("/admin/v1/Asserter"));
        assertThat(request.headers().contains(HeaderNames.CONTENT_TYPE), is(false));
        assertThat(request.headers().get(HeaderNames.AUTHORIZATION).get(), is("Bearer tenant-app-token"));
        assertThat(provider.subjectName(), is("test-user"));
        assertThat(provider.entity(), is(instanceOf(JsonObject.class)));

        JsonObject requestEntity = (JsonObject) provider.entity();
        assertThat(requestEntity.stringValue("mappingAttributeValue").orElseThrow(), is("test-user"));
        assertThat(requestEntity.stringValue("subjectType").orElseThrow(),
                   is(IdcsRoleMapperProviderBase.IDCS_SUBJECT_TYPE_USER));
        assertThat(requestEntity.stringValue("appName").orElseThrow(), is("tenant-app"));
        assertThat(requestEntity.booleanValue("includeMemberships").orElseThrow(), is(true));
        assertThat(requestEntity.arrayValue("schemas").orElseThrow().get(0).orElseThrow().asString().value(),
                   is(ASSERTER_SCHEMA));
    }

    @Test
    void testInvalidTenantIdRejectedBeforeEndpointResolution() {
        TrackingEndpoints endpoints = new TrackingEndpoints();
        IdcsMtRoleMapperProvider.Builder<?> builder = IdcsMtRoleMapperProvider.builder();
        builder.oidcConfig(oidcConfig())
                .multitenantEndpoints(endpoints);
        CapturingMtProvider provider = new CapturingMtProvider(builder, "tenant-app-token");
        ProviderRequest request = Mockito.mock(ProviderRequest.class);
        Mockito.when(request.env())
                .thenReturn(SecurityEnvironment.builder()
                                    .targetUri(URI.create("http://service.example.test/protected"))
                                    .header(IdcsMtRoleMapperProvider.IDCS_TENANT_HEADER, "127.0.0.1:9999/")
                                    .header(IdcsMtRoleMapperProvider.IDCS_APP_HEADER, "tenant-app")
                                    .build());

        assertThrows(SecurityException.class,
                     () -> provider.map(request,
                                        AuthenticationResponse.builder()
                                                .user(Subject.builder()
                                                              .principal(Principal.create("test-user"))
                                                              .build())
                                                .build()));

        assertThat(endpoints.assertEndpointCalls, is(0));
        assertThat(endpoints.tokenEndpointCalls, is(0));
        assertThat(provider.request(), nullValue());
    }

    @Test
    void testDefaultEndpointsBuildTenantHostWithoutRegexReplacement() {
        IdcsMtRoleMapperProvider.DefaultMultitenancyEndpoints endpoints =
                new IdcsMtRoleMapperProvider.DefaultMultitenancyEndpoints(OidcConfig.builder()
                                                                                   .oidcMetadataWellKnown(false)
                                                                                   .clientId("client-id")
                                                                                   .clientSecret("client-secret")
                                                                                   .identityUri(URI.create("https://idcs.idcs.example.com"))
                                                                                   .tokenEndpointUri(TOKEN_ENDPOINT_URI)
                                                                                   .authorizationEndpointUri(AUTHORIZATION_ENDPOINT_URI)
                                                                                   .build());

        assertThat(endpoints.assertEndpoint("tenant-1"),
                   is(URI.create("https://tenant-1.idcs.example.com/admin/v1/Asserter")));
        assertThat(endpoints.tokenEndpoint("tenant-1"),
                   is(URI.create("https://tenant-1.idcs.example.com/oauth2/v1/token?IDCS_CLIENT_TENANT=idcs")));
        assertThat(endpoints.useClientCredentials("tenant-1", endpoints.tokenEndpoint("tenant-1")), is(true));
        assertThat(endpoints.useClientCredentials("tenant-1",
                                                  URI.create("https://other.idcs.example.com/oauth2/v1/token")),
                   is(false));
    }

    @Test
    void testDefaultEndpointsRejectInvalidTenantId() {
        IdcsMtRoleMapperProvider.DefaultMultitenancyEndpoints endpoints =
                new IdcsMtRoleMapperProvider.DefaultMultitenancyEndpoints(oidcConfig());

        assertThrows(SecurityException.class, () -> endpoints.tokenEndpoint("evil.example.com/path"));
        assertThrows(SecurityException.class, () -> endpoints.assertEndpoint("-tenant"));
    }

    @Test
    void testCustomEndpointsUseClientCredentialsByDefault() {
        assertThat(new TestEndpoints().useClientCredentials("tenant", TENANT_TOKEN_URI), is(true));
    }

    @Test
    void testProgrammaticTenantTokenRequestUsesClientCredentials() {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> host = new AtomicReference<>();
        Instant issueTime = Instant.now();
        String accessToken = SignedJwt.sign(Jwt.builder()
                                                    .algorithm("none")
                                                    .issuer("unit-test")
                                                    .issueTime(issueTime)
                                                    .expirationTime(issueTime.plusSeconds(3600))
                                                    .build(),
                                            Jwk.NONE_JWK)
                .tokenContent();

        WebServer tokenServer = WebServer.builder()
                .host(InetAddress.getLoopbackAddress().getHostAddress())
                .routing(routing -> routing
                        .post("/oauth2/v1/token", (req, res) -> {
                            authorization.set(req.headers()
                                                      .first(HeaderNames.AUTHORIZATION)
                                                      .orElse(null));
                            host.set(req.headers()
                                             .first(HeaderNames.HOST)
                                             .orElse(null));
                            res.header(HeaderValues.CONTENT_TYPE_JSON)
                                    .send("{\"access_token\":\"" + accessToken + "\"}");
                        }))
                .build()
                .start();

        try {
            String identityHost = "infra.example.test";
            String tenantHost = "tenant.example.test";
            String untrustedHost = identityHost;
            AtomicInteger tokenEndpointCalls = new AtomicInteger();
            AtomicReference<Boolean> resolveTokenEndpoint = new AtomicReference<>(true);
            OidcConfig config = OidcConfig.builder()
                    .oidcMetadataWellKnown(false)
                    .clientId("client-id")
                    .clientSecret("client-secret")
                    .identityUri(URI.create("http://" + identityHost + ":" + tokenServer.port()))
                    .tokenEndpointUri(URI.create("http://" + identityHost + ":" + tokenServer.port()
                                                         + "/oauth2/v1/token"))
                    .authorizationEndpointUri(URI.create("http://" + identityHost + ":" + tokenServer.port()
                                                                 + "/oauth2/v1/authorize"))
                    .validateJwtWithJwk(false)
                    .webclient(webClient -> webClient.dnsResolver((hostname, dnsAddressLookup) ->
                                                                           InetAddress.getLoopbackAddress()))
                    .build();
            IdcsMtRoleMapperProvider.Builder<?> builder = IdcsMtRoleMapperProvider.builder();
            builder.oidcConfig(config)
                    .multitenantEndpoints(new IdcsMtRoleMapperProvider.MultitenancyEndpoints() {
                        @Override
                        public String idcsInfraTenantId() {
                            return "infra";
                        }

                        @Override
                        public URI assertEndpoint(String tenantId) {
                            return URI.create("http://" + hostForTenant(tenantId) + ":" + tokenServer.port()
                                                      + "/admin/v1/Asserter");
                        }

                        @Override
                        public URI tokenEndpoint(String tenantId) {
                            tokenEndpointCalls.incrementAndGet();
                            if (!resolveTokenEndpoint.get()) {
                                throw new SecurityException("Endpoint resolution failure");
                            }
                            return URI.create("http://" + hostForTenant(tenantId) + ":" + tokenServer.port()
                                                      + "/oauth2/v1/token?IDCS_CLIENT_TENANT=infra");
                        }

                        @Override
                        public boolean useClientCredentials(String tenantId, URI tokenEndpoint) {
                            return tokenEndpoint.getHost().equals(tenantHost);
                        }

                        private String hostForTenant(String tenantId) {
                            return tenantId.equals("tenant") ? tenantHost : untrustedHost;
                        }
                    });

            IdcsMtRoleMapperProvider provider = builder.build();
            Optional<String> token = provider.getAppToken("tenant", SecurityTracing.get().roleMapTracing("idcs"));
            String expectedAuthorization = "Basic " + Base64.getEncoder()
                    .encodeToString("client-id:client-secret".getBytes(StandardCharsets.UTF_8));

            assertThat(token.orElseThrow(), is(accessToken));
            assertThat(authorization.get(), is(expectedAuthorization));
            assertThat(host.get(), is(tenantHost + ":" + tokenServer.port()));
            assertThat(tokenEndpointCalls.get(), is(1));

            authorization.set(null);
            host.set(null);

            assertThat(provider.getAppToken("other", SecurityTracing.get().roleMapTracing("idcs")).orElseThrow(),
                       is(accessToken));
            assertThat(authorization.get(), nullValue());
            assertThat(host.get(), is(untrustedHost + ":" + tokenServer.port()));
            assertThat(tokenEndpointCalls.get(), is(2));

            resolveTokenEndpoint.set(false);

            assertThat(provider.getAppToken("tenant", SecurityTracing.get().roleMapTracing("idcs")).orElseThrow(),
                       is(accessToken));
            assertThat(tokenEndpointCalls.get(), is(2));
        } finally {
            tokenServer.stop();
        }
    }

    private static OidcConfig oidcConfig() {
        return OidcConfig.builder()
                .oidcMetadataWellKnown(false)
                .clientId("client-id")
                .clientSecret("client-secret")
                .identityUri(IDENTITY_URI)
                .tokenEndpointUri(TOKEN_ENDPOINT_URI)
                .authorizationEndpointUri(AUTHORIZATION_ENDPOINT_URI)
                .build();
    }

    private static final class CapturingMtProvider extends IdcsMtRoleMapperProvider {
        private final String appToken;
        private HttpClientRequest request;
        private Object entity;
        private String subjectName;

        private CapturingMtProvider(Builder<?> builder, String appToken) {
            super(builder);
            this.appToken = appToken;
        }

        @Override
        protected Optional<String> getAppToken(String idcsTenantId, RoleMapTracing tracing) {
            return Optional.of(appToken);
        }

        @Override
        protected List<? extends Grant> processRoleRequest(HttpClientRequest request, Object entity, String subjectName) {
            this.request = request;
            this.entity = entity;
            this.subjectName = subjectName;
            return List.of();
        }

        private HttpClientRequest request() {
            return request;
        }

        private Object entity() {
            return entity;
        }

        private String subjectName() {
            return subjectName;
        }
    }

    private static final class TestEndpoints implements IdcsMtRoleMapperProvider.MultitenancyEndpoints {
        @Override
        public String idcsInfraTenantId() {
            return "infra-tenant";
        }

        @Override
        public URI assertEndpoint(String tenantId) {
            return TENANT_ASSERT_URI;
        }

        @Override
        public URI tokenEndpoint(String tenantId) {
            return TENANT_TOKEN_URI;
        }
    }

    private static final class TrackingEndpoints implements IdcsMtRoleMapperProvider.MultitenancyEndpoints {
        private int assertEndpointCalls;
        private int tokenEndpointCalls;

        @Override
        public String idcsInfraTenantId() {
            return "infra-tenant";
        }

        @Override
        public URI assertEndpoint(String tenantId) {
            assertEndpointCalls++;
            return TENANT_ASSERT_URI;
        }

        @Override
        public URI tokenEndpoint(String tenantId) {
            tokenEndpointCalls++;
            return TENANT_TOKEN_URI;
        }
    }
}
