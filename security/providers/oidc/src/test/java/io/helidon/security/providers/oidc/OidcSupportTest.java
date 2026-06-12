/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.http.FormParams;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.Subject;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.common.TokenCredential;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.Tenant;
import io.helidon.security.providers.oidc.common.TenantConfig;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import jakarta.json.Json;

import static io.helidon.security.providers.oidc.common.spi.TenantConfigFinder.DEFAULT_TENANT_ID;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link OidcSupport}.
 */
class OidcSupportTest {
    private static final String PARAM_NAME = "my-param-attempts";
    private static final String WELL_KNOWN_PATH = "/identity/.well-known/openid-configuration";

    private final OidcConfig oidcConfig = OidcConfig.builder()
            .clientId("id")
            .clientSecret("secret")
            .identityUri(URI.create("http://localhost:7774/identity"))
            .tokenEndpointUri(URI.create("http://localhost:7774/token"))
            .authorizationEndpointUri(URI.create("http://localhost:7774/authorize"))
            .signJwk(JwkKeys.builder().build())
            .oidcMetadataWellKnown(false)
            .build();
    private final OidcConfig oidcConfigCustomParam = OidcConfig.builder()
            .clientId("id")
            .clientSecret("secret")
            .identityUri(URI.create("http://localhost:7774/identity"))
            .tokenEndpointUri(URI.create("http://localhost:7774/token"))
            .authorizationEndpointUri(URI.create("http://localhost:7774/authorize"))
            .signJwk(JwkKeys.builder().build())
            .oidcMetadataWellKnown(false)
            .redirectAttemptParam(PARAM_NAME)
            .build();

    private final OidcSupport oidcSupport = OidcSupport.create(oidcConfig);
    private final OidcSupport oidcSupportCustomParam = OidcSupport.create(oidcConfigCustomParam);
    private final OidcProvider provider = OidcProvider.builder()
            .oidcConfig(oidcConfig)
            .outboundConfig(OutboundConfig.builder()
                                    .addTarget(OutboundTarget.builder("disabled")
                                                       .addHost("www.example.com")
                                                       .config(Config.create(ConfigSources.create(Map.of(
                                                               "propagate",
                                                               "false"))))
                                                       .build())
                                    .build())
            .build();

    private static String waitForLog(ByteArrayOutputStream capturedLogs,
                                     String expectedSnippet) throws InterruptedException {
        String capturedLog = "";
        for (int attempt = 0; attempt < 40; attempt++) {
            flushRootHandlers();
            capturedLog = capturedLogs.toString(StandardCharsets.UTF_8);
            if (capturedLog.contains(expectedSnippet)) {
                return capturedLog;
            }
            Thread.sleep(50);
        }
        return capturedLog;
    }

    private static void flushRootHandlers() {
        for (Handler handler : Logger.getLogger("").getHandlers()) {
            handler.flush();
        }
    }

    private static void restoreLoggingConfiguration() throws Exception {
        readLoggingConfigurationFromResource();
    }

    private static void readLoggingConfigurationFromResource() throws Exception {
        try (InputStream stream = OidcSupportTest.class.getResourceAsStream("/logging.properties")) {
            if (stream == null) {
                LogManager.getLogManager().readConfiguration();
            } else {
                LogManager.getLogManager().readConfiguration(stream);
            }
        }
    }

    @Test
    void testRedirectAttemptNoParams() {
        String state = "http://localhost:7145/test";
        String newState = oidcSupport.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(oidcConfig.redirectAttemptParam() + "=1"));
    }

    @Test
    void testRedirectAttemptNoParamsCustomName() {
        String state = "http://localhost:7145/test";
        String newState = oidcSupportCustomParam.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(PARAM_NAME + "=1"));
    }

    @Test
    void testRedirectAttemptOtherParams() {
        String state = "http://localhost:7145/test?a=first&b=second";
        String newState = oidcSupport.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(oidcConfig.redirectAttemptParam() + "=1"));
    }

    @Test
    void testRedirectAttemptOtherParamsCustomName() {
        String state = "http://localhost:7145/test?a=first&b=second";
        String newState = oidcSupportCustomParam.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(PARAM_NAME + "=1"));
    }

    @Test
    void testRedirectAttemptParams() {
        String state = "http://localhost:7145/test?a=first&b=second&" + oidcConfig.redirectAttemptParam() + "=1";
        String newState = oidcSupport.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(oidcConfig.redirectAttemptParam() + "=2"));
    }

    @Test
    void testRedirectAttemptParamsCustomName() {
        String state = "http://localhost:7145/test?a=first&b=second&" + PARAM_NAME + "=1";
        String newState = oidcSupportCustomParam.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(PARAM_NAME + "=2"));
    }

    @Test
    void testRedirectAttemptParamsInMiddle() {
        String state = "http://localhost:7145/test?a=first&" + oidcConfig.redirectAttemptParam() + "=1&b=second";
        String newState = oidcSupport.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(oidcConfig.redirectAttemptParam() + "=2&b=second"));
    }

    @Test
    void testRedirectAttemptParamsInMiddleCustomName() {
        String state = "http://localhost:7145/test?a=first&" + PARAM_NAME + "=11&b=second";
        String newState = oidcSupportCustomParam.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(PARAM_NAME + "=12&b=second"));
    }

    @Test
    void testPostLoginRedirectRejectsRawState() throws Exception {
        CallbackResult result = callbackResult(true, false, "https://example.com/test", DEFAULT_TENANT_ID, false);

        assertThat(result.status, is(Http.Status.UNAUTHORIZED_401));
        assertThat(result.tokenRequestCount, is(0));
    }

    @Test
    void testPostLoginRedirectRejectsRawStateWithTenant() throws Exception {
        CallbackResult result = callbackResult(true, false, "https://example.com/test", "tenant-one", false);

        assertThat(result.status, is(Http.Status.UNAUTHORIZED_401));
        assertThat(result.tokenRequestCount, is(0));
    }

    @Test
    void testPostLoginRedirectRejectsEncryptedStateWithoutNonceCookie() throws Exception {
        CallbackResult result = callbackResult(false,
                                               true,
                                               "/index.html",
                                               DEFAULT_TENANT_ID,
                                               true,
                                               null,
                                               false,
                                               false,
                                               false,
                                               null,
                                               false);

        assertThat(result.status, is(Http.Status.UNAUTHORIZED_401));
        assertThat(result.tokenRequestCount, is(0));
    }

    @Test
    void testPostLoginRedirectDoesNotAddQueryResultWithDefaultCookies() throws Exception {
        CallbackResult result = callbackResult(false, true, "/index.html", DEFAULT_TENANT_ID, true);

        assertThat(result.status, is(Http.Status.TEMPORARY_REDIRECT_307));
        assertThat(result.location, is("/index.html?h_ra=1"));
        assertThat(queryParam(result.location, "accessToken"), is(Optional.empty()));
        assertThat(result.queryResultNonce, is(Optional.empty()));
        assertThat(result.setCookies.stream().anyMatch(it -> it.startsWith("JSESSIONID=")), is(true));
        assertThat(result.setCookies.stream().anyMatch(it -> it.startsWith("HELIDON_TENANT=")), is(true));
        assertThat(result.setCookies.stream()
                           .anyMatch(it -> it.startsWith(OidcState.loginStateNonceCookieName("JSESSIONID") + "=;")),
                   is(true));
    }

    @Test
    void testPostLoginRedirectRejectsInvalidStateVariants() throws Exception {
        for (String state : List.of("//example.com/test",
                                    "/\\example.com/test",
                                    "/%2f%2fexample.com/test",
                                    "/%5cexample.com/test",
                                    "/test\nset-cookie: test=value")) {
            CallbackResult result = callbackResult(true, false, state, DEFAULT_TENANT_ID, false);

            assertThat(result.status, is(Http.Status.UNAUTHORIZED_401));
            assertThat(result.tokenRequestCount, is(0));
        }
    }

    @Test
    void testPostLoginRedirectAcceptsLegacyLocalStateFallback() throws Exception {
        CallbackResult result = callbackResult(false, true, "/index.html", DEFAULT_TENANT_ID, false, null, true, false);

        assertThat(result.status, is(Http.Status.TEMPORARY_REDIRECT_307));
        assertThat(result.location, is("/index.html?h_ra=1"));
        assertThat(result.tokenRequestCount, is(1));
    }

    @Test
    void testPostLoginRedirectAcceptsLegacyLocalStateWhenLegacyStateParamEnabled() throws Exception {
        CallbackResult result = callbackResult(false,
                                               true,
                                               "/index.html",
                                               DEFAULT_TENANT_ID,
                                               false,
                                               null,
                                               true,
                                               false,
                                               false);

        assertThat(result.status, is(Http.Status.TEMPORARY_REDIRECT_307));
        assertThat(result.location, is("/index.html?h_ra=1"));
        assertThat(result.tokenRequestCount, is(1));
    }

    @Test
    void testPostLoginRedirectRejectsExternalLegacyStateFallback() throws Exception {
        CallbackResult result = callbackResult(false,
                                               true,
                                               "https://example.com/test",
                                               DEFAULT_TENANT_ID,
                                               false,
                                               null,
                                               true,
                                               false);

        assertThat(result.status, is(Http.Status.UNAUTHORIZED_401));
        assertThat(result.tokenRequestCount, is(0));
    }

    @Test
    void testPostLoginRedirectUsesEncryptedQueryResult() throws Exception {
        CallbackResult result = callbackResult(true, false, "/index.html", DEFAULT_TENANT_ID, true);

        assertThat(result.status, is(Http.Status.TEMPORARY_REDIRECT_307));
        assertThat(result.location, containsString("/index.html?accessToken="));
        assertThat(result.location, not(containsString("access-token")));
        assertThat(result.queryResultAccessToken, is(Optional.of("access-token")));
    }

    @Test
    void testPostLoginRedirectUsesEncryptedQueryResultWithTenant() throws Exception {
        CallbackResult result = callbackResult(true, false, "/index.html", "tenant-one", true);

        assertThat(result.status, is(Http.Status.TEMPORARY_REDIRECT_307));
        assertThat(result.location, containsString("/index.html?accessToken="));
        assertThat(result.location, containsString("&h_tenant=tenant-one&"));
        assertThat(result.location, not(containsString("access-token")));
        assertThat(result.queryResultAccessToken, is(Optional.of("access-token")));
    }

    @Test
    void testPostLoginRedirectUsesNonceBoundEncryptedQueryResultWithCookies() throws Exception {
        CallbackResult result = callbackResult(true, true, "/index.html", DEFAULT_TENANT_ID, true);

        assertThat(result.status, is(Http.Status.TEMPORARY_REDIRECT_307));
        assertThat(result.location, containsString("/index.html?accessToken="));
        assertThat(result.location, not(containsString("access-token")));
        assertThat(result.queryResultNonce.isPresent(), is(true));
        assertThat(result.setCookies.stream()
                           .anyMatch(it -> it.startsWith(OidcState.queryResultNonceCookieName("JSESSIONID") + "=")
                                   && it.contains("Max-Age=60")),
                   is(true));
        assertThat(result.queryResultAccessToken, is(Optional.of("access-token")));
        assertThat(result.queryResultAccessTokenWithoutNonce, is(Optional.empty()));
    }

    @Test
    void testLogoutRemovesQueryResultNonceCookie() {
        OidcConfig config = OidcConfig.builder()
                .clientId("id")
                .clientSecret("secret")
                .identityUri(URI.create("http://localhost:7774/identity"))
                .tokenEndpointUri(URI.create("http://localhost:7774/token"))
                .authorizationEndpointUri(URI.create("http://localhost:7774/authorize"))
                .logoutEndpointUri(URI.create("http://localhost:7774/logout"))
                .postLogoutUri(URI.create("/logged-out"))
                .signJwk(JwkKeys.builder().build())
                .oidcMetadataWellKnown(false)
                .useParam(true)
                .useCookie(true)
                .logoutEnabled(true)
                .cookieEncryptionPassword("test-password".toCharArray())
                .build();
        Routing.Builder routing = Routing.builder();
        OidcSupport.create(config).update(routing);
        WebServer server = WebServer.builder()
                .defaultSocket(socket -> socket.host("localhost"))
                .addRouting(routing.build())
                .build();
        server.start().await(Duration.ofSeconds(10));
        try {
            String idTokenSetCookie = config.idTokenCookieHandler()
                    .createCookie("id-token")
                    .await()
                    .build()
                    .toString();
            String nonceCookieName = OidcState.queryResultNonceCookieName(config.tokenCookieHandler().cookieName());
            WebClientRequestBuilder request = WebClient.builder()
                    .build()
                    .get()
                    .uri("http://localhost:" + server.port() + config.logoutUri())
                    .followRedirects(false);
            request.headers()
                    .addCookie(config.idTokenCookieHandler().cookieName(), cookieValue(idTokenSetCookie))
                    .addCookie(nonceCookieName, "nonce");

            WebClientResponse response = request.request().await(Duration.ofSeconds(10));
            try {
                List<String> setCookies = response.headers().all(Http.Header.SET_COOKIE);

                assertThat(response.status(), is(Http.Status.TEMPORARY_REDIRECT_307));
                assertThat(setCookies.stream()
                                   .anyMatch(it -> it.startsWith(config.tokenCookieHandler().cookieName() + "=;")),
                           is(true));
                assertThat(setCookies.stream()
                                   .anyMatch(it -> it.startsWith(config.idTokenCookieHandler().cookieName() + "=;")),
                           is(true));
                assertThat(setCookies.stream()
                                   .anyMatch(it -> it.startsWith(config.tenantCookieHandler().cookieName() + "=;")),
                           is(true));
                assertThat(setCookies.stream().anyMatch(it -> it.startsWith(nonceCookieName + "=;")),
                           is(true));
            } finally {
                response.close().await(Duration.ofSeconds(10));
            }
        } finally {
            server.shutdown().await(Duration.ofSeconds(10));
        }
    }

    @Test
    void testPostLoginRedirectUsesConfiguredQueryParamName() throws Exception {
        CallbackResult result = callbackResult(true, false, "/index.html", DEFAULT_TENANT_ID, true, "custom-token");

        assertThat(result.status, is(Http.Status.TEMPORARY_REDIRECT_307));
        assertThat(result.location, containsString("/index.html?custom-token="));
        assertThat(result.location, not(containsString("accessToken=")));
        assertThat(result.location, not(containsString("access-token")));
        assertThat(result.queryResultAccessToken, is(Optional.of("access-token")));
    }

    @Test
    void testPostLoginRedirectUsesLastDecryptableQueryResult() throws Exception {
        CallbackResult result = callbackResult(true, false, "/index.html?accessToken=app-value", DEFAULT_TENANT_ID, true);

        assertThat(result.status, is(Http.Status.TEMPORARY_REDIRECT_307));
        assertThat(result.location, containsString("/index.html?accessToken=app-value&accessToken="));
        assertThat(result.location, not(containsString("access-token")));
        assertThat(result.queryResultAccessToken, is(Optional.of("access-token")));
    }

    @Test
    void testPostLoginRedirectUsesLegacyRawQueryParamHandoff() throws Exception {
        CallbackResult result = callbackResult(true, false, "/index.html", DEFAULT_TENANT_ID, true, null, false, true);

        assertThat(result.status, is(Http.Status.TEMPORARY_REDIRECT_307));
        assertThat(result.location, containsString("/index.html?accessToken=access-token"));
        assertThat(queryParam(result.location, "accessToken"), is(Optional.of("access-token")));
        assertThat(result.queryResultAccessToken, is(Optional.empty()));
    }

    @Test
    void testPostLoginRedirectUsesLegacyRawQueryParamHandoffWithCookies() throws Exception {
        CallbackResult result = callbackResult(true, true, "/index.html", DEFAULT_TENANT_ID, true, null, false, true);

        assertThat(result.status, is(Http.Status.TEMPORARY_REDIRECT_307));
        assertThat(result.location, containsString("/index.html?accessToken=access-token"));
        assertThat(queryParam(result.location, "accessToken"), is(Optional.of("access-token")));
        assertThat(result.queryResultNonce, is(Optional.empty()));
        assertThat(result.queryResultAccessToken, is(Optional.empty()));
    }

    @Test
    void testPostLoginRedirectAppendsLegacyRawQueryParamHandoffAfterExistingParam() throws Exception {
        CallbackResult result = callbackResult(true,
                                               false,
                                               "/index.html?accessToken=app-value",
                                               DEFAULT_TENANT_ID,
                                               true,
                                               null,
                                               false,
                                               true);

        assertThat(result.status, is(Http.Status.TEMPORARY_REDIRECT_307));
        assertThat(queryParams(result.location, "accessToken"), is(List.of("app-value", "access-token")));
        assertThat(result.queryResultAccessToken, is(Optional.empty()));
    }

    @Test
    void testQueryParamWithoutCookiesLogsRecommendation() {
        OidcConfig config = OidcConfig.builder()
                .clientId("id")
                .clientSecret("secret")
                .identityUri(URI.create("http://localhost:7774/identity"))
                .tokenEndpointUri(URI.create("http://localhost:7774/token"))
                .authorizationEndpointUri(URI.create("http://localhost:7774/authorize"))
                .signJwk(JwkKeys.builder().build())
                .oidcMetadataWellKnown(false)
                .useParam(true)
                .useCookie(false)
                .build();
        CapturingLogHandler handler = new CapturingLogHandler();
        Logger logger = Logger.getLogger(OidcSupport.class.getName());
        logger.addHandler(handler);
        try {
            OidcSupport.create(config);
        } finally {
            logger.removeHandler(handler);
        }

        assertThat(handler.contains("query parameter handoff is enabled without cookies"), is(true));
    }

    @Test
    void testLegacyQueryParamWithoutCookiesDoesNotLogRecommendation() {
        OidcConfig config = OidcConfig.builder()
                .clientId("id")
                .clientSecret("secret")
                .identityUri(URI.create("http://localhost:7774/identity"))
                .tokenEndpointUri(URI.create("http://localhost:7774/token"))
                .authorizationEndpointUri(URI.create("http://localhost:7774/authorize"))
                .signJwk(JwkKeys.builder().build())
                .oidcMetadataWellKnown(false)
                .useParam(true)
                .useCookie(false)
                .legacyQueryParamHandoff(true)
                .build();
        CapturingLogHandler handler = new CapturingLogHandler();
        Logger logger = Logger.getLogger(OidcSupport.class.getName());
        logger.addHandler(handler);
        try {
            OidcSupport.create(config);
        } finally {
            logger.removeHandler(handler);
        }

        assertThat(handler.contains("query parameter handoff is enabled without cookies"), is(false));
    }

    @Test
    void testLoginStateAllowsSmallClockSkew() {
        long now = Instant.now().getEpochSecond();
        String state = OidcState.createLoginState("/protected", oidcConfig, now + 30);

        assertThat(OidcState.loginRedirect(state, oidcConfig), is(Optional.of("/protected")));
    }

    @Test
    void testLoginStateRejectsLargeClockSkew() {
        long now = Instant.now().getEpochSecond();
        String state = OidcState.createLoginState("/protected", oidcConfig, now + 120);

        assertThat(OidcState.loginRedirect(state, oidcConfig), is(Optional.empty()));
    }

    @Test
    void testLoginStateRequiresMatchingNonce() {
        String state = OidcState.createLoginState("/protected", oidcConfig, "nonce");

        assertThat(OidcState.loginRedirect(state, oidcConfig), is(Optional.empty()));
        assertThat(OidcState.loginRedirect(state, oidcConfig, Optional.empty(), true), is(Optional.empty()));
        assertThat(OidcState.loginRedirect(state, oidcConfig, Optional.of("other"), true), is(Optional.empty()));
        assertThat(OidcState.loginRedirect(state, oidcConfig, Optional.of("nonce"), true), is(Optional.of("/protected")));
    }

    @Test
    void testCallbackRejectsExpiredLoginStateWithoutTokenExchange() throws Exception {
        CallbackResult result = callbackResult(false,
                                               false,
                                               "/protected",
                                               DEFAULT_TENANT_ID,
                                               true,
                                               null,
                                               false,
                                               false,
                                               false,
                                               Instant.now().minusSeconds(360).getEpochSecond());

        assertThat(result.status, is(Http.Status.UNAUTHORIZED_401));
        assertThat(result.tokenRequestCount, is(0));
        assertThat(result.location, is((String) null));
    }

    @Test
    void testQueryResultAllowsSmallClockSkew() {
        long now = Instant.now().getEpochSecond();
        String result = OidcState.createQueryResult("access-token",
                                                    Json.createObjectBuilder().build(),
                                                    oidcConfig,
                                                    now + 30);

        assertThat(OidcState.queryResultAccessToken(result, oidcConfig), is(Optional.of("access-token")));
    }

    @Test
    void testQueryResultRejectsLargeClockSkew() {
        long now = Instant.now().getEpochSecond();
        String result = OidcState.createQueryResult("access-token",
                                                    Json.createObjectBuilder().build(),
                                                    oidcConfig,
                                                    now + 120);

        assertThat(OidcState.queryResultAccessToken(result, oidcConfig), is(Optional.empty()));
    }

    @Test
    void testCreateQueryResultRejectsOversizedEncryptedValue() {
        assertThrows(IllegalStateException.class,
                     () -> OidcState.createQueryResult("x".repeat(17 * 1024),
                                                       Json.createObjectBuilder().build(),
                                                       oidcConfig));
    }

    @Test
    void testAuthorizationRedirectUsesEncryptedState() {
        ProviderRequest providerRequest = Mockito.mock(ProviderRequest.class);
        SecurityEnvironment env = SecurityEnvironment.builder()
                .targetUri(URI.create("http://localhost:8080/protected?query=value"))
                .path("/protected")
                .header("host", "localhost:8080")
                .build();
        when(providerRequest.env()).thenReturn(env);
        when(providerRequest.endpointConfig()).thenReturn(EndpointConfig.builder().build());

        TenantAuthenticationHandler handler = new TenantAuthenticationHandler(oidcConfig,
                                                                              Tenant.create(oidcConfig, oidcConfig),
                                                                              false,
                                                                              false);
        AuthenticationResponse response = handler.authenticate(DEFAULT_TENANT_ID, providerRequest)
                .toCompletableFuture()
                .join();
        String location = response.responseHeaders().get("Location").get(0);
        String state = queryParam(location, "state").orElseThrow();
        Optional<String> stateNonce = loginStateNonce(response.responseHeaders()
                                                              .getOrDefault(Http.Header.SET_COOKIE, List.of()),
                                                      oidcConfig);

        assertThat(location, not(containsString("/protected?query=value")));
        assertThat(stateNonce.isPresent(), is(true));
        assertThat(OidcState.loginRedirect(state, oidcConfig), is(Optional.empty()));
        assertThat(OidcState.loginRedirect(state, oidcConfig, stateNonce, true),
                   is(Optional.of("/protected?query=value")));
    }

    @Test
    void testAuthorizationRedirectUsesCallbackCompatibleStateNonceCookie() {
        OidcConfig config = OidcConfig.builder()
                .clientId("id")
                .clientSecret("secret")
                .identityUri(URI.create("http://localhost:7774/identity"))
                .tokenEndpointUri(URI.create("http://localhost:7774/token"))
                .authorizationEndpointUri(URI.create("http://localhost:7774/authorize"))
                .redirectUri("/oidc/callback")
                .cookiePath("/protected")
                .cookieSameSite("Strict")
                .cookieMaxAgeSeconds(1000)
                .cookieSecure(true)
                .signJwk(JwkKeys.builder().build())
                .oidcMetadataWellKnown(false)
                .build();
        ProviderRequest providerRequest = Mockito.mock(ProviderRequest.class);
        SecurityEnvironment env = SecurityEnvironment.builder()
                .targetUri(URI.create("http://localhost:8080/protected"))
                .path("/protected")
                .header("host", "localhost:8080")
                .build();
        when(providerRequest.env()).thenReturn(env);
        when(providerRequest.endpointConfig()).thenReturn(EndpointConfig.builder().build());

        TenantAuthenticationHandler handler = new TenantAuthenticationHandler(config,
                                                                              Tenant.create(config, config),
                                                                              false,
                                                                              false);
        AuthenticationResponse response = handler.authenticate(DEFAULT_TENANT_ID, providerRequest)
                .toCompletableFuture()
                .join();
        String stateNonceSetCookie = response.responseHeaders()
                .getOrDefault(Http.Header.SET_COOKIE, List.of())
                .stream()
                .filter(it -> it.startsWith(OidcState.loginStateNonceCookieName(config.tokenCookieHandler().cookieName())
                                                    + "="))
                .findFirst()
                .orElseThrow();

        assertThat(stateNonceSetCookie, containsString("Max-Age=300"));
        assertThat(stateNonceSetCookie, containsString("Path=/oidc/callback"));
        assertThat(stateNonceSetCookie, containsString("SameSite=Lax"));
        assertThat(stateNonceSetCookie, containsString("Secure"));
        assertThat(stateNonceSetCookie, containsString("HttpOnly"));
        assertThat(stateNonceSetCookie, not(containsString("Max-Age=1000")));
        assertThat(stateNonceSetCookie, not(containsString("Path=/protected")));
        assertThat(stateNonceSetCookie, not(containsString("SameSite=Strict")));
    }

    @Test
    void testUnknownRedirectTenantDoesNotUseDefaultTokenEndpoint() throws Exception {
        AtomicInteger tokenRequestCount = new AtomicInteger();
        WebServer tokenServer = WebServer.builder()
                .defaultSocket(socket -> socket.host("localhost"))
                .routing(routing -> routing.post("/token",
                                                 (req, res) -> {
                                                     tokenRequestCount.incrementAndGet();
                                                     res.headers().contentType(MediaType.APPLICATION_JSON);
                                                     res.send("{\"access_token\":\"access-token\"}");
                                                 }))
                .build();
        tokenServer.start().await(Duration.ofSeconds(10));

        try {
            OidcConfig config = OidcConfig.builder()
                    .clientId("id")
                    .clientSecret("secret")
                    .identityUri(URI.create("http://localhost:" + tokenServer.port() + "/identity"))
                    .tokenEndpointUri(URI.create("http://localhost:" + tokenServer.port() + "/token"))
                    .authorizationEndpointUri(URI.create("http://localhost:" + tokenServer.port() + "/authorize"))
                    .signJwk(JwkKeys.builder().build())
                    .oidcMetadataWellKnown(false)
                    .useParam(true)
                    .useCookie(false)
                    .build();
            Routing.Builder routing = Routing.builder();
            OidcSupport.create(config).update(routing);
            WebServer callbackServer = WebServer.builder()
                    .defaultSocket(socket -> socket.host("localhost"))
                    .addRouting(routing.build())
                    .build();
            callbackServer.start().await(Duration.ofSeconds(10));
            try {
                String stateNonce = "state-nonce";
                String state = URLEncoder.encode(OidcState.createLoginState("/test", config, stateNonce),
                                                 StandardCharsets.UTF_8);
                WebClientRequestBuilder request = WebClient.builder()
                        .build()
                        .get()
                        .uri("http://localhost:" + callbackServer.port()
                                     + config.redirectUri()
                                     + "?code=code&state=" + state + "&"
                                     + config.tenantParamName()
                                     + "=unknown")
                        .followRedirects(false);
                request.headers()
                        .addCookie(OidcState.loginStateNonceCookieName(config.tokenCookieHandler().cookieName()), stateNonce);
                WebClientResponse response = request.request()
                        .await(Duration.ofSeconds(10));

                try {
                    assertThat(response.status(), is(Http.Status.UNAUTHORIZED_401));
                    assertThat("unknown tenant must not use default token endpoint", tokenRequestCount.get(), is(0));
                } finally {
                    response.close().await(Duration.ofSeconds(10));
                }
            } finally {
                callbackServer.shutdown().await(Duration.ofSeconds(10));
            }
        } finally {
            tokenServer.shutdown().await(Duration.ofSeconds(10));
        }
    }

    @Test
    void testFallbackRedirectTenantUsesDefaultTokenEndpoint() throws Exception {
        AtomicInteger tokenRequestCount = new AtomicInteger();
        WebServer tokenServer = WebServer.builder()
                .defaultSocket(socket -> socket.host("localhost"))
                .routing(routing -> routing.post("/token",
                                                 (req, res) -> {
                                                     tokenRequestCount.incrementAndGet();
                                                     res.headers().contentType(MediaType.APPLICATION_JSON);
                                                     res.send("{\"access_token\":\"access-token\"}");
                                                 }))
                .build();
        tokenServer.start().await(Duration.ofSeconds(10));

        try {
            OidcConfig config = OidcConfig.builder()
                    .clientId("id")
                    .clientSecret("secret")
                    .identityUri(URI.create("http://localhost:" + tokenServer.port() + "/identity"))
                    .tokenEndpointUri(URI.create("http://localhost:" + tokenServer.port() + "/token"))
                    .authorizationEndpointUri(URI.create("http://localhost:" + tokenServer.port() + "/authorize"))
                    .signJwk(JwkKeys.builder().build())
                    .oidcMetadataWellKnown(false)
                    .useParam(true)
                    .useCookie(false)
                    .fallbackToDefaultTenantEnabled(true)
                    .build();
            Routing.Builder routing = Routing.builder();
            OidcSupport.create(config).update(routing);
            WebServer callbackServer = WebServer.builder()
                    .defaultSocket(socket -> socket.host("localhost"))
                    .addRouting(routing.build())
                    .build();
            callbackServer.start().await(Duration.ofSeconds(10));
            try {
                String stateNonce = "state-nonce";
                String state = URLEncoder.encode(OidcState.createLoginState("/test", config, stateNonce),
                                                 StandardCharsets.UTF_8);
                WebClientRequestBuilder request = WebClient.builder()
                        .build()
                        .get()
                        .uri("http://localhost:" + callbackServer.port()
                                     + config.redirectUri()
                                     + "?code=code&state=" + state + "&"
                                     + config.tenantParamName()
                                     + "=unknown")
                        .followRedirects(false);
                request.headers()
                        .addCookie(OidcState.loginStateNonceCookieName(config.tokenCookieHandler().cookieName()), stateNonce);
                WebClientResponse response = request.request()
                        .await(Duration.ofSeconds(10));

                try {
                    assertThat(response.status(), is(Http.Status.TEMPORARY_REDIRECT_307));
                    assertThat("unknown tenant should use default token endpoint when fallback is enabled",
                               tokenRequestCount.get(),
                               is(1));
                    String location = response.headers().first(Http.Header.LOCATION).orElseThrow();
                    assertThat(location, containsString("/test?accessToken="));
                    assertThat(location, containsString("&h_tenant=unknown&h_ra=1"));
                    assertThat(location, not(containsString("access-token")));
                    assertThat(queryResultAccessToken(location, config, "unknown"), is(Optional.of("access-token")));
                } finally {
                    response.close().await(Duration.ofSeconds(10));
                }
            } finally {
                callbackServer.shutdown().await(Duration.ofSeconds(10));
            }
        } finally {
            tokenServer.shutdown().await(Duration.ofSeconds(10));
        }
    }

    @Test
    void testUnknownLogoutTenantDoesNotResolveDefaultTenant() throws Exception {
        try (MockIdpServer idp = new MockIdpServer()) {
            OidcConfig config = OidcConfig.builder()
                    .clientId("id")
                    .clientSecret("secret")
                    .identityUri(idp.identityUri())
                    .signJwk(JwkKeys.builder().build())
                    .logoutEnabled(true)
                    .postLogoutUri(URI.create("/logged-out"))
                    .useParam(true)
                    .build();
            Routing.Builder routing = Routing.builder();
            OidcSupport.create(config).update(routing);
            WebServer logoutServer = WebServer.builder()
                    .defaultSocket(socket -> socket.host("localhost"))
                    .addRouting(routing.build())
                    .build();
            logoutServer.start().await(Duration.ofSeconds(10));
            try {
                WebClientResponse response = WebClient.builder()
                        .build()
                        .get()
                        .uri("http://localhost:" + logoutServer.port()
                                     + config.logoutUri()
                                     + "?"
                                     + config.tenantParamName()
                                     + "=unknown")
                        .headers(headers -> headers.add(Http.Header.COOKIE, logoutCookies(config)))
                        .followRedirects(false)
                        .request()
                        .await(Duration.ofSeconds(10));

                try {
                    assertThat(response.status(), is(Http.Status.UNAUTHORIZED_401));
                    assertThat("unknown logout tenant must not resolve default metadata", idp.wellKnownHits(), is(0));
                    List<String> cookies = response.headers().all(Http.Header.SET_COOKIE);
                    assertRemoveCookie(cookies, config.tokenCookieHandler().cookieName());
                    assertRemoveCookie(cookies, config.idTokenCookieHandler().cookieName());
                    assertRemoveCookie(cookies, config.tenantCookieHandler().cookieName());
                } finally {
                    response.close().await(Duration.ofSeconds(10));
                }
            } finally {
                logoutServer.shutdown().await(Duration.ofSeconds(10));
            }
        }
    }

    @Test
    void testFallbackLogoutTenantUsesDefaultTenant() throws Exception {
        try (MockIdpServer idp = new MockIdpServer()) {
            OidcConfig config = OidcConfig.builder()
                    .clientId("id")
                    .clientSecret("secret")
                    .identityUri(idp.identityUri())
                    .signJwk(JwkKeys.builder().build())
                    .logoutEnabled(true)
                    .postLogoutUri(URI.create("/logged-out"))
                    .useParam(true)
                    .fallbackToDefaultTenantEnabled(true)
                    .build();
            Routing.Builder routing = Routing.builder();
            OidcSupport.create(config).update(routing);
            WebServer logoutServer = WebServer.builder()
                    .defaultSocket(socket -> socket.host("localhost"))
                    .addRouting(routing.build())
                    .build();
            logoutServer.start().await(Duration.ofSeconds(10));
            try {
                WebClientResponse response = WebClient.builder()
                        .build()
                        .get()
                        .uri("http://localhost:" + logoutServer.port()
                                     + config.logoutUri()
                                     + "?"
                                     + config.tenantParamName()
                                     + "=unknown")
                        .headers(headers -> headers.add(Http.Header.COOKIE, logoutCookies(config, "unknown")))
                        .followRedirects(false)
                        .request()
                        .await(Duration.ofSeconds(10));

                try {
                    assertThat(response.status(), is(Http.Status.TEMPORARY_REDIRECT_307));
                    assertThat("unknown logout tenant should resolve default metadata when fallback is enabled",
                               idp.wellKnownHits(),
                               is(1));
                    assertThat(response.headers().first(Http.Header.LOCATION).orElseThrow(),
                               startsWith(idp.logoutEndpointUri()
                                                  + "?id_token_hint=id-token&post_logout_redirect_uri=http://localhost:"
                                                  + logoutServer.port()
                                                  + "/logged-out"));
                } finally {
                    response.close().await(Duration.ofSeconds(10));
                }
            } finally {
                logoutServer.shutdown().await(Duration.ofSeconds(10));
            }
        }
    }

    @Test
    void testLogoutStateIsEncoded() throws Exception {
        try (MockIdpServer idp = new MockIdpServer()) {
            OidcConfig config = OidcConfig.builder()
                    .clientId("id")
                    .clientSecret("secret")
                    .identityUri(idp.identityUri())
                    .signJwk(JwkKeys.builder().build())
                    .logoutEnabled(true)
                    .postLogoutUri(URI.create("/logged-out"))
                    .build();
            Routing.Builder routing = Routing.builder();
            OidcSupport.create(config).update(routing);
            WebServer logoutServer = WebServer.builder()
                    .defaultSocket(socket -> socket.host("localhost"))
                    .addRouting(routing.build())
                    .build();
            logoutServer.start().await(Duration.ofSeconds(10));
            try {
                WebClientResponse response = WebClient.builder()
                        .build()
                        .get()
                        .uri("http://localhost:" + logoutServer.port()
                                     + config.logoutUri()
                                     + "?state=a%26x%3Dy")
                        .headers(headers -> headers.add(Http.Header.COOKIE, logoutCookies(config, DEFAULT_TENANT_ID)))
                        .followRedirects(false)
                        .skipUriEncoding()
                        .request()
                        .await(Duration.ofSeconds(10));

                try {
                    assertThat(response.status(), is(Http.Status.TEMPORARY_REDIRECT_307));
                    String location = response.headers().first(Http.Header.LOCATION).orElseThrow();
                    assertThat(location, containsString("&state=a%26x%3Dy"));
                    assertThat(location, not(containsString("&x=y")));
                } finally {
                    response.close().await(Duration.ofSeconds(10));
                }
            } finally {
                logoutServer.shutdown().await(Duration.ofSeconds(10));
            }
        }
    }

    @Test
    void testInvalidLogoutStateRejected() throws Exception {
        try (MockIdpServer idp = new MockIdpServer()) {
            OidcConfig config = OidcConfig.builder()
                    .clientId("id")
                    .clientSecret("secret")
                    .identityUri(idp.identityUri())
                    .signJwk(JwkKeys.builder().build())
                    .logoutEnabled(true)
                    .postLogoutUri(URI.create("/logged-out"))
                    .build();
            Routing.Builder routing = Routing.builder();
            OidcSupport.create(config).update(routing);
            WebServer logoutServer = WebServer.builder()
                    .defaultSocket(socket -> socket.host("localhost"))
                    .addRouting(routing.build())
                    .build();
            logoutServer.start().await(Duration.ofSeconds(10));
            try {
                WebClientResponse response = WebClient.builder()
                        .build()
                        .get()
                        .uri("http://localhost:" + logoutServer.port()
                                     + config.logoutUri()
                                     + "?state=a%0D%0AInjected:%20value")
                        .headers(headers -> headers.add(Http.Header.COOKIE, logoutCookies(config, DEFAULT_TENANT_ID)))
                        .followRedirects(false)
                        .skipUriEncoding()
                        .request()
                        .await(Duration.ofSeconds(10));

                try {
                    assertThat(response.status(), is(Http.Status.BAD_REQUEST_400));
                    assertThat(response.headers().first(Http.Header.LOCATION), is(Optional.empty()));
                    assertThat("invalid state must not resolve tenant metadata", idp.wellKnownHits(), is(0));
                } finally {
                    response.close().await(Duration.ofSeconds(10));
                }
            } finally {
                logoutServer.shutdown().await(Duration.ofSeconds(10));
            }
        }
    }

    @Test
    void testLogoutWithoutIdTokenDoesNotResolveTenant() throws Exception {
        try (MockIdpServer idp = new MockIdpServer()) {
            OidcConfig config = OidcConfig.builder()
                    .clientId("id")
                    .clientSecret("secret")
                    .identityUri(idp.identityUri())
                    .signJwk(JwkKeys.builder().build())
                    .logoutEnabled(true)
                    .postLogoutUri(URI.create("/logged-out"))
                    .useParam(true)
                    .build();
            Routing.Builder routing = Routing.builder();
            OidcSupport.create(config).update(routing);
            WebServer logoutServer = WebServer.builder()
                    .defaultSocket(socket -> socket.host("localhost"))
                    .addRouting(routing.build())
                    .build();
            logoutServer.start().await(Duration.ofSeconds(10));
            try {
                assertLogoutWithoutIdToken(logoutServer, config, DEFAULT_TENANT_ID);
                assertLogoutWithoutIdToken(logoutServer, config, "unknown");
                assertThat("logout without ID token must not resolve tenant metadata", idp.wellKnownHits(), is(0));
            } finally {
                logoutServer.shutdown().await(Duration.ofSeconds(10));
            }
        }
    }

    @Test
    void testInvalidLogoutIdTokenDoesNotResolveTenant() throws Exception {
        try (MockIdpServer idp = new MockIdpServer()) {
            OidcConfig config = OidcConfig.builder()
                    .clientId("id")
                    .clientSecret("secret")
                    .identityUri(idp.identityUri())
                    .signJwk(JwkKeys.builder().build())
                    .logoutEnabled(true)
                    .postLogoutUri(URI.create("/logged-out"))
                    .useParam(true)
                    .build();
            Routing.Builder routing = Routing.builder();
            OidcSupport.create(config).update(routing);
            WebServer logoutServer = WebServer.builder()
                    .defaultSocket(socket -> socket.host("localhost"))
                    .addRouting(routing.build())
                    .build();
            logoutServer.start().await(Duration.ofSeconds(10));
            try {
                assertLogoutWithInvalidIdToken(logoutServer, config, DEFAULT_TENANT_ID);
                assertLogoutWithInvalidIdToken(logoutServer, config, "unknown");
                assertThat("invalid ID token must not resolve tenant metadata", idp.wellKnownHits(), is(0));
            } finally {
                logoutServer.shutdown().await(Duration.ofSeconds(10));
            }
        }
    }

    private static String logoutCookies(OidcConfig config) {
        return logoutCookies(config, "unknown");
    }

    private static String logoutCookies(OidcConfig config, String tenantName) {
        return logoutCookies(config, tenantName, idTokenCookie(config, "id-token"));
    }

    private static String logoutCookies(OidcConfig config, String tenantName, String idTokenCookie) {
        return config.tokenCookieHandler().cookieName() + "=token; "
                + idTokenCookie + "; "
                + config.tenantCookieHandler().cookieName() + "=" + tenantName;
    }

    private static String idTokenCookie(OidcConfig config, String idToken) {
        String setCookie = config.idTokenCookieHandler()
                .createCookie(idToken)
                .map(cookie -> cookie.build().toString())
                .await(Duration.ofSeconds(10));
        int optionsIndex = setCookie.indexOf(';');
        return optionsIndex < 0 ? setCookie : setCookie.substring(0, optionsIndex);
    }

    private static void assertLogoutWithoutIdToken(WebServer logoutServer,
                                                   OidcConfig config,
                                                   String tenantName) throws Exception {
        String logoutUri = "http://localhost:" + logoutServer.port() + config.logoutUri();
        if (!DEFAULT_TENANT_ID.equals(tenantName)) {
            logoutUri += "?" + config.tenantParamName() + "=" + tenantName;
        }

        WebClientResponse response = WebClient.builder()
                .build()
                .get()
                .uri(logoutUri)
                .headers(headers -> headers.add(Http.Header.COOKIE, logoutCookiesWithoutIdToken(config, tenantName)))
                .followRedirects(false)
                .request()
                .await(Duration.ofSeconds(10));

        try {
            assertThat(response.status(), is(Http.Status.FORBIDDEN_403));
            assertThat(response.headers().all(Http.Header.SET_COOKIE), is(List.of()));
        } finally {
            response.close().await(Duration.ofSeconds(10));
        }
    }

    private static void assertLogoutWithInvalidIdToken(WebServer logoutServer,
                                                       OidcConfig config,
                                                       String tenantName) throws Exception {
        String logoutUri = "http://localhost:" + logoutServer.port() + config.logoutUri();
        if (!DEFAULT_TENANT_ID.equals(tenantName)) {
            logoutUri += "?" + config.tenantParamName() + "=" + tenantName;
        }

        WebClientResponse response = WebClient.builder()
                .build()
                .get()
                .uri(logoutUri)
                .headers(headers -> headers.add(Http.Header.COOKIE,
                                                logoutCookies(config,
                                                              tenantName,
                                                              config.idTokenCookieHandler().cookieName() + "=invalid")))
                .followRedirects(false)
                .request()
                .await(Duration.ofSeconds(10));

        try {
            assertThat(response.status(), is(Http.Status.UNAUTHORIZED_401));
            List<String> cookies = response.headers().all(Http.Header.SET_COOKIE);
            assertRemoveCookie(cookies, config.tokenCookieHandler().cookieName());
            assertRemoveCookie(cookies, config.idTokenCookieHandler().cookieName());
            assertRemoveCookie(cookies, config.tenantCookieHandler().cookieName());
        } finally {
            response.close().await(Duration.ofSeconds(10));
        }
    }

    private static String logoutCookiesWithoutIdToken(OidcConfig config, String tenantName) {
        return config.tokenCookieHandler().cookieName() + "=token; "
                + config.tenantCookieHandler().cookieName() + "=" + tenantName;
    }

    private static void assertRemoveCookie(List<String> cookies, String cookieName) {
        assertThat("remove cookie " + cookieName,
                   cookies.stream().anyMatch(cookie -> cookie.startsWith(cookieName + "=;")),
                   is(true));
    }

    @Test
    void testAuthorizationRedirectUsesLegacyStateWhenConfigured() {
        OidcConfig config = OidcConfig.builder()
                .clientId("id")
                .clientSecret("secret")
                .identityUri(URI.create("http://localhost:7774/identity"))
                .tokenEndpointUri(URI.create("http://localhost:7774/token"))
                .authorizationEndpointUri(URI.create("http://localhost:7774/authorize"))
                .signJwk(JwkKeys.builder().build())
                .oidcMetadataWellKnown(false)
                .legacyStateParam(true)
                .build();
        ProviderRequest providerRequest = Mockito.mock(ProviderRequest.class);
        SecurityEnvironment env = SecurityEnvironment.builder()
                .targetUri(URI.create("http://localhost:8080/protected?query=value"))
                .path("/protected")
                .header("host", "localhost:8080")
                .build();
        when(providerRequest.env()).thenReturn(env);
        when(providerRequest.endpointConfig()).thenReturn(EndpointConfig.builder().build());

        TenantAuthenticationHandler handler = new TenantAuthenticationHandler(config,
                                                                              Tenant.create(config, config),
                                                                              false,
                                                                              false);
        AuthenticationResponse response = handler.authenticate(DEFAULT_TENANT_ID, providerRequest)
                .toCompletableFuture()
                .join();
        String location = response.responseHeaders().get("Location").get(0);
        String state = queryParam(location, "state").orElseThrow();

        assertThat(state, is("/protected?query=value"));
        assertThat(OidcState.loginRedirect(state, config), is(Optional.empty()));
    }

    @Test
    void testOriginalUriHeaderUsesRawRequestPathAndQuery() throws Exception {
        Routing.Builder routing = Routing.builder();
        OidcSupport.create(oidcConfig).update(routing);
        routing.get("/raw*", (req, res) -> {
            @SuppressWarnings("unchecked")
            Map<String, List<String>> addedHeaders = req.context()
                    .get(WebSecurity.CONTEXT_ADD_HEADERS, Map.class)
                    .map(map -> (Map<String, List<String>>) map)
                    .orElseThrow();
            res.send(addedHeaders.get(Security.HEADER_ORIG_URI).get(0));
        });
        WebServer server = WebServer.builder()
                .defaultSocket(socket -> socket.host("localhost"))
                .addRouting(routing.build())
                .build();
        server.start().await(Duration.ofSeconds(10));

        try {
            WebClientResponse response = WebClient.builder()
                    .build()
                    .get()
                    .uri("http://localhost:" + server.port()
                                 + "/raw%2Fresource/?return=https%3A%2F%2Fexample.com%2Ftest")
                    .skipUriEncoding()
                    .request()
                    .await(Duration.ofSeconds(10));

            try {
                assertThat(response.status(), is(Http.Status.OK_200));
                assertThat(response.content().as(String.class).await(Duration.ofSeconds(10)),
                           is("/raw%2Fresource/?return=https%3A%2F%2Fexample.com%2Ftest"));
            } finally {
                response.close().await(Duration.ofSeconds(10));
            }
        } finally {
            server.shutdown().await(Duration.ofSeconds(10));
        }
    }

    @Test
    void testErrorCallbackEscapesNewLinesBeforeLogging() throws Exception {
        ByteArrayOutputStream capturedLogs = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream captureOut = new PrintStream(capturedLogs, true, StandardCharsets.UTF_8);

        WebServer server = WebServer.builder()
                .defaultSocket(socket -> socket.host("localhost"))
                .routing(rules -> rules.register(OidcSupport.create(oidcConfig)))
                .build();

        try {
            System.setOut(captureOut);
            readLoggingConfigurationFromResource();
            server.start().await(Duration.ofSeconds(10));
            capturedLogs.reset();
            String forgedLine = "FORGED WARNING: attacker-controlled second line";
            WebClientResponse response = WebClient.builder()
                    .build()
                    .get()
                    .uri("http://localhost:" + server.port() + oidcConfig.redirectUri())
                    .queryParam("error", "access_denied")
                    .queryParam("error_description", "original error\r\n" + forgedLine)
                    .request()
                    .await(Duration.ofSeconds(10));

            try {
                assertThat(response.status(), is(Http.Status.BAD_REQUEST_400));
                response.content().as(String.class).await(Duration.ofSeconds(10));
            } finally {
                response.close().await(Duration.ofSeconds(10));
            }

            String capturedLog = waitForLog(capturedLogs, "original error");
            assertThat(capturedLog, containsString("Error description: original error\\r\\n" + forgedLine));
            assertThat(Arrays.asList(capturedLog.split("\\R")), not(hasItem(forgedLine)));
        } finally {
            if (server.isRunning()) {
                server.shutdown().await(Duration.ofSeconds(10));
            }
            System.setOut(originalOut);
            restoreLoggingConfiguration();
            captureOut.close();
        }
    }

    @Test
    void testOutbound() {
        String tokenContent = "huhahihohyhe";
        TokenCredential tokenCredential = TokenCredential.builder()
                .token(tokenContent)
                .build();

        Subject subject = Subject.builder()
                .addPublicCredential(TokenCredential.class, tokenCredential)
                .build();

        ProviderRequest providerRequest = Mockito.mock(ProviderRequest.class);
        SecurityContext ctx = Mockito.mock(SecurityContext.class);

        when(ctx.user()).thenReturn(Optional.of(subject));
        when(providerRequest.securityContext()).thenReturn(ctx);

        SecurityEnvironment outboundEnv = SecurityEnvironment.builder()
                .targetUri(URI.create("http://localhost:7777"))
                .path("/test")
                .build();
        EndpointConfig endpointConfig = EndpointConfig.builder().build();

        OutboundSecurityResponse response = provider.outboundSecurity(providerRequest, outboundEnv, endpointConfig)
                .toCompletableFuture()
                .join();

        List<String> authorization = response.requestHeaders().get("Authorization");
        assertThat("Authorization header", authorization, hasItem("Bearer " + tokenContent));
    }

    @Test
    void testOutboundFull() {
        String tokenContent = "huhahihohyhe";
        TokenCredential tokenCredential = TokenCredential.builder()
                .token(tokenContent)
                .build();

        Subject subject = Subject.builder()
                .addPublicCredential(TokenCredential.class, tokenCredential)
                .build();

        ProviderRequest providerRequest = Mockito.mock(ProviderRequest.class);
        SecurityContext ctx = Mockito.mock(SecurityContext.class);

        when(ctx.user()).thenReturn(Optional.of(subject));
        when(providerRequest.securityContext()).thenReturn(ctx);

        SecurityEnvironment outboundEnv = SecurityEnvironment.builder()
                .targetUri(URI.create("http://www.example.com:7777"))
                .path("/test")
                .build();
        EndpointConfig endpointConfig = EndpointConfig.builder().build();

        boolean outboundSupported = provider.isOutboundSupported(providerRequest, outboundEnv, endpointConfig);
        assertThat("Outbound should not be supported by default", outboundSupported, is(false));

        OutboundSecurityResponse response = provider.outboundSecurity(providerRequest, outboundEnv, endpointConfig)
                .toCompletableFuture()
                .join();

        assertThat("Disabled target should have empty headers", response.requestHeaders().size(), is(0));
    }

    @Test
    void testDisabledFeature() {
        OidcSupport oidcSupport = OidcSupport.builder()
                .enabled(false)
                .build();

        // make sure we can pass through its lifecycle without getting an exception
        Routing.Builder builder = Routing.builder();
        oidcSupport.update(builder);

        assertThat(oidcSupport.hashCode(), not(0));
        assertThat(oidcSupport.toString(), notNullValue());
    }

    private static TenantConfig tenantConfig(String tenantName, int tokenServerPort) {
        return TenantConfig.tenantBuilder()
                .name(tenantName)
                .clientId("id")
                .clientSecret("secret")
                .identityUri(URI.create("http://localhost:" + tokenServerPort + "/identity"))
                .tokenEndpointUri(URI.create("http://localhost:" + tokenServerPort + "/token"))
                .authorizationEndpointUri(URI.create("http://localhost:" + tokenServerPort + "/authorize"))
                .signJwk(JwkKeys.builder().build())
                .oidcMetadataWellKnown(false)
                .build();
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

        private URI logoutEndpointUri() {
            return URI.create("http://localhost:" + server.port() + "/oauth2/v1/userlogout");
        }

        private int wellKnownHits() {
            return wellKnownHits.get();
        }

        @Override
        public void close() {
            server.shutdown().await(Duration.ofSeconds(10));
        }
    }

    private static CallbackResult callbackResult(boolean useParam,
                                                 boolean useCookie,
                                                 String state,
                                                 String tenantName,
                                                 boolean encryptState) throws Exception {
        return callbackResult(useParam, useCookie, state, tenantName, encryptState, null);
    }

    private static CallbackResult callbackResult(boolean useParam,
                                                 boolean useCookie,
                                                 String state,
                                                 String tenantName,
                                                 boolean encryptState,
                                                 String paramName) throws Exception {
        return callbackResult(useParam, useCookie, state, tenantName, encryptState, paramName, false, false);
    }

    private static CallbackResult callbackResult(boolean useParam,
                                                 boolean useCookie,
                                                 String state,
                                                 String tenantName,
                                                 boolean encryptState,
                                                 String paramName,
                                                 boolean legacyStateFallback,
                                                 boolean legacyQueryParamHandoff) throws Exception {
        return callbackResult(useParam,
                              useCookie,
                              state,
                              tenantName,
                              encryptState,
                              paramName,
                              false,
                              legacyStateFallback,
                              legacyQueryParamHandoff,
                              null);
    }

    private static CallbackResult callbackResult(boolean useParam,
                                                 boolean useCookie,
                                                 String state,
                                                 String tenantName,
                                                 boolean encryptState,
                                                 String paramName,
                                                 boolean legacyStateParam,
                                                 boolean legacyStateFallback,
                                                 boolean legacyQueryParamHandoff) throws Exception {
        return callbackResult(useParam,
                              useCookie,
                              state,
                              tenantName,
                              encryptState,
                              paramName,
                              legacyStateParam,
                              legacyStateFallback,
                              legacyQueryParamHandoff,
                              null);
    }

    private static CallbackResult callbackResult(boolean useParam,
                                                 boolean useCookie,
                                                 String state,
                                                 String tenantName,
                                                 boolean encryptState,
                                                 String paramName,
                                                 boolean legacyStateParam,
                                                 boolean legacyStateFallback,
                                                 boolean legacyQueryParamHandoff,
                                                 Long stateIssuedAt) throws Exception {
        return callbackResult(useParam,
                              useCookie,
                              state,
                              tenantName,
                              encryptState,
                              paramName,
                              legacyStateParam,
                              legacyStateFallback,
                              legacyQueryParamHandoff,
                              stateIssuedAt,
                              true);
    }

    private static CallbackResult callbackResult(boolean useParam,
                                                 boolean useCookie,
                                                 String state,
                                                 String tenantName,
                                                 boolean encryptState,
                                                 String paramName,
                                                 boolean legacyStateParam,
                                                 boolean legacyStateFallback,
                                                 boolean legacyQueryParamHandoff,
                                                 Long stateIssuedAt,
                                                 boolean includeStateNonceCookie) throws Exception {
        AtomicInteger tokenRequestCount = new AtomicInteger();
        AtomicReference<FormParams> tokenRequestParameters = new AtomicReference<>();
        WebServer tokenServer = WebServer.builder()
                .defaultSocket(socket -> socket.host("localhost"))
                .routing(routing -> routing.post("/token",
                                                 (req, res) -> req.content()
                                                         .as(FormParams.class)
                                                         .thenAccept(form -> {
                                                             tokenRequestCount.incrementAndGet();
                                                             tokenRequestParameters.set(form);
                                                             res.headers().contentType(MediaType.APPLICATION_JSON);
                                                             res.send("{\"access_token\":\"access-token\"}");
                                                         })))
                .build();
        tokenServer.start().await(Duration.ofSeconds(10));

        try {
            OidcConfig.Builder builder = OidcConfig.builder()
                    .clientId("id")
                    .clientSecret("secret")
                    .identityUri(URI.create("http://localhost:" + tokenServer.port() + "/identity"))
                    .tokenEndpointUri(URI.create("http://localhost:" + tokenServer.port() + "/token"))
                    .authorizationEndpointUri(URI.create("http://localhost:" + tokenServer.port() + "/authorize"))
                    .signJwk(JwkKeys.builder().build())
                    .oidcMetadataWellKnown(false)
                    .useParam(useParam)
                    .useCookie(useCookie)
                    .legacyStateParam(legacyStateParam)
                    .legacyStateFallback(legacyStateFallback)
                    .legacyQueryParamHandoff(legacyQueryParamHandoff);
            if (!DEFAULT_TENANT_ID.equals(tenantName)) {
                builder.addTenantConfig(tenantConfig(tenantName, tokenServer.port()));
            }
            if (paramName != null) {
                builder.paramName(paramName);
            }
            OidcConfig config = builder.build();
            Routing.Builder routing = Routing.builder();
            OidcSupport.create(config).update(routing);
            WebServer callbackServer = WebServer.builder()
                    .defaultSocket(socket -> socket.host("localhost"))
                    .addRouting(routing.build())
                    .build();
            callbackServer.start().await(Duration.ofSeconds(10));
            try {
                Optional<String> stateNonce = encryptState && !legacyStateParam
                        ? Optional.of("state-nonce")
                        : Optional.empty();
                String callbackState = callbackState(state, tenantName, encryptState, stateNonce, stateIssuedAt, config);
                String callbackUri = "http://localhost:" + callbackServer.port()
                        + config.redirectUri()
                        + "?code=code&state=" + URLEncoder.encode(callbackState, StandardCharsets.UTF_8);
                if (!DEFAULT_TENANT_ID.equals(tenantName)) {
                    callbackUri += "&" + config.tenantParamName() + "=" + tenantName;
                }
                String expectedRedirectUri = "http://localhost:" + callbackServer.port() + config.redirectUri();
                if (!DEFAULT_TENANT_ID.equals(tenantName)) {
                    expectedRedirectUri += "?" + config.tenantParamName() + "=" + tenantName;
                }

                WebClientRequestBuilder callbackRequest = WebClient.builder()
                        .build()
                        .get()
                        .uri(callbackUri)
                        .followRedirects(false);
                if (includeStateNonceCookie) {
                    stateNonce.ifPresent(it -> callbackRequest.headers()
                            .addCookie(OidcState.loginStateNonceCookieName(config.tokenCookieHandler().cookieName()), it));
                }

                WebClientResponse response = callbackRequest.request()
                        .await(Duration.ofSeconds(10));

                try {
                    String location = response.headers()
                            .first(Http.Header.LOCATION)
                            .orElse(null);
                    if (response.status().equals(Http.Status.TEMPORARY_REDIRECT_307)) {
                        FormParams tokenParams = tokenRequestParameters.get();
                        assertThat(tokenRequestCount.get(), is(1));
                        assertThat(tokenParams.first("grant_type").orElseThrow(), is("authorization_code"));
                        assertThat(tokenParams.first("code").orElseThrow(), is("code"));
                        assertThat(tokenParams.first("redirect_uri").orElseThrow(), is(expectedRedirectUri));
                    }
                    List<String> setCookies = response.headers().all(Http.Header.SET_COOKIE);
                    Optional<String> nonce = queryResultNonce(setCookies, config);
                    boolean requireNonce = config.useCookie() && !config.legacyQueryParamHandoff();
                    Optional<String> accessToken = Optional.ofNullable(location)
                            .flatMap(it -> queryResultAccessToken(it, config, tenantName, nonce, requireNonce));
                    Optional<String> accessTokenWithoutNonce = Optional.ofNullable(location)
                            .flatMap(it -> queryResultAccessToken(it,
                                                                  config,
                                                                  tenantName,
                                                                  Optional.empty(),
                                                                  requireNonce));
                    return new CallbackResult(location,
                                              setCookies,
                                              response.status(),
                                              tokenRequestCount.get(),
                                              accessToken,
                                              accessTokenWithoutNonce,
                                              nonce);
                } finally {
                    response.close().await(Duration.ofSeconds(10));
                }
            } finally {
                callbackServer.shutdown().await(Duration.ofSeconds(10));
            }
        } finally {
            tokenServer.shutdown().await(Duration.ofSeconds(10));
        }
    }

    private static String callbackState(String state,
                                        String tenantName,
                                        boolean encryptState,
                                        Optional<String> stateNonce,
                                        Long stateIssuedAt,
                                        OidcConfig config) {
        if (!encryptState) {
            return state;
        }
        if (stateIssuedAt == null) {
            return stateNonce.map(it -> OidcState.createLoginState(state, config.tenantConfig(tenantName), it))
                    .orElseGet(() -> OidcState.createLoginState(state, config.tenantConfig(tenantName)));
        }
        return stateNonce.map(it -> OidcState.createLoginState(state, config.tenantConfig(tenantName), it, stateIssuedAt))
                .orElseGet(() -> OidcState.createLoginState(state, config.tenantConfig(tenantName), stateIssuedAt));
    }

    private static Optional<String> queryResultAccessToken(String uri, OidcConfig config, String tenantName) {
        return queryResultAccessToken(uri, config, tenantName, Optional.empty(), false);
    }

    private static Optional<String> queryResultAccessToken(String uri,
                                                           OidcConfig config,
                                                           String tenantName,
                                                           Optional<String> nonce,
                                                           boolean requireNonce) {
        List<String> values = queryParams(uri, config.paramName());
        for (int i = values.size() - 1; i >= 0; i--) {
            Optional<String> accessToken = OidcState.queryResultAccessToken(values.get(i),
                                                                            config.tenantConfig(tenantName),
                                                                            nonce,
                                                                            requireNonce);
            if (accessToken.isPresent()) {
                return accessToken;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> queryResultNonce(List<String> setCookies, OidcConfig config) {
        return nonceCookie(setCookies, OidcState.queryResultNonceCookieName(config.tokenCookieHandler().cookieName()));
    }

    private static Optional<String> loginStateNonce(List<String> setCookies, OidcConfig config) {
        return nonceCookie(setCookies, OidcState.loginStateNonceCookieName(config.tokenCookieHandler().cookieName()));
    }

    private static Optional<String> nonceCookie(List<String> setCookies, String cookieName) {
        String prefix = cookieName + "=";
        for (String setCookie : setCookies) {
            if (setCookie.startsWith(prefix)) {
                int end = setCookie.indexOf(';');
                return Optional.of(end < 0
                                           ? setCookie.substring(prefix.length())
                                           : setCookie.substring(prefix.length(), end));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> queryParam(String uri, String name) {
        List<String> values = queryParams(uri, name);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    private static List<String> queryParams(String uri, String name) {
        String toParse = uri.startsWith("http://") || uri.startsWith("https://") ? uri : "http://localhost" + uri;
        String query = URI.create(toParse).getRawQuery();
        if (query == null || query.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String param : query.split("&")) {
            int equals = param.indexOf('=');
            String paramName = equals < 0 ? param : param.substring(0, equals);
            if (name.equals(decode(paramName))) {
                values.add(decode(equals < 0 ? "" : param.substring(equals + 1)));
            }
        }
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String cookieValue(String setCookieHeader) {
        String cookieHeader = cookieHeader(setCookieHeader);
        int equals = cookieHeader.indexOf('=');
        return cookieHeader.substring(equals + 1);
    }

    private static String cookieHeader(String setCookieHeader) {
        int attributesStart = setCookieHeader.indexOf(';');
        if (attributesStart < 0) {
            return setCookieHeader;
        }
        return setCookieHeader.substring(0, attributesStart);
    }

    private static final class CallbackResult {
        private final String location;
        private final List<String> setCookies;
        private final Http.ResponseStatus status;
        private final int tokenRequestCount;
        private final Optional<String> queryResultAccessToken;
        private final Optional<String> queryResultAccessTokenWithoutNonce;
        private final Optional<String> queryResultNonce;

        private CallbackResult(String location,
                               List<String> setCookies,
                               Http.ResponseStatus status,
                               int tokenRequestCount,
                               Optional<String> queryResultAccessToken,
                               Optional<String> queryResultAccessTokenWithoutNonce,
                               Optional<String> queryResultNonce) {
            this.location = location;
            this.setCookies = setCookies;
            this.status = status;
            this.tokenRequestCount = tokenRequestCount;
            this.queryResultAccessToken = queryResultAccessToken;
            this.queryResultAccessTokenWithoutNonce = queryResultAccessTokenWithoutNonce;
            this.queryResultNonce = queryResultNonce;
        }
    }

    private static final class CapturingLogHandler extends Handler {
        private final List<String> messages = new ArrayList<>();

        private CapturingLogHandler() {
            setLevel(Level.ALL);
        }

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        private boolean contains(String message) {
            return messages.stream().anyMatch(it -> it.contains(message));
        }
    }
}
