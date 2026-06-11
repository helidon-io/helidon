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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import io.helidon.common.http.FormParams;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
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
import io.helidon.security.providers.oidc.common.TenantConfig;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.helidon.security.providers.oidc.common.spi.TenantConfigFinder.DEFAULT_TENANT_ID;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
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

    private static CallbackResult callbackResult(boolean useParam,
                                                 boolean useCookie,
                                                 String state,
                                                 String tenantName) throws Exception {
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
            OidcConfig.Builder configBuilder = OidcConfig.builder()
                    .clientId("id")
                    .clientSecret("secret")
                    .identityUri(URI.create("http://localhost:" + tokenServer.port() + "/identity"))
                    .tokenEndpointUri(URI.create("http://localhost:" + tokenServer.port() + "/token"))
                    .authorizationEndpointUri(URI.create("http://localhost:" + tokenServer.port() + "/authorize"))
                    .signJwk(JwkKeys.builder().build())
                    .oidcMetadataWellKnown(false)
                    .useParam(useParam)
                    .useCookie(useCookie);
            if (!DEFAULT_TENANT_ID.equals(tenantName)) {
                configBuilder.addTenantConfig(tenantConfig(tenantName, tokenServer.port()));
            }
            OidcConfig config = configBuilder.build();
            Routing.Builder routing = Routing.builder();
            OidcSupport.create(config).update(routing);
            WebServer callbackServer = WebServer.builder()
                    .defaultSocket(socket -> socket.host("localhost"))
                    .addRouting(routing.build())
                    .build();
            callbackServer.start().await(Duration.ofSeconds(10));
            try {
                String callbackUri = "http://localhost:" + callbackServer.port()
                        + config.redirectUri()
                        + "?code=code&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
                if (!DEFAULT_TENANT_ID.equals(tenantName)) {
                    callbackUri += "&" + config.tenantParamName() + "=" + tenantName;
                }
                String expectedRedirectUri = "http://localhost:" + callbackServer.port() + config.redirectUri();
                if (!DEFAULT_TENANT_ID.equals(tenantName)) {
                    expectedRedirectUri += "?" + config.tenantParamName() + "=" + tenantName;
                }

                WebClientResponse response = WebClient.builder()
                        .build()
                        .get()
                        .uri(callbackUri)
                        .followRedirects(false)
                        .request()
                        .await(Duration.ofSeconds(10));

                try {
                    assertThat(response.status(), is(Http.Status.TEMPORARY_REDIRECT_307));
                    FormParams tokenParams = tokenRequestParameters.get();
                    assertThat(tokenRequestCount.get(), is(1));
                    assertThat(tokenParams.first("grant_type").orElseThrow(), is("authorization_code"));
                    assertThat(tokenParams.first("code").orElseThrow(), is("code"));
                    assertThat(tokenParams.first("redirect_uri").orElseThrow(), is(expectedRedirectUri));
                    return new CallbackResult(response.headers()
                                                      .first(Http.Header.LOCATION)
                                                      .orElseThrow(),
                                              response.headers().all(Http.Header.SET_COOKIE));
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
    void testPostLoginRedirectFallsBackForNonLocalState() throws Exception {
        CallbackResult result = callbackResult(true, false, "https://example.com/test", DEFAULT_TENANT_ID);

        assertThat(result.location, is("/index.html?accessToken=access-token&h_ra=1"));
    }

    @Test
    void testPostLoginRedirectFallsBackForNonLocalStateWithTenant() throws Exception {
        CallbackResult result = callbackResult(true, false, "https://example.com/test", "tenant-one");

        assertThat(result.location, is("/index.html?accessToken=access-token&h_tenant=tenant-one&h_ra=1"));
    }

    @Test
    void testPostLoginRedirectFallsBackForNonLocalStateWithDefaultCookies() throws Exception {
        CallbackResult result = callbackResult(false, true, "https://example.com/test", DEFAULT_TENANT_ID);

        assertThat(result.location, is("/index.html?h_ra=1"));
        assertThat(result.setCookies.stream().anyMatch(it -> it.startsWith("JSESSIONID=")), is(true));
        assertThat(result.setCookies.stream().anyMatch(it -> it.startsWith("HELIDON_TENANT=")), is(true));
    }

    @Test
    void testPostLoginRedirectFallsBackForNonLocalStateVariants() throws Exception {
        for (String state : List.of("//example.com/test",
                                    "/\\example.com/test",
                                    "/%2f%2fexample.com/test",
                                    "/%5cexample.com/test",
                                    "/test\nset-cookie: test=value")) {
            CallbackResult result = callbackResult(true, false, state, DEFAULT_TENANT_ID);

            assertThat(result.location, is("/index.html?accessToken=access-token&h_ra=1"));
        }
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
                WebClientResponse response = WebClient.builder()
                        .build()
                        .get()
                        .uri("http://localhost:" + callbackServer.port()
                                     + config.redirectUri()
                                     + "?code=code&state=/test&"
                                     + config.tenantParamName()
                                     + "=unknown")
                        .followRedirects(false)
                        .request()
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
                WebClientResponse response = WebClient.builder()
                        .build()
                        .get()
                        .uri("http://localhost:" + callbackServer.port()
                                     + config.redirectUri()
                                     + "?code=code&state=/test&"
                                     + config.tenantParamName()
                                     + "=unknown")
                        .followRedirects(false)
                        .request()
                        .await(Duration.ofSeconds(10));

                try {
                    assertThat(response.status(), is(Http.Status.TEMPORARY_REDIRECT_307));
                    assertThat("unknown tenant should use default token endpoint when fallback is enabled",
                               tokenRequestCount.get(),
                               is(1));
                    assertThat(response.headers().first(Http.Header.LOCATION).orElseThrow(),
                               is("/test?accessToken=access-token&h_tenant=unknown&h_ra=1"));
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

    private static final class CallbackResult {
        private final String location;
        private final List<String> setCookies;

        private CallbackResult(String location, List<String> setCookies) {
            this.location = location;
            this.setCookies = setCookies;
        }
    }
}
