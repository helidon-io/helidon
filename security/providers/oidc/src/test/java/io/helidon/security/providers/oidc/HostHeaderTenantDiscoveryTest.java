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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.json.JsonObject;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
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
                AuthenticationResponse response = provider.authenticate(providerRequest(host));
                assertUnauthorized(response, "attacker request " + i);
            }

            int hitsAfterAttack = idp.wellKnownHits();

            assertThat("unknown attacker hosts should be rejected before live well-known fetch",
                       hitsAfterAttack,
                       is(0));
        }
    }

    @Test
    void fallbackFlagAllowsUnknownHostHeaderToUseDefaultTenant() {
        try (MockIdpServer idp = new MockIdpServer()) {
            Map<String, String> config = Map.of("fallback-to-default-tenant-enabled", "true");
            OidcProvider provider = OidcProvider.create(oidcProviderConfig(idp.identityUri(), config));

            AuthenticationResponse response = provider.authenticate(providerRequest("unknown.example.test"));

            assertUnauthorized(response, "unknown tenant fallback request");
            assertThat("unknown tenant fallback should resolve default OIDC metadata", idp.wellKnownHits(), is(1));
        }
    }

    @Test
    void fallbackFlagReusesDefaultTenantForUnknownHostHeaders() {
        try (MockIdpServer idp = new MockIdpServer()) {
            Map<String, String> config = Map.of("fallback-to-default-tenant-enabled", "true");
            OidcProvider provider = OidcProvider.create(oidcProviderConfig(idp.identityUri(), config));

            for (int i = 0; i < 25; i++) {
                String host = "unknown-" + i + ".example.test";
                AuthenticationResponse response = provider.authenticate(providerRequest(host));
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

            AuthenticationResponse response = provider.authenticate(providerRequest(host));

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

            AuthenticationResponse response = provider.authenticate(providerRequest(host));

            assertUnauthorized(response, "configured tenant request");
            assertThat("configured tenant should resolve OIDC metadata",
                       idp.wellKnownHits(),
                       is(1));
        }
    }

    @Test
    void optionalProviderUnknownHostHeaderAbstainsWithoutDiscovery() {
        try (MockIdpServer idp = new MockIdpServer()) {
            OidcProvider provider = OidcProvider.create(oidcProviderConfig(idp.identityUri(),
                                                                           Map.of("optional", "true")));

            AuthenticationResponse response = provider.authenticate(providerRequest("unknown.example.test"));

            assertThat("optional unknown tenant status",
                       response.status(),
                       is(SecurityResponse.SecurityStatus.ABSTAIN));
            assertThat("optional unknown tenant status code", response.statusCode().isEmpty(), is(true));
            assertThat("optional unknown tenant should not resolve OIDC metadata", idp.wellKnownHits(), is(0));
        }
    }

    @Test
    void unknownRedirectTenantDoesNotTriggerOutboundDiscovery() {
        try (MockIdpServer idp = new MockIdpServer();
             FeatureServer server = new FeatureServer(
                     oidcProviderConfig(idp.identityUri(), Map.of("cookie-encryption-state-enabled", "false")))) {

            try (HttpClientResponse response = server.client()
                    .get()
                    .path("/oidc/redirect")
                    .queryParam("code", "test-code")
                    .queryParam("state", "test-state")
                    .queryParam("h_tenant", "unknown.example.test")
                    .header(HeaderNames.COOKIE, stateCookie("test-state"))
                    .request()) {
                assertThat("unknown redirect tenant status", response.status(), is(Status.UNAUTHORIZED_401));
            }

            assertThat("unknown redirect tenant should not resolve OIDC metadata", idp.wellKnownHits(), is(0));
        }
    }

    @Test
    void fallbackFlagReusesDefaultTenantForUnknownRedirectTenants() {
        Map<String, String> config = Map.of("cookie-encryption-state-enabled", "false",
                                            "fallback-to-default-tenant-enabled", "true");

        try (MockIdpServer idp = new MockIdpServer();
             FeatureServer server = new FeatureServer(oidcProviderConfig(idp.identityUri(), config))) {

            for (int i = 0; i < 25; i++) {
                String state = "test-state-" + i;
                try (HttpClientResponse response = server.client()
                        .get()
                        .path("/oidc/redirect")
                        .queryParam("code", "test-code")
                        .queryParam("state", state)
                        .queryParam("h_tenant", "unknown-" + i + ".example.test")
                        .header(HeaderNames.COOKIE, stateCookie(state))
                        .request()) {
                    assertThat("unknown redirect fallback tenant status",
                               response.status(),
                               is(Status.UNAUTHORIZED_401));
                }
            }

            assertThat("unknown redirect fallback should reuse default OIDC metadata", idp.wellKnownHits(), is(1));
        }
    }

    @Test
    void configuredRedirectTenantStillResolvesTenant() {
        String tenantId = "configured";

        try (MockIdpServer idp = new MockIdpServer();
             FeatureServer server = new FeatureServer(
                     oidcProviderConfig(idp.identityUri(),
                                        Map.of("cookie-encryption-state-enabled", "false",
                                               "tenants.0.name", tenantId)))) {

            try (HttpClientResponse response = server.client()
                    .get()
                    .path("/oidc/redirect")
                    .queryParam("code", "test-code")
                    .queryParam("state", "test-state")
                    .queryParam("h_tenant", tenantId)
                    .header(HeaderNames.COOKIE, stateCookie("test-state"))
                    .request()) {
                assertThat("configured redirect tenant status", response.status(), is(Status.UNAUTHORIZED_401));
            }

            assertThat("configured redirect tenant should resolve OIDC metadata", idp.wellKnownHits(), is(1));
        }
    }

    @Test
    void unknownLogoutTenantDoesNotTriggerOutboundDiscovery() {
        try (MockIdpServer idp = new MockIdpServer();
             FeatureServer server = new FeatureServer(oidcProviderConfig(idp.identityUri(),
                                                                         Map.of("logout-enabled", "true",
                                                                                "cookie-use", "true",
                                                                                "cookie-encryption-tenant-enabled", "false",
                                                                                "post-logout-uri", "/logged-out")))) {

            try (HttpClientResponse response = server.client()
                    .get()
                    .path("/oidc/logout")
                    .header(HeaderNames.COOKIE,
                            OidcConfig.DEFAULT_COOKIE_NAME + "=bogus; "
                                    + OidcConfig.DEFAULT_ID_COOKIE_NAME + "=bogus; "
                                    + OidcConfig.DEFAULT_TENANT_COOKIE_NAME + "=unknown.example.test; "
                                    + OidcConfig.DEFAULT_REFRESH_COOKIE_NAME + "=bogus")
                    .request()) {
                assertThat("unknown logout tenant status", response.status(), is(Status.UNAUTHORIZED_401));
                List<String> cookies = response.headers().get(HeaderNames.SET_COOKIE).allValues();
                assertRemoveCookie(cookies, OidcConfig.DEFAULT_COOKIE_NAME);
                assertRemoveCookie(cookies, OidcConfig.DEFAULT_ID_COOKIE_NAME);
                assertRemoveCookie(cookies, OidcConfig.DEFAULT_TENANT_COOKIE_NAME);
                assertRemoveCookie(cookies, OidcConfig.DEFAULT_REFRESH_COOKIE_NAME);
            }

            assertThat("unknown logout tenant should not resolve OIDC metadata", idp.wellKnownHits(), is(0));
        }
    }

    private static void assertRemoveCookie(List<String> cookies, String cookieName) {
        assertThat("remove cookie " + cookieName,
                   cookies.stream().anyMatch(cookie -> cookie.startsWith(cookieName + "=;")),
                   is(true));
    }

    private static void assertUnauthorized(AuthenticationResponse response, String label) {
        assertThat(label + " status", response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(label + " status code", response.statusCode().orElseThrow(), is(401));
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

    private static String stateCookie(String state) {
        JsonObject stateJson = JsonObject.builder()
                .set("originalUri", "/test")
                .set("state", state)
                .build();
        String encoded = Base64.getEncoder()
                .encodeToString(stateJson.toString().getBytes(StandardCharsets.UTF_8));
        return OidcConfig.DEFAULT_STATE_COOKIE_NAME + "=" + encoded;
    }

    private static ProviderRequest providerRequest(String hostHeader) {
        ProviderRequest providerRequest = Mockito.mock(ProviderRequest.class);
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        SecurityEnvironment env = SecurityEnvironment.builder()
                .header("host", hostHeader)
                .targetUri(URI.create("http://" + hostHeader + "/protected"))
                .build();

        when(securityContext.user()).thenReturn(Optional.empty());
        when(securityContext.service()).thenReturn(Optional.empty());
        when(providerRequest.env()).thenReturn(env);
        when(providerRequest.endpointConfig()).thenReturn(EndpointConfig.create());
        when(providerRequest.securityContext()).thenReturn(securityContext);
        when(providerRequest.subject()).thenReturn(Optional.empty());
        when(providerRequest.service()).thenReturn(Optional.empty());
        when(providerRequest.getObject()).thenReturn(Optional.empty());

        return providerRequest;
    }

    private static final class FeatureServer implements AutoCloseable {
        private final WebServer server;
        private final WebClient client;

        private FeatureServer(Config config) {
            OidcFeature feature = OidcFeature.builder()
                    .config(OidcConfig.create(config))
                    .discoverTenantConfigProviders(false)
                    .build();
            this.server = WebServer.builder()
                    .featuresDiscoverServices(false)
                    .routing(routing -> routing.addFeature(feature))
                    .build()
                    .start();
            this.client = WebClient.builder()
                    .servicesDiscoverServices(false)
                    .baseUri("http://127.0.0.1:" + server.port())
                    .build();
        }

        private WebClient client() {
            return client;
        }

        @Override
        public void close() {
            server.stop();
        }
    }

    private static final class MockIdpServer implements AutoCloseable {
        private final AtomicInteger wellKnownHits = new AtomicInteger();
        private final JsonObject[] metadataHolder = new JsonObject[1];
        private final WebServer server;
        private final URI identityUri;

        private MockIdpServer() {
            this.server = WebServer.builder()
                    .host("127.0.0.1")
                    .routing(routing -> routing.get(WELL_KNOWN_PATH, (req, res) -> {
                        wellKnownHits.incrementAndGet();
                        res.send(metadataHolder[0]);
                    }))
                    .build()
                    .start();
            this.identityUri = URI.create("http://127.0.0.1:" + server.port() + "/identity");
            metadataHolder[0] = JsonObject.builder()
                    .set("issuer", identityUri.toString())
                    .set("token_endpoint", "http://127.0.0.1:" + server.port() + "/oauth2/v1/token")
                    .set("authorization_endpoint", "http://127.0.0.1:" + server.port() + "/oauth2/v1/authorize")
                    .set("end_session_endpoint", "http://127.0.0.1:" + server.port() + "/oauth2/v1/userlogout")
                    .set("introspection_endpoint", "http://127.0.0.1:" + server.port() + "/oauth2/v1/introspect")
                    .build();
        }

        private URI identityUri() {
            return identityUri;
        }

        private int wellKnownHits() {
            return wellKnownHits.get();
        }

        @Override
        public void close() {
            server.stop();
        }
    }
}
