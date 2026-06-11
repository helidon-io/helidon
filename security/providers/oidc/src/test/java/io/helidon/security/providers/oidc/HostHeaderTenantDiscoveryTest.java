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

package io.helidon.security.providers.oidc;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

class HostHeaderTenantDiscoveryTest {
    private static final String WELL_KNOWN_PATH = "/identity/.well-known/openid-configuration";

    @Test
    void unknownHostHeadersDoNotTriggerOutboundDiscoveryOnEachCacheMiss() {
        int uniqueHosts = 25;

        try (MockIdpServer idp = new MockIdpServer()) {
            OidcProvider provider = OidcProvider.create(oidcProviderConfig(idp.identityUri()));

            assertThat("unexpected metadata fetch before traffic", idp.wellKnownHits(), is(0));

            for (int i = 0; i < uniqueHosts; i++) {
                String host = "attacker-" + i + ".example.test";
                AuthenticationResponse response = authenticate(provider, host);
                assertUnauthorized(response, "attacker request " + i);
            }

            assertThat("unknown attacker hosts should be rejected before live well-known fetch",
                       idp.wellKnownHits(),
                       is(0));
        }
    }

    @Test
    void fallbackFlagAllowsUnknownHostHeaderToUseDefaultTenant() {
        try (MockIdpServer idp = new MockIdpServer()) {
            OidcProvider provider = OidcProvider.create(oidcProviderConfig(idp.identityUri(),
                                                                           Map.of("fallback-to-default-tenant-enabled",
                                                                                  "true")));

            AuthenticationResponse response = authenticate(provider, "unknown.example.test");

            assertUnauthorized(response, "unknown tenant fallback request");
            assertThat("unknown tenant fallback should resolve default OIDC metadata", idp.wellKnownHits(), is(1));
        }
    }

    @Test
    void fallbackFlagReusesDefaultTenantForUnknownHostHeaders() {
        try (MockIdpServer idp = new MockIdpServer()) {
            OidcProvider provider = OidcProvider.create(oidcProviderConfig(idp.identityUri(),
                                                                           Map.of("fallback-to-default-tenant-enabled",
                                                                                  "true")));

            for (int i = 0; i < 25; i++) {
                String host = "unknown-" + i + ".example.test";
                AuthenticationResponse response = authenticate(provider, host);
                assertUnauthorized(response, "unknown tenant fallback request " + i);
            }

            assertThat("unknown tenant fallback should reuse default OIDC metadata", idp.wellKnownHits(), is(1));
        }
    }

    @Test
    void configuredTenantWinsWhenFallbackFlagIsEnabled() {
        String host = "configured.example.test";

        try (MockIdpServer defaultIdp = new MockIdpServer();
             MockIdpServer tenantIdp = new MockIdpServer()) {
            Map<String, String> config = Map.of("fallback-to-default-tenant-enabled", "true",
                                                "tenants.0.name", host,
                                                "tenants.0.identity-uri", tenantIdp.identityUri().toString());
            OidcProvider provider = OidcProvider.create(oidcProviderConfig(defaultIdp.identityUri(), config));

            AuthenticationResponse response = authenticate(provider, host);

            assertUnauthorized(response, "configured tenant request");
            assertThat("configured tenant should resolve tenant OIDC metadata", tenantIdp.wellKnownHits(), is(1));
            assertThat("configured tenant should not fall back to default OIDC metadata",
                       defaultIdp.wellKnownHits(),
                       is(0));
        }
    }

    @Test
    void configuredHostHeaderTenantStillResolvesTenant() {
        String host = "configured.example.test";

        try (MockIdpServer idp = new MockIdpServer()) {
            OidcProvider provider = OidcProvider.create(oidcProviderConfig(idp.identityUri(),
                                                                           Map.of("tenants.0.name", host)));

            AuthenticationResponse response = authenticate(provider, host);

            assertUnauthorized(response, "configured tenant request");
            assertThat("configured tenant should resolve OIDC metadata", idp.wellKnownHits(), is(1));
        }
    }

    @Test
    void optionalProviderUnknownHostHeaderAbstainsWithoutDiscovery() {
        try (MockIdpServer idp = new MockIdpServer()) {
            OidcProvider provider = OidcProvider.create(oidcProviderConfig(idp.identityUri(),
                                                                           Map.of("optional", "true")));

            AuthenticationResponse response = authenticate(provider, "unknown.example.test");

            assertThat("optional unknown tenant status", response.status(), is(SecurityResponse.SecurityStatus.ABSTAIN));
            assertThat("optional unknown tenant status code", response.statusCode().isEmpty(), is(true));
            assertThat("optional unknown tenant should not resolve OIDC metadata", idp.wellKnownHits(), is(0));
        }
    }

    @Test
    void unknownQueryParamTenantDoesNotTriggerDiscovery() {
        try (MockIdpServer idp = new MockIdpServer()) {
            OidcProvider provider = OidcProvider.create(oidcProviderConfig(idp.identityUri(),
                                                                           queryParamTenantConfig()));

            AuthenticationResponse response = authenticate(provider, queryParamTenantEnv("unknown"));

            assertUnauthorized(response, "unknown query-param tenant");
            assertThat("unknown query-param tenant should not resolve OIDC metadata", idp.wellKnownHits(), is(0));
        }
    }

    @Test
    void fallbackFlagAllowsUnknownQueryParamTenantToUseDefaultTenant() {
        Map<String, String> config = new HashMap<>(queryParamTenantConfig());
        config.put("fallback-to-default-tenant-enabled", "true");

        try (MockIdpServer idp = new MockIdpServer()) {
            OidcProvider provider = OidcProvider.create(oidcProviderConfig(idp.identityUri(), config));

            AuthenticationResponse response = authenticate(provider, queryParamTenantEnv("unknown"));

            assertUnauthorized(response, "unknown query-param tenant fallback request");
            assertThat("unknown query-param tenant fallback should resolve default OIDC metadata",
                       idp.wellKnownHits(),
                       is(1));
        }
    }

    @Test
    void unknownTenantCookieDoesNotTriggerDiscovery() {
        try (MockIdpServer idp = new MockIdpServer()) {
            OidcProvider provider = OidcProvider.create(oidcProviderConfig(idp.identityUri(),
                                                                           tenantCookieConfig()));

            AuthenticationResponse response = authenticate(provider, tenantCookieEnv("unknown"));

            assertUnauthorized(response, "unknown cookie tenant");
            assertThat("unknown cookie tenant should not resolve OIDC metadata", idp.wellKnownHits(), is(0));
        }
    }

    @Test
    void fallbackFlagAllowsUnknownTenantCookieToUseDefaultTenant() {
        Map<String, String> config = new HashMap<>(tenantCookieConfig());
        config.put("fallback-to-default-tenant-enabled", "true");

        try (MockIdpServer idp = new MockIdpServer()) {
            OidcProvider provider = OidcProvider.create(oidcProviderConfig(idp.identityUri(), config));

            AuthenticationResponse response = authenticate(provider, tenantCookieEnv("unknown"));

            assertUnauthorized(response, "unknown cookie tenant fallback request");
            assertThat("unknown cookie tenant fallback should resolve default OIDC metadata",
                       idp.wellKnownHits(),
                       is(1));
        }
    }

    private static AuthenticationResponse authenticate(OidcProvider provider, String hostHeader) {
        return authenticate(provider, hostHeaderEnv(hostHeader));
    }

    private static AuthenticationResponse authenticate(OidcProvider provider, SecurityEnvironment env) {
        return provider.authenticate(providerRequest(env))
                .toCompletableFuture()
                .join();
    }

    private static void assertUnauthorized(AuthenticationResponse response, String label) {
        assertThat(label + " status", response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(label + " status code", response.statusCode().orElse(-1), is(Http.Status.UNAUTHORIZED_401.code()));
        assertThat(label + " authenticate header",
                   response.responseHeaders().get(Http.Header.WWW_AUTHENTICATE),
                   is(List.of("Bearer realm=\"helidon\"")));
    }

    private static Config oidcProviderConfig(URI identityUri) {
        return oidcProviderConfig(identityUri, Map.of());
    }

    private static Config oidcProviderConfig(URI identityUri, Map<String, String> additionalConfig) {
        Map<String, String> config = new HashMap<>(Map.ofEntries(
                Map.entry("client-id", "reproducer-client"),
                Map.entry("client-secret", "reproducer-secret"),
                Map.entry("identity-uri", identityUri.toString()),
                Map.entry("redirect", "false"),
                Map.entry("header-use", "false"),
                Map.entry("query-param-use", "false"),
                Map.entry("cookie-use", "false"),
                Map.entry("multi-tenant", "true"),
                Map.entry("tenant-id-style", "host-header"),
                Map.entry("validate-jwt-with-jwk", "false"),
                Map.entry("oidc-metadata-well-known", "true"),
                Map.entry("discover-tenant-id-providers", "false"),
                Map.entry("discover-tenant-config-providers", "false")));
        config.putAll(additionalConfig);
        return Config.create(ConfigSources.create(config));
    }

    private static Map<String, String> queryParamTenantConfig() {
        return Map.of("tenant-id-style", "none",
                      "query-param-use", "true",
                      "cookie-use", "false");
    }

    private static Map<String, String> tenantCookieConfig() {
        return Map.of("tenant-id-style", "none",
                      "query-param-use", "false",
                      "cookie-use", "true",
                      "cookie-encryption-enabled", "false",
                      "cookie-name-tenant", "OIDC_TEST_TENANT");
    }

    private static SecurityEnvironment hostHeaderEnv(String hostHeader) {
        return environmentBuilder(hostHeader).build();
    }

    private static SecurityEnvironment queryParamTenantEnv(String tenantId) {
        return environmentBuilder("app.example.test")
                .queryParam("h_tenant", tenantId)
                .build();
    }

    private static SecurityEnvironment tenantCookieEnv(String tenantId) {
        return environmentBuilder("app.example.test")
                .header("Cookie", "OIDC_TEST_TENANT=" + tenantId)
                .build();
    }

    private static SecurityEnvironment.Builder environmentBuilder(String hostHeader) {
        return SecurityEnvironment.builder()
                .header("host", hostHeader)
                .targetUri(URI.create("http://" + hostHeader + "/protected"));
    }

    private static ProviderRequest providerRequest(SecurityEnvironment env) {
        ProviderRequest providerRequest = Mockito.mock(ProviderRequest.class);
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);

        when(securityContext.user()).thenReturn(Optional.empty());
        when(securityContext.service()).thenReturn(Optional.empty());
        when(securityContext.executorService()).thenReturn(ForkJoinPool.commonPool());
        when(providerRequest.env()).thenReturn(env);
        when(providerRequest.endpointConfig()).thenReturn(EndpointConfig.create());
        when(providerRequest.securityContext()).thenReturn(securityContext);
        when(providerRequest.subject()).thenReturn(Optional.empty());
        when(providerRequest.service()).thenReturn(Optional.empty());
        when(providerRequest.getObject()).thenReturn(Optional.empty());

        return providerRequest;
    }

    private static final class MockIdpServer implements AutoCloseable {
        private final AtomicInteger wellKnownHits = new AtomicInteger();
        private final String[] metadataHolder = new String[1];
        private final WebServer server;
        private final URI identityUri;

        private MockIdpServer() {
            this.server = WebServer.builder()
                    .defaultSocket(socket -> socket.host("localhost"))
                    .routing(routing -> routing.get(WELL_KNOWN_PATH, (req, res) -> {
                        wellKnownHits.incrementAndGet();
                        res.headers().contentType(MediaType.APPLICATION_JSON);
                        res.send(metadataHolder[0]);
                    }))
                    .build();
            server.start().await(Duration.ofSeconds(10));
            this.identityUri = URI.create("http://localhost:" + server.port() + "/identity");
            metadataHolder[0] = """
                    {
                        "issuer": "%s",
                        "token_endpoint": "http://localhost:%d/oauth2/v1/token",
                        "authorization_endpoint": "http://localhost:%d/oauth2/v1/authorize",
                        "end_session_endpoint": "http://localhost:%d/oauth2/v1/userlogout",
                        "introspection_endpoint": "http://localhost:%d/oauth2/v1/introspect"
                    }
                    """.formatted(identityUri, server.port(), server.port(), server.port(), server.port());
        }

        private URI identityUri() {
            return identityUri;
        }

        private int wellKnownHits() {
            return wellKnownHits.get();
        }

        @Override
        public void close() {
            server.shutdown().await(Duration.ofSeconds(10));
        }
    }
}
