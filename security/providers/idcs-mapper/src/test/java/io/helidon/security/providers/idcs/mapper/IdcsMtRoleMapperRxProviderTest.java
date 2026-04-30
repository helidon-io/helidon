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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Single;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Grant;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityException;
import io.helidon.security.Subject;
import io.helidon.security.integration.common.SecurityTracing;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdcsMtRoleMapperRxProviderTest {
    private static final URI TOKEN_ENDPOINT_URI = URI.create("https://idcs.example.com/token/endpoint/uri");
    private static final URI AUTHORIZATION_ENDPOINT_URI =
            URI.create("https://idcs.example.com/authorization/endpoint/uri");
    private static final String CLIENT_HEADER = "X-Token-Client";
    private static final String CLIENT_HEADER_VALUE = "custom-token-client";

    @Test
    void testInvalidTenantIdRejectedBeforeRoleMapping() {
        IdcsMtRoleMapperRxProvider.Builder<?> builder = IdcsMtRoleMapperRxProvider.builder();
        builder.oidcConfig(oidcConfig("https://idcs.example.com"));
        TrackingMtProvider provider = new TrackingMtProvider(builder);
        ProviderRequest request = Mockito.mock(ProviderRequest.class);
        Mockito.when(request.env())
                .thenReturn(SecurityEnvironment.builder()
                                    .targetUri(URI.create("http://service.example.test/protected"))
                                    .header(IdcsMtRoleMapperRxProvider.IDCS_TENANT_HEADER, "127.0.0.1:9999/")
                                    .header(IdcsMtRoleMapperRxProvider.IDCS_APP_HEADER, "tenant-app")
                                    .build());

        CompletionException exception = assertThrows(CompletionException.class,
                                                     () -> provider.map(request,
                                                                        AuthenticationResponse.builder()
                                                                                .user(Subject.builder()
                                                                                              .principal(Principal.create("test-user"))
                                                                                              .build())
                                                                                .build())
                                                             .await(Duration.ofSeconds(10)));

        assertThat(exception.getCause(), instanceOf(SecurityException.class));
        assertThat(provider.computeCalls(), is(0));
    }

    @Test
    void testDefaultEndpointsBuildTenantHostWithoutRegexReplacement() {
        IdcsMtRoleMapperRxProvider.DefaultMultitenancyEndpoints endpoints =
                new IdcsMtRoleMapperRxProvider.DefaultMultitenancyEndpoints(
                        oidcConfig("https://idcs.idcs.example.com"));

        URI assertEndpoint = endpoints.assertEndpoint("tenant-1");
        URI tokenEndpoint = endpoints.tokenEndpoint("tenant-1");

        assertThat(assertEndpoint, is(URI.create("https://tenant-1.idcs.example.com/admin/v1/Asserter")));
        assertThat(tokenEndpoint,
                   is(URI.create("https://tenant-1.idcs.example.com/oauth2/v1/token?IDCS_CLIENT_TENANT=idcs")));
        assertThat(endpoints.useClientCredentials("tenant-1", tokenEndpoint), is(true));
        assertThat(endpoints.useClientCredentials("tenant-1",
                                                  URI.create("https://other.idcs.example.com/oauth2/v1/token")),
                   is(false));
    }

    @Test
    void testDefaultEndpointsRejectInvalidTenantId() {
        IdcsMtRoleMapperRxProvider.DefaultMultitenancyEndpoints endpoints =
                new IdcsMtRoleMapperRxProvider.DefaultMultitenancyEndpoints(oidcConfig("https://idcs.example.com"));

        assertThrows(SecurityException.class, () -> endpoints.tokenEndpoint("evil.example.com/path"));
        assertThrows(SecurityException.class, () -> endpoints.assertEndpoint("-tenant"));
    }

    @Test
    void testJaxRsDefaultEndpointsBuildTenantHostWithoutRegexReplacement() {
        IdcsMtRoleMapperProvider.DefaultMultitenancyEndpoints endpoints =
                new IdcsMtRoleMapperProvider.DefaultMultitenancyEndpoints(oidcConfig("https://idcs.idcs.example.com"));

        WebTarget assertEndpoint = endpoints.assertEndpoint("tenant-1");
        WebTarget tokenEndpoint = endpoints.tokenEndpoint("tenant-1");

        assertThat(assertEndpoint.getUri(), is(URI.create("https://tenant-1.idcs.example.com/admin/v1/Asserter")));
        assertThat(tokenEndpoint.getUri(),
                   is(URI.create("https://tenant-1.idcs.example.com/oauth2/v1/token?IDCS_CLIENT_TENANT=idcs")));
        assertThat(endpoints.useClientCredentials("tenant-1", tokenEndpoint), is(true));
        assertThat(endpoints.useClientCredentials("tenant-1",
                                                  oidcConfig("https://idcs.idcs.example.com").appClient()
                                                          .target("https://other.idcs.example.com/oauth2/v1/token")),
                   is(false));
    }

    @Test
    void testJaxRsDefaultEndpointsRejectInvalidTenantId() {
        IdcsMtRoleMapperProvider.DefaultMultitenancyEndpoints endpoints =
                new IdcsMtRoleMapperProvider.DefaultMultitenancyEndpoints(oidcConfig("https://idcs.example.com"));

        assertThrows(SecurityException.class, () -> endpoints.tokenEndpoint("evil.example.com/path"));
        assertThrows(SecurityException.class, () -> endpoints.assertEndpoint("-tenant"));
    }

    @Test
    void testProgrammaticTenantTokenRequestUsesClientCredentials() {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> host = new AtomicReference<>();
        String accessToken = signedToken();
        WebServer tokenServer = tokenServer(authorization, host, new AtomicReference<>(), accessToken);

        try {
            String tokenHost = "127.0.0.1";
            String trustedTenant = "tenant";
            int tokenPort = tokenServer.port();
            AtomicInteger tokenEndpointCalls = new AtomicInteger();
            AtomicReference<Boolean> resolveTokenEndpoint = new AtomicReference<>(true);
            OidcConfig config = tokenOidcConfig(tokenHost, tokenPort);
            IdcsMtRoleMapperRxProvider.Builder<?> builder = IdcsMtRoleMapperRxProvider.builder();
            builder.oidcConfig(config)
                    .multitenantEndpoints(new IdcsMtRoleMapperRxProvider.MultitenancyEndpoints() {
                        @Override
                        public String idcsInfraTenantId() {
                            return "infra";
                        }

                        @Override
                        public URI assertEndpoint(String tenantId) {
                            return URI.create("http://" + tokenHost + ":" + tokenPort
                                                      + "/admin/v1/Asserter");
                        }

                        @Override
                        public URI tokenEndpoint(String tenantId) {
                            tokenEndpointCalls.incrementAndGet();
                            if (!resolveTokenEndpoint.get()) {
                                throw new SecurityException("Endpoint resolution failure");
                            }
                            return URI.create("http://" + tokenHost + ":" + tokenPort
                                                      + "/oauth2/v1/token?IDCS_CLIENT_TENANT=infra");
                        }

                        @Override
                        public boolean useClientCredentials(String tenantId, URI tokenEndpoint) {
                            return tenantId.equals(trustedTenant);
                        }
                    });

            IdcsMtRoleMapperRxProvider provider = builder.build();
            Optional<String> token = provider.getAppToken("tenant", SecurityTracing.get().roleMapTracing("idcs"))
                    .await(Duration.ofSeconds(10));
            String expectedAuthorization = "Basic " + Base64.getEncoder()
                    .encodeToString("client-id:client-secret".getBytes(StandardCharsets.UTF_8));

            assertThat(token.orElseThrow(), is(accessToken));
            assertThat(authorization.get(), is(expectedAuthorization));
            assertThat(host.get(), is(tokenHost + ":" + tokenPort));
            assertThat(tokenEndpointCalls.get(), is(1));

            authorization.set(null);
            host.set(null);

            assertThat(provider.getAppToken("other", SecurityTracing.get().roleMapTracing("idcs"))
                               .await(Duration.ofSeconds(10))
                               .orElseThrow(),
                       is(accessToken));
            assertThat(authorization.get(), nullValue());
            assertThat(host.get(), is(tokenHost + ":" + tokenPort));
            assertThat(tokenEndpointCalls.get(), is(2));

            authorization.set(null);
            host.set(null);
            resolveTokenEndpoint.set(false);

            assertThat(provider.getAppToken("tenant", SecurityTracing.get().roleMapTracing("idcs"))
                               .await(Duration.ofSeconds(10))
                               .orElseThrow(),
                       is(accessToken));
            assertThat(authorization.get(), nullValue());
            assertThat(host.get(), nullValue());
            assertThat(tokenEndpointCalls.get(), is(2));
        } finally {
            tokenServer.shutdown().await(Duration.ofSeconds(10));
        }
    }

    @Test
    void testCustomTenantTokenRequestDoesNotUseClientCredentialsByDefault() {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> host = new AtomicReference<>();
        String accessToken = signedToken();
        WebServer tokenServer = tokenServer(authorization, host, new AtomicReference<>(), accessToken);

        try {
            String tokenHost = "127.0.0.1";
            int tokenPort = tokenServer.port();
            OidcConfig config = tokenOidcConfig(tokenHost, tokenPort);
            IdcsMtRoleMapperRxProvider.Builder<?> builder = IdcsMtRoleMapperRxProvider.builder();
            builder.oidcConfig(config)
                    .multitenantEndpoints(new IdcsMtRoleMapperRxProvider.MultitenancyEndpoints() {
                        @Override
                        public String idcsInfraTenantId() {
                            return "infra";
                        }

                        @Override
                        public URI assertEndpoint(String tenantId) {
                            return URI.create("http://" + tokenHost + ":" + tokenPort
                                                      + "/admin/v1/Asserter");
                        }

                        @Override
                        public URI tokenEndpoint(String tenantId) {
                            return URI.create("http://" + tokenHost + ":" + tokenPort
                                                      + "/oauth2/v1/token?IDCS_CLIENT_TENANT=infra");
                        }
                    });

            IdcsMtRoleMapperRxProvider provider = builder.build();

            assertThat(provider.getAppToken("tenant", SecurityTracing.get().roleMapTracing("idcs"))
                               .await(Duration.ofSeconds(10))
                               .orElseThrow(),
                       is(accessToken));
            assertThat(authorization.get(), nullValue());
            assertThat(host.get(), is(tokenHost + ":" + tokenPort));

            authorization.set(null);
            host.set(null);

            IdcsMtRoleMapperProvider.Builder<?> jaxRsBuilder = IdcsMtRoleMapperProvider.builder();
            jaxRsBuilder.oidcConfig(config)
                    .multitenantEndpoints(new IdcsMtRoleMapperProvider.MultitenancyEndpoints() {
                        @Override
                        public String idcsInfraTenantId() {
                            return "infra";
                        }

                        @Override
                        public WebTarget assertEndpoint(String tenantId) {
                            return config.generalClient()
                                    .target("http://" + tokenHost + ":" + tokenPort + "/admin/v1/Asserter");
                        }

                        @Override
                        public WebTarget tokenEndpoint(String tenantId) {
                            return config.appClient()
                                    .target("http://" + tokenHost + ":" + tokenPort + "/oauth2/v1/token");
                        }
                    });

            IdcsMtRoleMapperProvider jaxRsProvider = jaxRsBuilder.build();

            assertThat(jaxRsProvider.getAppToken("tenant", SecurityTracing.get().roleMapTracing("idcs"))
                               .orElseThrow(),
                       is(accessToken));
            assertThat(authorization.get(), nullValue());
            assertThat(host.get(), is(tokenHost + ":" + tokenPort));
        } finally {
            tokenServer.shutdown().await(Duration.ofSeconds(10));
        }
    }

    @Test
    void testJaxRsProgrammaticTenantTokenRequestUsesClientCredentials() {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> host = new AtomicReference<>();
        AtomicReference<String> clientHeader = new AtomicReference<>();
        String accessToken = signedToken();
        WebServer tokenServer = tokenServer(authorization, host, clientHeader, accessToken);
        Client client = ClientBuilder.newClient();
        client.register((ClientRequestFilter) requestContext ->
                requestContext.getHeaders().putSingle(CLIENT_HEADER, CLIENT_HEADER_VALUE));

        try {
            String tokenHost = "127.0.0.1";
            String trustedTenant = "tenant";
            int tokenPort = tokenServer.port();
            AtomicInteger tokenEndpointCalls = new AtomicInteger();
            AtomicReference<Boolean> resolveTokenEndpoint = new AtomicReference<>(true);
            OidcConfig config = tokenOidcConfig(tokenHost, tokenPort);
            IdcsMtRoleMapperProvider.Builder<?> builder = IdcsMtRoleMapperProvider.builder();
            builder.oidcConfig(config)
                    .multitenantEndpoints(new IdcsMtRoleMapperProvider.MultitenancyEndpoints() {
                        @Override
                        public String idcsInfraTenantId() {
                            return "infra";
                        }

                        @Override
                        public WebTarget assertEndpoint(String tenantId) {
                            return client.target("http://" + tokenHost + ":" + tokenPort
                                                         + "/admin/v1/Asserter");
                        }

                        @Override
                        public WebTarget tokenEndpoint(String tenantId) {
                            tokenEndpointCalls.incrementAndGet();
                            if (!resolveTokenEndpoint.get()) {
                                throw new SecurityException("Endpoint resolution failure");
                            }
                            return client.target("http://" + tokenHost + ":" + tokenPort
                                                         + "/oauth2/v1/token?IDCS_CLIENT_TENANT=infra");
                        }

                        @Override
                        public boolean useClientCredentials(String tenantId, WebTarget tokenEndpoint) {
                            return tenantId.equals(trustedTenant);
                        }
                    });

            IdcsMtRoleMapperProvider provider = builder.build();
            Optional<String> token = provider.getAppToken("tenant", SecurityTracing.get().roleMapTracing("idcs"));
            String expectedAuthorization = "Basic " + Base64.getEncoder()
                    .encodeToString("client-id:client-secret".getBytes(StandardCharsets.UTF_8));

            assertThat(token.orElseThrow(), is(accessToken));
            assertThat(authorization.get(), is(expectedAuthorization));
            assertThat(host.get(), is(tokenHost + ":" + tokenPort));
            assertThat(clientHeader.get(), is(CLIENT_HEADER_VALUE));
            assertThat(tokenEndpointCalls.get(), is(1));

            authorization.set(null);
            host.set(null);
            clientHeader.set(null);

            assertThat(provider.getAppToken("other", SecurityTracing.get().roleMapTracing("idcs")).orElseThrow(),
                       is(accessToken));
            assertThat(authorization.get(), nullValue());
            assertThat(host.get(), is(tokenHost + ":" + tokenPort));
            assertThat(clientHeader.get(), is(CLIENT_HEADER_VALUE));
            assertThat(tokenEndpointCalls.get(), is(2));

            authorization.set(null);
            host.set(null);
            clientHeader.set(null);
            resolveTokenEndpoint.set(false);

            assertThat(provider.getAppToken("tenant", SecurityTracing.get().roleMapTracing("idcs")).orElseThrow(),
                       is(accessToken));
            assertThat(authorization.get(), nullValue());
            assertThat(host.get(), nullValue());
            assertThat(clientHeader.get(), nullValue());
            assertThat(tokenEndpointCalls.get(), is(2));
        } finally {
            client.close();
            tokenServer.shutdown().await(Duration.ofSeconds(10));
        }
    }

    private static OidcConfig oidcConfig(String identityUri) {
        return OidcConfig.builder()
                .oidcMetadataWellKnown(false)
                .clientId("client-id")
                .clientSecret("client-secret")
                .identityUri(URI.create(identityUri))
                .tokenEndpointUri(TOKEN_ENDPOINT_URI)
                .authorizationEndpointUri(AUTHORIZATION_ENDPOINT_URI)
                .build();
    }

    private static OidcConfig tokenOidcConfig(String tokenHost, int tokenPort) {
        return OidcConfig.builder()
                .oidcMetadataWellKnown(false)
                .clientId("client-id")
                .clientSecret("client-secret")
                .identityUri(URI.create("http://" + tokenHost + ":" + tokenPort))
                .tokenEndpointUri(URI.create("http://" + tokenHost + ":" + tokenPort + "/oauth2/v1/token"))
                .authorizationEndpointUri(URI.create("http://" + tokenHost + ":" + tokenPort + "/oauth2/v1/authorize"))
                .validateJwtWithJwk(false)
                .build();
    }

    private static WebServer tokenServer(AtomicReference<String> authorization,
                                         AtomicReference<String> host,
                                         AtomicReference<String> clientHeader,
                                         String accessToken) {
        WebServer tokenServer = WebServer.builder()
                .host("127.0.0.1")
                .routing(Routing.builder()
                                 .post("/oauth2/v1/token", (req, res) -> {
                                     authorization.set(req.headers()
                                                               .first(Http.Header.AUTHORIZATION)
                                                               .orElse(null));
                                     host.set(req.headers()
                                                      .first(Http.Header.HOST)
                                                      .orElse(null));
                                     clientHeader.set(req.headers()
                                                              .first(CLIENT_HEADER)
                                                              .orElse(null));
                                     res.headers().contentType(MediaType.APPLICATION_JSON);
                                     res.send("{\"access_token\":\"" + accessToken + "\"}");
                                 }))
                .build();
        tokenServer.start().await(Duration.ofSeconds(10));
        return tokenServer;
    }

    private static String signedToken() {
        Instant issueTime = Instant.now();
        Jwt jwt = Jwt.builder()
                .algorithm("none")
                .issuer("unit-test")
                .issueTime(issueTime)
                .expirationTime(issueTime.plusSeconds(3600))
                .build();

        return SignedJwt.sign(jwt, Jwk.NONE_JWK).tokenContent();
    }

    private static final class TrackingMtProvider extends IdcsMtRoleMapperRxProvider {
        private int computeCalls;

        private TrackingMtProvider(Builder<?> builder) {
            super(builder);
        }

        @Override
        protected Single<List<? extends Grant>> computeGrants(String idcsTenantId,
                                                              String idcsAppName,
                                                              Subject subject) {
            computeCalls++;
            return Single.just(List.of());
        }

        private int computeCalls() {
            return computeCalls;
        }
    }
}
