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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
import io.helidon.security.providers.oidc.common.TenantConfig;
import io.helidon.security.providers.oidc.common.spi.TenantConfigFinder;
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
    private static final String JWK_JSON = """
            {"keys":[{"kty":"oct","kid":"test-key","alg":"HS256",
                      "key_ops":["sign","verify"],
                      "k":"FdFYFzERwC2uCBB46pZQi4GG85LujR8obt-KWRBICVQ"}]}
            """;

    @Test
    void unknownHostHeadersDoNotTriggerOutboundDiscoveryOnEachCacheMiss() {
        int uniqueHosts = 25;

        try (MockIdpServer idp = new MockIdpServer()) {
            OidcProvider provider = oidcProvider(oidcProviderConfig(idp.identityUri()));

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
            OidcProvider provider = oidcProvider(oidcProviderConfig(idp.identityUri(), config));

            AuthenticationResponse response = provider.authenticate(providerRequest("unknown.example.test"));

            assertUnauthorized(response, "unknown tenant fallback request");
            assertThat("unknown tenant fallback should resolve default OIDC metadata", idp.wellKnownHits(), is(1));
        }
    }

    @Test
    void fallbackFlagReusesDefaultTenantForUnknownHostHeaders() {
        try (MockIdpServer idp = new MockIdpServer()) {
            Map<String, String> config = Map.of("fallback-to-default-tenant-enabled", "true");
            OidcProvider provider = oidcProvider(oidcProviderConfig(idp.identityUri(), config));

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
            OidcProvider provider = oidcProvider(oidcProviderConfig(defaultIdp.identityUri(), config));

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
            OidcProvider provider = oidcProvider(oidcProviderConfig(idp.identityUri(),
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
            OidcProvider provider = oidcProvider(oidcProviderConfig(idp.identityUri(),
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
    void unavailableTenantFailsRequiredAuthenticationAndOpensCircuit() {
        try (MockIdpServer idp = new MockIdpServer()) {
            idp.metadataAvailable(false);
            OidcProvider provider = oidcProvider(oidcProviderConfig(idp.identityUri(),
                                                                    oneAttemptLoaderConfig()));

            AuthenticationResponse first = provider.authenticate(providerRequest("default.example.test"));
            AuthenticationResponse second = provider.authenticate(providerRequest("default.example.test"));

            assertUnauthorized(first, "first unavailable tenant request");
            assertUnauthorized(second, "open circuit tenant request");
            assertThat("unavailable response description",
                       first.description().orElseThrow(),
                       is("Tenant configuration is temporarily unavailable"));
            assertThat("only the first request should attempt discovery", idp.wellKnownHits(), is(1));
            assertThat("unavailable response must not redirect", first.responseHeaders().isEmpty(), is(true));
        }
    }

    @Test
    void unavailableJwkUsesAuthenticationOutcomeAndOpensCircuit() {
        try (MockIdpServer idp = new MockIdpServer()) {
            idp.jwkAvailable(false);
            Map<String, String> requiredConfig = new HashMap<>(oneAttemptLoaderConfig());
            requiredConfig.put("validate-jwt-with-jwk", "true");
            OidcProvider requiredProvider = oidcProvider(oidcProviderConfig(idp.identityUri(), requiredConfig));

            AuthenticationResponse first = requiredProvider.authenticate(providerRequest("default.example.test"));
            AuthenticationResponse second = requiredProvider.authenticate(providerRequest("default.example.test"));

            assertUnauthorized(first, "first unavailable JWK request");
            assertUnauthorized(second, "open JWK circuit request");
            assertThat("only the first required request should fetch JWK", idp.jwkHits(), is(1));

            Map<String, String> optionalConfig = new HashMap<>(requiredConfig);
            optionalConfig.put("optional", "true");
            OidcProvider optionalProvider = oidcProvider(oidcProviderConfig(idp.identityUri(), optionalConfig));
            AuthenticationResponse optional = optionalProvider.authenticate(providerRequest("default.example.test"));

            assertThat("optional unavailable JWK status",
                       optional.status(),
                       is(SecurityResponse.SecurityStatus.ABSTAIN));
            assertThat("optional unavailable JWK status code", optional.statusCode().isEmpty(), is(true));
            assertThat("optional provider should have its own JWK circuit", idp.jwkHits(), is(2));
        }
    }

    @Test
    void concurrentFirstRequestsShareOneTenantLoader() throws InterruptedException {
        try (MockIdpServer idp = new MockIdpServer()) {
            idp.blockMetadataResponse();
            OidcProvider provider = oidcProvider(oidcProviderConfig(idp.identityUri(),
                                                                    oneAttemptLoaderConfig()));
            AtomicReference<AuthenticationResponse> firstResponse = new AtomicReference<>();
            AtomicReference<AuthenticationResponse> secondResponse = new AtomicReference<>();
            AtomicReference<Throwable> firstFailure = new AtomicReference<>();
            AtomicReference<Throwable> secondFailure = new AtomicReference<>();
            CountDownLatch firstComplete = new CountDownLatch(1);
            CountDownLatch secondComplete = new CountDownLatch(1);

            Thread firstRequest = authenticateAsync(provider,
                                                    firstResponse,
                                                    firstFailure,
                                                    firstComplete);
            Thread secondRequest = null;
            boolean metadataRequested;
            boolean secondWaitingBeforeRelease = false;
            int hitsBeforeRelease;
            try {
                metadataRequested = idp.awaitMetadataRequest();
                if (metadataRequested) {
                    secondRequest = authenticateAsync(provider,
                                                      secondResponse,
                                                      secondFailure,
                                                      secondComplete);
                    awaitWaiting(secondRequest);
                    secondWaitingBeforeRelease = secondComplete.getCount() == 1;
                }
                hitsBeforeRelease = idp.wellKnownHits();
            } finally {
                idp.releaseMetadataResponse();
                firstRequest.join(TimeUnit.SECONDS.toMillis(5));
                if (secondRequest != null) {
                    secondRequest.join(TimeUnit.SECONDS.toMillis(5));
                }
            }

            assertThat("the first request should reach metadata discovery", metadataRequested, is(true));
            assertThat("the concurrent follower should wait for the active load", secondWaitingBeforeRelease, is(true));
            assertThat("only one request should load metadata", hitsBeforeRelease, is(1));
            assertThat("first request thread should complete", firstRequest.isAlive(), is(false));
            assertThat("second request thread should complete", secondRequest.isAlive(), is(false));
            assertThat("first request failure", firstFailure.get(), is((Throwable) null));
            assertThat("second request failure", secondFailure.get(), is((Throwable) null));
            assertUnauthorized(firstResponse.get(), "first concurrent request");
            assertUnauthorized(secondResponse.get(), "second concurrent request");
        }
    }

    @Test
    void unavailableTenantAbstainsForOptionalAuthenticationWithoutRedirect() {
        try (MockIdpServer idp = new MockIdpServer()) {
            idp.metadataAvailable(false);
            Map<String, String> config = new HashMap<>(oneAttemptLoaderConfig());
            config.put("optional", "true");
            OidcProvider provider = oidcProvider(oidcProviderConfig(idp.identityUri(), config));

            AuthenticationResponse response = provider.authenticate(providerRequest("default.example.test"));

            assertThat("optional unavailable tenant status",
                       response.status(),
                       is(SecurityResponse.SecurityStatus.ABSTAIN));
            assertThat("optional unavailable tenant status code", response.statusCode().isEmpty(), is(true));
            assertThat("optional unavailable tenant response must not redirect",
                       response.responseHeaders().isEmpty(),
                       is(true));
            assertThat("optional request should attempt discovery once", idp.wellKnownHits(), is(1));
        }
    }

    @Test
    void unavailableTenantRecoversOnLaterRequest() {
        try (MockIdpServer idp = new MockIdpServer()) {
            idp.metadataAvailable(false);
            Map<String, String> config = new HashMap<>(oneAttemptLoaderConfig());
            config.put("jwk-loader.circuit-breaker.volume", "2");
            OidcProvider provider = oidcProvider(oidcProviderConfig(idp.identityUri(), config));

            AuthenticationResponse unavailable = provider.authenticate(providerRequest("default.example.test"));
            idp.metadataAvailable(true);
            AuthenticationResponse recovered = provider.authenticate(providerRequest("default.example.test"));
            provider.authenticate(providerRequest("default.example.test"));

            assertUnauthorized(unavailable, "unavailable tenant request");
            assertUnauthorized(recovered, "recovered tenant request without credentials");
            assertThat("successful tenant load should be cached", idp.wellKnownHits(), is(2));
        }
    }

    @Test
    void unavailableTenantDoesNotOpenAnotherTenantsCircuit() {
        String unavailableTenant = "unavailable.example.test";
        String availableTenant = "available.example.test";

        try (MockIdpServer unavailableIdp = new MockIdpServer();
             MockIdpServer availableIdp = new MockIdpServer()) {
            unavailableIdp.metadataAvailable(false);
            Map<String, String> config = new HashMap<>(oneAttemptLoaderConfig());
            config.put("tenants.0.name", unavailableTenant);
            config.put("tenants.0.identity-uri", unavailableIdp.identityUri().toString());
            config.put("tenants.1.name", availableTenant);
            config.put("tenants.1.identity-uri", availableIdp.identityUri().toString());
            OidcProvider provider = oidcProvider(oidcProviderConfig(availableIdp.identityUri(), config));

            provider.authenticate(providerRequest(unavailableTenant));
            provider.authenticate(providerRequest(availableTenant));

            assertThat("unavailable tenant discovery attempts", unavailableIdp.wellKnownHits(), is(1));
            assertThat("available tenant should have an independent circuit", availableIdp.wellKnownHits(), is(1));
        }
    }

    @Test
    void tenantChangeRemovesOpenCircuit() {
        String tenantId = "changing.example.test";

        try (MockIdpServer unavailableIdp = new MockIdpServer();
             MockIdpServer availableIdp = new MockIdpServer()) {
            unavailableIdp.metadataAvailable(false);
            MutableTenantConfigFinder configFinder = new MutableTenantConfigFinder(
                    tenantId,
                    tenantConfig(unavailableIdp.identityUri()));
            Config config = oidcProviderConfig(availableIdp.identityUri(), oneAttemptLoaderConfig());
            OidcProvider provider = OidcProvider.builder()
                    .oidcConfig(oidcConfig(config))
                    .config(config)
                    .discoverTenantConfigProviders(false)
                    .addTenantConfigFinder(configFinder)
                    .build();

            provider.authenticate(providerRequest(tenantId));
            configFinder.update(tenantConfig(availableIdp.identityUri()));
            provider.authenticate(providerRequest(tenantId));

            assertThat("original configuration discovery attempts", unavailableIdp.wellKnownHits(), is(1));
            assertThat("updated configuration should install a new circuit", availableIdp.wellKnownHits(), is(1));
        }
    }

    @Test
    void tenantChangeDuringProviderResolutionDoesNotInstallStaleConfiguration() throws InterruptedException {
        String tenantId = "default.example.test";

        try (MockIdpServer staleIdp = new MockIdpServer();
             MockIdpServer currentIdp = new MockIdpServer()) {
            MutableTenantConfigFinder configFinder = new MutableTenantConfigFinder(
                    tenantId,
                    tenantConfig(staleIdp.identityUri()));
            Config config = oidcProviderConfig(currentIdp.identityUri(), oneAttemptLoaderConfig());
            OidcProvider provider = OidcProvider.builder()
                    .oidcConfig(oidcConfig(config))
                    .config(config)
                    .discoverTenantConfigProviders(false)
                    .addTenantConfigFinder(configFinder)
                    .build();
            AtomicReference<AuthenticationResponse> response = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch complete = new CountDownLatch(1);
            configFinder.blockConfigResponse();

            Thread request = authenticateAsync(provider, response, failure, complete);
            boolean configRequested;
            try {
                configRequested = configFinder.awaitConfigRequest();
                if (configRequested) {
                    configFinder.update(tenantConfig(currentIdp.identityUri()));
                }
            } finally {
                configFinder.releaseConfigResponse();
                request.join(TimeUnit.SECONDS.toMillis(5));
            }

            assertThat("tenant config should be requested", configRequested, is(true));
            assertThat("authentication should complete", request.isAlive(), is(false));
            assertThat("authentication failure", failure.get(), is((Throwable) null));
            assertUnauthorized(response.get(), "request using concurrently updated tenant");
            assertThat("stale tenant configuration must not be loaded", staleIdp.wellKnownHits(), is(0));
            assertThat("updated tenant configuration should be loaded", currentIdp.wellKnownHits(), is(1));
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
    void unavailableRedirectTenantReturnsUnauthorized() {
        try (MockIdpServer idp = new MockIdpServer()) {
            idp.metadataAvailable(false);
            Map<String, String> config = new HashMap<>(oneAttemptLoaderConfig());
            config.put("cookie-encryption-state-enabled", "false");
            try (FeatureServer server = new FeatureServer(oidcProviderConfig(idp.identityUri(), config));
                 HttpClientResponse response = server.client()
                         .get()
                         .path("/oidc/redirect")
                         .queryParam("code", "test-code")
                         .queryParam("state", "test-state")
                         .header(HeaderNames.COOKIE, stateCookie("test-state"))
                         .request()) {
                assertThat("unavailable redirect tenant status", response.status(), is(Status.UNAUTHORIZED_401));
                assertThat("unavailable tenant must not redirect",
                           response.headers().contains(HeaderNames.LOCATION),
                           is(false));
            }

            assertThat("feature should attempt discovery once", idp.wellKnownHits(), is(1));
        }
    }

    @Test
    void tenantChangeDuringFeatureResolutionDoesNotInstallStaleConfiguration() throws InterruptedException {
        String tenantId = "changing";

        try (MockIdpServer staleIdp = new MockIdpServer();
             MockIdpServer currentIdp = new MockIdpServer()) {
            MutableTenantConfigFinder configFinder = new MutableTenantConfigFinder(
                    tenantId,
                    tenantConfig(staleIdp.identityUri()));
            Map<String, String> additionalConfig = new HashMap<>(oneAttemptLoaderConfig());
            additionalConfig.put("cookie-encryption-state-enabled", "false");
            Config config = oidcProviderConfig(currentIdp.identityUri(), additionalConfig);
            AtomicReference<Status> responseStatus = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            configFinder.blockConfigResponse();

            try (FeatureServer server = new FeatureServer(config, configFinder)) {
                Thread request = Thread.startVirtualThread(() -> {
                    try (HttpClientResponse response = server.client()
                            .get()
                            .path("/oidc/redirect")
                            .queryParam("code", "test-code")
                            .queryParam("state", "test-state")
                            .queryParam("h_tenant", tenantId)
                            .header(HeaderNames.COOKIE, stateCookie("test-state"))
                            .request()) {
                        responseStatus.set(response.status());
                    } catch (Throwable t) {
                        failure.set(t);
                    }
                });
                boolean configRequested;
                try {
                    configRequested = configFinder.awaitConfigRequest();
                    if (configRequested) {
                        configFinder.update(tenantConfig(currentIdp.identityUri()));
                    }
                } finally {
                    configFinder.releaseConfigResponse();
                    request.join(TimeUnit.SECONDS.toMillis(5));
                }

                assertThat("tenant config should be requested", configRequested, is(true));
                assertThat("feature request should complete", request.isAlive(), is(false));
                assertThat("feature request failure", failure.get(), is((Throwable) null));
                assertThat("feature response", responseStatus.get(), is(Status.UNAUTHORIZED_401));
            }

            assertThat("stale feature tenant configuration must not be loaded", staleIdp.wellKnownHits(), is(0));
            assertThat("updated feature tenant configuration should be loaded", currentIdp.wellKnownHits(), is(1));
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

    private static OidcProvider oidcProvider(Config config) {
        return OidcProvider.builder()
                .oidcConfig(oidcConfig(config))
                .config(config)
                .build();
    }

    private static OidcConfig oidcConfig(Config config) {
        return OidcConfig.builder()
                .config(config)
                .webclient(it -> it.clearServices().servicesDiscoverServices(false))
                .build();
    }

    private static Map<String, String> oneAttemptLoaderConfig() {
        return Map.of("jwk-loader.retry.calls", "1",
                      "jwk-loader.retry.delay", "PT0S",
                      "jwk-loader.retry.overall-timeout", "PT1S",
                      "jwk-loader.timeout.timeout", "PT1S",
                      "jwk-loader.circuit-breaker.volume", "1",
                      "jwk-loader.circuit-breaker.error-ratio", "100",
                      "fallback-to-default-tenant-enabled", "true");
    }

    private static OidcConfig tenantConfig(URI identityUri) {
        return oidcConfig(oidcProviderConfig(identityUri, oneAttemptLoaderConfig()));
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

    private static Thread authenticateAsync(OidcProvider provider,
                                            AtomicReference<AuthenticationResponse> response,
                                            AtomicReference<Throwable> failure,
                                            CountDownLatch complete) {
        return Thread.startVirtualThread(() -> {
            try {
                response.set(provider.authenticate(providerRequest("default.example.test")));
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                complete.countDown();
            }
        });
    }

    private static void awaitWaiting(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (thread.isAlive() && thread.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertThat("concurrent request state", thread.getState(), is(Thread.State.WAITING));
    }

    private static final class FeatureServer implements AutoCloseable {
        private final WebServer server;
        private final WebClient client;

        private FeatureServer(Config config) {
            this(config, null);
        }

        private FeatureServer(Config config, TenantConfigFinder tenantConfigFinder) {
            OidcFeature.Builder featureBuilder = OidcFeature.builder()
                    .config(oidcConfig(config))
                    .discoverTenantConfigProviders(false);
            if (tenantConfigFinder != null) {
                featureBuilder.addTenantConfigFinder(tenantConfigFinder);
            }
            OidcFeature feature = featureBuilder.build();
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
        private final AtomicInteger jwkHits = new AtomicInteger();
        private final CountDownLatch metadataRequest = new CountDownLatch(1);
        private final JsonObject[] metadataHolder = new JsonObject[1];
        private volatile boolean metadataAvailable = true;
        private volatile boolean jwkAvailable = true;
        private volatile CountDownLatch metadataResponseGate;
        private final WebServer server;
        private final URI identityUri;

        private MockIdpServer() {
            this.server = WebServer.builder()
                    .featuresDiscoverServices(false)
                    .host("127.0.0.1")
                    .routing(routing -> routing
                            .get(WELL_KNOWN_PATH, (req, res) -> {
                                wellKnownHits.incrementAndGet();
                                metadataRequest.countDown();
                                CountDownLatch responseGate = metadataResponseGate;
                                if (responseGate != null) {
                                    try {
                                        responseGate.await();
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        throw new IllegalStateException("Interrupted while blocking metadata response", e);
                                    }
                                }
                                if (metadataAvailable) {
                                    res.send(metadataHolder[0]);
                                } else {
                                    res.status(Status.SERVICE_UNAVAILABLE_503).send();
                                }
                            })
                            .get("/jwks", (req, res) -> {
                                jwkHits.incrementAndGet();
                                if (jwkAvailable) {
                                    res.send(JWK_JSON);
                                } else {
                                    res.status(Status.SERVICE_UNAVAILABLE_503).send();
                                }
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
                    .set("jwks_uri", "http://127.0.0.1:" + server.port() + "/jwks")
                    .build();
        }

        private URI identityUri() {
            return identityUri;
        }

        private int wellKnownHits() {
            return wellKnownHits.get();
        }

        private int jwkHits() {
            return jwkHits.get();
        }

        private void metadataAvailable(boolean metadataAvailable) {
            this.metadataAvailable = metadataAvailable;
        }

        private void jwkAvailable(boolean jwkAvailable) {
            this.jwkAvailable = jwkAvailable;
        }

        private void blockMetadataResponse() {
            metadataResponseGate = new CountDownLatch(1);
        }

        private boolean awaitMetadataRequest() throws InterruptedException {
            return metadataRequest.await(5, TimeUnit.SECONDS);
        }

        private void releaseMetadataResponse() {
            CountDownLatch responseGate = metadataResponseGate;
            if (responseGate != null) {
                responseGate.countDown();
            }
        }

        @Override
        public void close() {
            server.stop();
        }
    }

    private static final class MutableTenantConfigFinder implements TenantConfigFinder {
        private final String tenantId;
        private final AtomicReference<TenantConfig> tenantConfig;
        private Consumer<String> changeListener = ignored -> { };
        private volatile CountDownLatch configRequest;
        private volatile CountDownLatch configResponseGate;

        private MutableTenantConfigFinder(String tenantId, TenantConfig tenantConfig) {
            this.tenantId = tenantId;
            this.tenantConfig = new AtomicReference<>(tenantConfig);
        }

        @Override
        public Optional<TenantConfig> config(String tenantId) {
            if (this.tenantId.equals(tenantId)) {
                TenantConfig currentConfig = tenantConfig.get();
                CountDownLatch request = configRequest;
                CountDownLatch responseGate = configResponseGate;
                if (request != null && responseGate != null) {
                    request.countDown();
                    try {
                        responseGate.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while blocking tenant configuration", e);
                    }
                }
                return Optional.of(currentConfig);
            }
            return Optional.empty();
        }

        @Override
        public void onChange(Consumer<String> tenantIdChangeConsumer) {
            changeListener = tenantIdChangeConsumer;
        }

        private void update(TenantConfig tenantConfig) {
            this.tenantConfig.set(tenantConfig);
            changeListener.accept(tenantId);
        }

        private void blockConfigResponse() {
            configRequest = new CountDownLatch(1);
            configResponseGate = new CountDownLatch(1);
        }

        private boolean awaitConfigRequest() throws InterruptedException {
            return configRequest.await(5, TimeUnit.SECONDS);
        }

        private void releaseConfigResponse() {
            configResponseGate.countDown();
        }
    }
}
