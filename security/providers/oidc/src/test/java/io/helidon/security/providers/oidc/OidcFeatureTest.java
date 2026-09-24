/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.helidon.common.configurable.AllowList;
import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriQuery;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.DirectHandler;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.RequestException;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.json.JsonObject;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.Subject;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.common.TokenCredential;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.TenantConfig;
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mockito;

import static io.helidon.security.providers.oidc.common.RedirectAttemptCounterStrategy.COOKIE;
import static io.helidon.security.providers.oidc.common.RedirectAttemptCounterStrategy.NONE;
import static io.helidon.security.providers.oidc.common.spi.TenantConfigFinder.DEFAULT_TENANT_ID;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link io.helidon.security.providers.oidc.OidcFeature}.
 */
class OidcFeatureTest {
    private static final String PARAM_NAME = "my-param-attempts";
    private static final String TENANT_REALM = "tenant-realm";
    private static final URI DEFAULT_LOGOUT_ENDPOINT = URI.create("http://idp.example.test/logout");
    private static final String ID_TOKEN = SignedJwt.sign(
            Jwt.builder()
                    .algorithm("none")
                    .addPayloadClaim("padding", "a".repeat(512))
                    .build(),
            Jwk.NONE_JWK)
            .tokenContent();

    private final OidcConfig oidcConfig = OidcConfig.builder()
            .clientId("id")
            .clientSecret("secret")
            .identityUri(URI.create("http://localhost:7774/identity"))
            .tokenEndpointUri(URI.create("http://localhost:7774/token"))
            .authorizationEndpointUri(URI.create("http://localhost:7774/authorize"))
            .signJwk(JwkKeys.builder().build())
            .oidcMetadataWellKnown(false)
            .useCookie(false)
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
    private final OidcConfig oidcConfigDisabledParam = OidcConfig.builder()
            .clientId("id")
            .clientSecret("secret")
            .identityUri(URI.create("http://localhost:7774/identity"))
            .tokenEndpointUri(URI.create("http://localhost:7774/token"))
            .authorizationEndpointUri(URI.create("http://localhost:7774/authorize"))
            .signJwk(JwkKeys.builder().build())
            .oidcMetadataWellKnown(false)
            .redirectAttemptCounterStrategy(NONE)
            .build();
    private final OidcConfig oidcConfigCookieCounter = OidcConfig.builder()
            .clientId("id")
            .clientSecret("secret")
            .identityUri(URI.create("http://localhost:7774/identity"))
            .tokenEndpointUri(URI.create("http://localhost:7774/token"))
            .authorizationEndpointUri(URI.create("http://localhost:7774/authorize"))
            .signJwk(JwkKeys.builder().build())
            .oidcMetadataWellKnown(false)
            .redirectAttemptCounterStrategy(COOKIE)
            .build();

    private final OidcFeature oidcFeature = OidcFeature.create(oidcConfig);
    private final OidcFeature oidcFeatureCustomParam = OidcFeature.create(oidcConfigCustomParam);
    private final OidcFeature oidcFeatureDisabledParam = OidcFeature.create(oidcConfigDisabledParam);
    private final OidcFeature oidcFeatureCookieCounter = OidcFeature.create(oidcConfigCookieCounter);
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

    @Test
    void testLogoutRejectsInvalidState() throws Exception {
        String state = "probe%0d%0aX-Reproducer:%20injected";
        String injectedHeader = "X-Reproducer: injected";
        String response = logoutResponse(state);

        assertThat(response, startsWith("HTTP/1.1 400"));
        assertThat(response, not(containsString("\r\n" + injectedHeader + "\r\n")));
    }

    @Test
    void testLogoutEncodesStateValue() throws Exception {
        String state = "ok%26post_logout_redirect_uri%3Dhttps%3A%2F%2Fexample.test%2Falternate";
        String response = logoutResponse(state);

        assertThat(response, startsWith("HTTP/1.1 307"));
        assertThat(response,
                   containsString("&state=ok%26post_logout_redirect_uri%3Dhttps%3A%2F%2Fexample.test%2Falternate\r\n"));
        assertThat(response, not(containsString("&state=ok&post_logout_redirect_uri=https://example.test/alternate")));
    }

    @Test
    void testLogoutAppendsParametersToEndpointQuery() throws Exception {
        String logoutEndpoint = "http://idp.example.test/logout?domain=identity%2Fdomain";
        String response = logoutResponse("ok", URI.create(logoutEndpoint));

        assertThat(response,
                   containsString("Location: " + logoutEndpoint
                                          + "&id_token_hint=" + ID_TOKEN
                                          + "&post_logout_redirect_uri=http://127.0.0.1:"));
    }

    @Test
    void testPostLogoutRedirectUriUsesRequestedUri() throws Exception {
        String response = logoutResponse("ok", DEFAULT_LOGOUT_ENDPOINT, ID_TOKEN, true, "https://frontend.example");

        assertThat(response, containsString("&post_logout_redirect_uri=https://frontend.example/logged-out"));
        assertThat(response, not(containsString("attacker.example")));
    }

    @Test
    void postLogoutRedirectUriNormalizesExpandedIpv6() throws Exception {
        String response = logoutResponse("ok",
                                         DEFAULT_LOGOUT_ENDPOINT,
                                         ID_TOKEN,
                                         true,
                                         "https://[0:0:0:0:0:0:0:1]",
                                         builder -> builder.frontendUriAllowList(AllowList.builder()
                                                 .addAllowed("https://[::1]").build()));

        assertThat(response, startsWith("HTTP/1.1 307"));
        assertThat(response, containsString("&post_logout_redirect_uri=https://[::1]/logged-out"));
    }

    @Test
    void postLogoutRedirectUriUsesConfiguredFrontendOrigin() throws Exception {
        String response = logoutResponse("ok",
                                         DEFAULT_LOGOUT_ENDPOINT,
                                         ID_TOKEN,
                                         true,
                                         null,
                                         builder -> builder.frontendUri("https://frontend.example/application"));

        assertThat(response, startsWith("HTTP/1.1 307"));
        assertThat(response, containsString("&post_logout_redirect_uri=https://frontend.example/logged-out"));
        assertThat(response, not(containsString("https://frontend.example/application/logged-out")));
    }

    @TestFactory
    Stream<DynamicTest> postLogoutRedirectUriPreservesEncodedPaths() {
        return Stream.of("/signed%20out", "signed%20out", "/%2Fother.example/path", "/signed%3Fout%23done%25")
                .flatMap(path -> Stream.of(
                        dynamicTest("configured frontend: " + path,
                                    () -> assertEncodedPostLogoutPath(path, "https://frontend.example/application")),
                        dynamicTest("discovered origin: " + path,
                                    () -> assertEncodedPostLogoutPath(path, null))));
    }

    @Test
    void testPostLogoutRedirectUriForceHttpsOmitsDefaultHttpPort() throws Exception {
        String response = logoutResponse("ok",
                                         DEFAULT_LOGOUT_ENDPOINT,
                                         ID_TOKEN,
                                         true,
                                         "http://frontend.example",
                                         builder -> builder.forceHttpsRedirects(true)
                                                 .frontendUriAllowList(AllowList.builder()
                                                         .addAllowed("https://frontend.example").build()));

        assertThat(response, startsWith("HTTP/1.1 307"));
        assertThat(response, containsString("&post_logout_redirect_uri=https://frontend.example/logged-out"));
        assertThat(response, not(containsString("https://frontend.example:80")));
    }

    @Test
    void testPostLogoutRedirectUriRejectsDisallowedRequestedUri() throws Exception {
        String response = logoutResponse("ok",
                                         DEFAULT_LOGOUT_ENDPOINT,
                                         ID_TOKEN,
                                         true,
                                         "https://frontend.example",
                                         builder -> builder.frontendUriAllowList(AllowList.builder()
                                                 .addAllowed("https://allowed.example").build()));

        assertThat(response, startsWith("HTTP/1.1 401"));
        assertThat(response, containsString("\r\nWWW-Authenticate: Bearer realm=\"helidon\"\r\n"));
        assertLocalOidcCookiesRemoved(response);
        assertThat(response, not(containsString("\r\nLocation:")));
    }

    @Test
    void testPostLogoutFrontendRejectionUsesTenantRealm() throws Exception {
        String response = logoutResponse("ok",
                                         DEFAULT_LOGOUT_ENDPOINT,
                                         ID_TOKEN,
                                         true,
                                         "https://frontend.example",
                                         "tenant-a",
                                         builder -> builder
                                                 .frontendUriAllowList(AllowList.builder()
                                                         .addAllowed("https://allowed.example").build())
                                                 .addTenantConfig(TenantConfig.tenantBuilder()
                                                         .name("tenant-a")
                                                         .realm(TENANT_REALM)
                                                         .clientId("id")
                                                         .clientSecret("secret")
                                                         .identityUri(URI.create("http://idp.example.test/identity"))
                                                         .logoutEndpointUri(DEFAULT_LOGOUT_ENDPOINT)
                                                         .oidcMetadataWellKnown(false)
                                                         .build()));

        assertThat(response, startsWith("HTTP/1.1 401"));
        assertThat(response, containsString("\r\nWWW-Authenticate: Bearer realm=\"" + TENANT_REALM + "\"\r\n"));
        assertLocalOidcCookiesRemoved(response);
        assertThat(response, not(containsString("\r\nLocation:")));
    }

    @Test
    void testPostLogoutRedirectUriRejectsInvalidRequestedUri() throws Exception {
        String response = logoutResponse("ok",
                                         DEFAULT_LOGOUT_ENDPOINT,
                                         ID_TOKEN,
                                         true,
                                         "ftp://frontend.example");

        assertThat(response, startsWith("HTTP/1.1 401"));
        assertThat(response, containsString("\r\nWWW-Authenticate: Bearer realm=\"helidon\"\r\n"));
        assertLocalOidcCookiesRemoved(response);
        assertThat(response, not(containsString("\r\nLocation:")));
    }

    @Test
    void postLogoutRedirectUriRejectsZeroPortOrigin() throws Exception {
        String response = logoutResponse("ok", DEFAULT_LOGOUT_ENDPOINT, ID_TOKEN, true, "https://frontend.example:0");

        assertThat(response, startsWith("HTTP/1.1 401"));
        assertThat(response, containsString("\r\nWWW-Authenticate: Bearer realm=\"helidon\"\r\n"));
        assertLocalOidcCookiesRemoved(response);
        assertThat(response, not(containsString("\r\nLocation:")));
    }

    @Test
    void testMalformedForwardedHeaderIsRejectedBeforeOidc() throws Exception {
        String response = logoutResponse("ok",
                                         DEFAULT_LOGOUT_ENDPOINT,
                                         ID_TOKEN,
                                         true,
                                         "https://[bad");

        // Malformed HTTP metadata is rejected by the unchanged server before OIDC processes the request.
        assertThat(response, startsWith("HTTP/1.1 400"));
        assertThat(response, not(containsString("\r\nLocation:")));
        String configuredResponse = logoutResponse("ok",
                                                   DEFAULT_LOGOUT_ENDPOINT,
                                                   ID_TOKEN,
                                                   true,
                                                   "https://[bad",
                                                   builder -> builder.frontendUri("https://configured.example"));
        assertThat(configuredResponse, startsWith("HTTP/1.1 400"));
    }

    @Test
    void testLogoutPropagatesRequestException() throws Exception {
        RequestException failure = RequestException.builder()
                .type(DirectHandler.EventType.OTHER)
                .status(Status.TOO_MANY_REQUESTS_429)
                .header(HeaderValues.create(HeaderNames.RETRY_AFTER, "7"))
                .build();

        String response = logoutFailure(failure, null);

        assertThat(response, startsWith("HTTP/1.1 429"));
        assertThat(response, containsString("\r\nRetry-After: 7\r\n"));
        assertLocalOidcCookiesRemoved(response);
    }

    @Test
    void testLogoutPropagatesCloseConnectionException() throws Exception {
        String response = logoutFailure(new CloseConnectionException("Close the logout connection"), null);

        assertThat(response, is(""));
    }

    @Test
    void testLogoutPropagatesServerConnectionException() throws Exception {
        String response = logoutFailure(new ServerConnectionException("Logout connection failed",
                                                                      new IOException("Socket failure")), null);

        assertThat(response, is(""));
    }

    @Test
    void testLogoutDelegatesHttpExceptionToRoutingHandler() throws Exception {
        HttpException failure = new HttpException("Logout failed", Status.CONFLICT_409);
        String response = logoutFailure(failure, routing -> routing.error(HttpException.class, (req, res, thrown) -> {
            assertThat(req.path().path(), is("/oidc/logout"));
            assertThat(thrown, sameInstance(failure));
            res.status(Status.BAD_REQUEST_400).send("Handled by routing");
        }));

        assertThat(response, startsWith("HTTP/1.1 400"));
        assertThat(response, endsWith("Handled by routing"));
        assertLocalOidcCookiesRemoved(response);
    }

    @Test
    void testLogoutKeepsUnexpectedFailurePrivate() throws Exception {
        String response = logoutFailure(new IllegalStateException("Private logout failure"), null);

        assertThat(response, startsWith("HTTP/1.1 500"));
        assertThat(response.substring(response.indexOf("\r\n\r\n") + 4), is(""));
        assertThat(response, not(containsString("Private logout failure")));
    }

    @Test
    void testLogoutRejectsInvalidCompressedIdToken() throws Exception {
        String injectedHeader = "X-Reproducer: injected";
        String invalidIdToken = ID_TOKEN + "\r\n" + injectedHeader + "\r\n";
        String response = logoutResponse("ok", DEFAULT_LOGOUT_ENDPOINT, invalidIdToken, false);

        assertThat(response, startsWith("HTTP/1.1 400"));
        assertLocalOidcCookiesRemoved(response);
        assertThat(response, not(containsString("\r\n" + injectedHeader + "\r\n")));
        assertThat(response, not(containsString("\r\nLocation:")));
    }

    @Test
    void testLogoutRejectsCompressedJweWithTooManySegments() throws Exception {
        String header = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("{\"alg\":\"RSA-OAEP\",\"enc\":\"A256GCM\"}"
                                        .getBytes(StandardCharsets.UTF_8));
        String invalidJwe = header + ".AA".repeat(20_000);
        String response = logoutResponse("ok", DEFAULT_LOGOUT_ENDPOINT, invalidJwe, true);

        assertThat(response, startsWith("HTTP/1.1 400"));
        assertLocalOidcCookiesRemoved(response);
        assertThat(response, not(containsString("\r\nLocation:")));
    }

    @Test
    void testCodeExchangeRedirectUriUsesRequestedUri() throws Exception {
        String location = callbackLocation(false, "/test", DEFAULT_TENANT_ID, "https://frontend.example");

        assertThat(location, is("/test?h_ra=1"));
    }

    @Test
    void testCodeExchangeForceHttpsRedirectUriUsesAllowedOrigin() throws Exception {
        String location = callbackLocation(false, "/test", DEFAULT_TENANT_ID, null, true);

        assertThat(location, is("/test?h_ra=1"));
    }

    @Test
    void testCodeExchangeRejectsDisallowedRequestedUri() throws Exception {
        callbackLocation(false, "/test", DEFAULT_TENANT_ID, "https://frontend.example", false, false);
    }

    @Test
    void testCodeExchangeFrontendRejectionUsesTenantRealm() throws Exception {
        callbackLocation(false, "/test", "tenant-a", "https://frontend.example", false, false);
    }

    @Test
    void testRedirectAttemptNoParams() {
        String state = "http://localhost:7145/test";
        String newState = oidcFeature.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(oidcConfig.redirectAttemptParam() + "=1"));
    }

    @Test
    void testRedirectAttemptNoParamsCustomName() {
        String state = "http://localhost:7145/test";
        String newState = oidcFeatureCustomParam.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(PARAM_NAME + "=1"));
    }

    @Test
    void testRedirectAttemptOtherParams() {
        String state = "http://localhost:7145/test?a=first&b=second";
        String newState = oidcFeature.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(oidcConfig.redirectAttemptParam() + "=1"));
    }

    @Test
    void testRedirectAttemptOtherParamsCustomName() {
        String state = "http://localhost:7145/test?a=first&b=second";
        String newState = oidcFeatureCustomParam.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(PARAM_NAME + "=1"));
    }

    @Test
    void testRedirectAttemptParams() {
        String state = "http://localhost:7145/test?a=first&b=second&" + oidcConfig.redirectAttemptParam() + "=1";
        String newState = oidcFeature.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(oidcConfig.redirectAttemptParam() + "=2"));
    }

    @Test
    void testRedirectAttemptParamsCustomName() {
        String state = "http://localhost:7145/test?a=first&b=second&" + PARAM_NAME + "=1";
        String newState = oidcFeatureCustomParam.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(PARAM_NAME + "=2"));
    }

    @Test
    void testRedirectAttemptParamsInMiddle() {
        String state = "http://localhost:7145/test?a=first&" + oidcConfig.redirectAttemptParam() + "=1&b=second";
        String newState = oidcFeature.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(oidcConfig.redirectAttemptParam() + "=2&b=second"));
    }

    @Test
    void testRedirectAttemptParamsInMiddleCustomName() {
        String state = "http://localhost:7145/test?a=first&" + PARAM_NAME + "=11&b=second";
        String newState = oidcFeatureCustomParam.increaseRedirectCounter(state);

        assertThat(state, not(newState));
        assertThat(newState, endsWith(PARAM_NAME + "=12&b=second"));
    }

    @Test
    void testRedirectAttemptCounterDisabled() {
        String state = "http://localhost:7145/test?a=first&b=second";
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        String newState = oidcFeatureDisabledParam.updateRedirectCounter(request(), responseHeaders, state);

        assertThat(newState, is(state));
        assertThat(responseHeaders.values(HeaderNames.SET_COOKIE).isEmpty(), is(true));
    }

    @Test
    void testRedirectAttemptCookieCounter() {
        String state = "http://localhost:7145/test?a=first&b=second";
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();

        String newState = oidcFeatureCookieCounter.updateRedirectCounter(request(), responseHeaders, state);

        assertThat(newState, is(state));
        assertThat(responseHeaders.values(HeaderNames.SET_COOKIE).isEmpty(), is(true));
    }

    @Test
    void testMissingCodeErrorClearsCookieCounter() {
        String state = "state-123";
        String originalUri = "/test?resource=a";
        ServerRequest request = requestWithQuery("error=access_denied&state=" + state,
                                                 stateCookie(oidcConfigCookieCounter, originalUri, state));
        ServerResponse response = Mockito.mock(ServerResponse.class);
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        when(response.headers()).thenReturn(responseHeaders);
        when(response.status(Status.BAD_REQUEST_400)).thenReturn(response);

        oidcFeatureCookieCounter.processError(request, response);

        List<String> cookies = responseHeaders.values(HeaderNames.SET_COOKIE);
        assertThat(cookies,
                   hasItem(startsWith(oidcConfigCookieCounter.stateCookieHandler().cookieName() + "=;")));
        assertThat(cookies,
                   hasItem(startsWith(RedirectAttemptCookie.name(oidcConfigCookieCounter,
                                                                 DEFAULT_TENANT_ID,
                                                                 originalUri) + "=;")));
    }

    @Test
    void testPostLoginRedirectFallsBackForNonLocalOriginalUri() throws Exception {
        String location = callbackLocation(true, "https://example.com/test", DEFAULT_TENANT_ID);

        assertThat(location, is("/index.html?accessToken=access-token&h_ra=1"));
    }

    @Test
    void testPostLoginRedirectFallsBackForNonLocalOriginalUriWithTenant() throws Exception {
        String location = callbackLocation(true, "https://example.com/test", "tenant-one");

        assertThat(location, is("/index.html?accessToken=access-token&h_tenant=tenant-one&h_ra=1"));
    }

    @Test
    void testPostLoginRedirectPreservesLocalOriginalUri() throws Exception {
        String location = callbackLocation(false,
                                           "/raw%2Fpath?return=https%3A%2F%2Fexample.com%2Ftest",
                                           DEFAULT_TENANT_ID);

        assertThat(location, is("/raw%2Fpath?return=https%3A%2F%2Fexample.com%2Ftest&h_ra=1"));
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

        OutboundSecurityResponse response = provider.outboundSecurity(providerRequest, outboundEnv, endpointConfig);

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

        OutboundSecurityResponse response = provider.outboundSecurity(providerRequest, outboundEnv, endpointConfig);

        assertThat("Disabled target should have empty headers", response.requestHeaders().size(), is(0));
    }

    @Test
    void testDisabledFeature() {
        OidcFeature feature = OidcFeature.builder()
                .enabled(false)
                .build();

        // make sure we can pass through its lifecycle without getting an exception
        feature.beforeStart();
        HttpRouting.Builder builder = HttpRouting.builder();
        feature.setup(builder);
        feature.afterStop();

        assertThat(feature.socket(), is(WebServer.DEFAULT_SOCKET_NAME));
        assertThat(feature.socketRequired(), is(false));
        assertThat(feature.hashCode(), not(0));
        assertThat(feature.toString(), notNullValue());
    }

    @Test
    void frontendAllowListDoesNotRejectNonOidcRoutes() throws Exception {
        WebServer server = WebServer.builder()
                .routing(routing -> routing.addFeature(OidcFeature.create(oidcConfig))
                        .get("/public", (request, response) -> response.send(request.path().path())))
                .build().start();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/public")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode(), is(200));
            assertThat(response.body(), is("/public"));
        } finally {
            server.stop();
        }
    }

    private static void assertLocalOidcCookiesRemoved(String response) {
        for (String cookieName : List.of(OidcConfig.DEFAULT_COOKIE_NAME,
                                         OidcConfig.DEFAULT_ID_COOKIE_NAME,
                                         OidcConfig.DEFAULT_TENANT_COOKIE_NAME,
                                         OidcConfig.DEFAULT_REFRESH_COOKIE_NAME)) {
            assertThat(response,
                       containsString("\r\nSet-Cookie: " + cookieName
                                              + "=; Expires="));
        }
    }

    private static String logoutFailure(RuntimeException failure,
                                        Consumer<HttpRouting.Builder> routingCustomizer) throws Exception {
        return logoutResponse("ok",
                              DEFAULT_LOGOUT_ENDPOINT,
                              ID_TOKEN,
                              true,
                              null,
                              DEFAULT_TENANT_ID,
                              builder -> builder.frontendUri("https://frontend.example"),
                              routing -> {
                                  if (routingCustomizer != null) {
                                      routingCustomizer.accept(routing);
                                  }
                                  routing.addFilter((chain, req, res) -> {
                                      if (req.path().path().equals("/oidc/logout")) {
                                          AtomicBoolean first = new AtomicBoolean(true);
                                          res.beforeSend(() -> {
                                              if (first.getAndSet(false)) {
                                                  throw failure;
                                              }
                                          });
                                      }
                                      chain.proceed();
                                  });
                              });
    }

    private static String logoutResponse(String state) throws Exception {
        return logoutResponse(state, DEFAULT_LOGOUT_ENDPOINT);
    }

    private static void assertEncodedPostLogoutPath(String path, String configuredFrontendUri) throws Exception {
        String response = logoutResponse("ok",
                                         DEFAULT_LOGOUT_ENDPOINT,
                                         ID_TOKEN,
                                         true,
                                         "https://frontend.example",
                                         builder -> {
                                             builder.postLogoutUri(URI.create(path));
                                             if (configuredFrontendUri != null) {
                                                 builder.frontendUri(configuredFrontendUri);
                                             }
                                         });
        String expectedPath = path.startsWith("/") ? path : "/" + path;

        assertThat(response, startsWith("HTTP/1.1 307"));
        assertThat(response,
                   containsString("&post_logout_redirect_uri=https://frontend.example" + expectedPath + "&state=ok\r\n"));
    }

    private static String logoutResponse(String state, URI logoutEndpoint) throws Exception {
        return logoutResponse(state, logoutEndpoint, ID_TOKEN, true);
    }

    private static String logoutResponse(String state,
                                         URI logoutEndpoint,
                                         String idToken,
                                         boolean validateResponseHeaders) throws Exception {
        return logoutResponse(state, logoutEndpoint, idToken, validateResponseHeaders, null);
    }

    private static String logoutResponse(String state,
                                         URI logoutEndpoint,
                                         String idToken,
                                         boolean validateResponseHeaders,
                                         String discoveredOrigin) throws Exception {
        return logoutResponse(state, logoutEndpoint, idToken, validateResponseHeaders, discoveredOrigin, ignored -> { });
    }

    private static String logoutResponse(String state,
                                         URI logoutEndpoint,
                                         String idToken,
                                         boolean validateResponseHeaders,
                                         String discoveredOrigin,
                                         Consumer<OidcConfig.Builder> customizer) throws Exception {
        return logoutResponse(state, logoutEndpoint, idToken, validateResponseHeaders, discoveredOrigin,
                              DEFAULT_TENANT_ID, customizer);
    }

    private static String logoutResponse(String state,
                                         URI logoutEndpoint,
                                         String idToken,
                                         boolean validateResponseHeaders,
                                         String discoveredOrigin,
                                         String tenantName,
                                         Consumer<OidcConfig.Builder> customizer) throws Exception {
        return logoutResponse(state, logoutEndpoint, idToken, validateResponseHeaders, discoveredOrigin,
                              tenantName, customizer, null);
    }

    private static String logoutResponse(String state,
                                         URI logoutEndpoint,
                                         String idToken,
                                         boolean validateResponseHeaders,
                                         String discoveredOrigin,
                                         String tenantName,
                                         Consumer<OidcConfig.Builder> customizer,
                                         Consumer<HttpRouting.Builder> routingCustomizer) throws Exception {
        OidcConfig.Builder oidcConfigBuilder = OidcConfig.builder()
                .clientId("id")
                .clientSecret("secret")
                .identityUri(URI.create("http://idp.example.test/identity"))
                .tokenEndpointUri(URI.create("http://idp.example.test/token"))
                .authorizationEndpointUri(URI.create("http://idp.example.test/authorize"))
                .logoutEndpointUri(logoutEndpoint)
                .signJwk(JwkKeys.builder().build())
                .oidcMetadataWellKnown(false)
                .logoutEnabled(true)
                .logoutUri("/oidc/logout")
                .postLogoutUri(URI.create("/logged-out"))
                .cookieEncryptionEnabled(false)
                .cookieEncryptionEnabledIdToken(false)
                .cookieCompressionEnabledIdToken(true)
                .cookieEncryptionEnabledTenantName(false)
                .cookieEncryptionEnabledRefreshToken(false)
                .cookieEncryptionEnabledState(false);
        if (discoveredOrigin == null) {
            oidcConfigBuilder.frontendUriAllowList(AllowList.builder()
                    .addAllowedPattern(Pattern.compile("http://127\\.0\\.0\\.1:[0-9]+")).build());
        } else {
            oidcConfigBuilder.frontendUriAllowList(AllowList.builder()
                    .addAllowed(discoveredOrigin).build());
        }
        customizer.accept(oidcConfigBuilder);
        OidcConfig oidcConfig = oidcConfigBuilder.build();
        String idTokenCookie = oidcConfig.idTokenCookieHandler()
                .createCookie(idToken)
                .build()
                .value();
        assertThat("ID token test cookie should be compressed", idTokenCookie, startsWith("~"));
        String cookies = oidcConfig.idTokenCookieHandler().cookieName() + "=" + idTokenCookie;
        if (!DEFAULT_TENANT_ID.equals(tenantName)) {
            cookies += "; " + oidcConfig.tenantCookieHandler().cookieName() + "="
                    + oidcConfig.tenantCookieHandler().createCookie(tenantName).build().value();
        }

        Http1Config.Builder http1Config = Http1Config.builder()
                .validateResponseHeaders(validateResponseHeaders);
        HttpRouting.Builder routing = HttpRouting.builder()
                .addFeature(OidcFeature.create(oidcConfig));
        if (routingCustomizer != null) {
            routingCustomizer.accept(routing);
        }
        var serverBuilder = WebServer.builder()
                .port(0)
                .addConnectionSelector(Http1ConnectionSelector.builder()
                                               .config(http1Config.build())
                                               .build())
                .addRouting(routing);
        if (discoveredOrigin != null) {
            serverBuilder.requestedUriDiscoveryContext(context -> context
                    .trustedProxies(AllowList.builder().allowAll(true).build()));
        }
        WebServer server = serverBuilder.build().start();

        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            String host = discoveredOrigin == null ? "127.0.0.1:" + server.port() : "attacker.example";
            String forwarded = "";
            if (discoveredOrigin != null) {
                int schemeSeparator = discoveredOrigin.indexOf("://");
                forwarded = "Forwarded: for=client.example;host=" + discoveredOrigin.substring(schemeSeparator + 3)
                        + ";proto=" + discoveredOrigin.substring(0, schemeSeparator) + "\r\n";
            }
            output.write(("GET /oidc/logout?state=" + state + " HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + forwarded
                    + "Cookie: " + cookies + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n").getBytes(StandardCharsets.US_ASCII));
            output.flush();
            socket.shutdownOutput();

            InputStream input = socket.getInputStream();
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        } finally {
            server.stop();
        }
    }

    private static String callbackLocation(boolean useParam,
                                           String originalUri,
                                           String tenantName,
                                           String discoveredOrigin) throws Exception {
        return callbackLocation(useParam, originalUri, tenantName, discoveredOrigin, false);
    }

    private static String callbackLocation(boolean useParam,
                                           String originalUri,
                                           String tenantName,
                                           String discoveredOrigin,
                                           boolean forceHttpsRedirects) throws Exception {
        return callbackLocation(useParam, originalUri, tenantName, discoveredOrigin, forceHttpsRedirects, true);
    }

    private static String callbackLocation(boolean useParam,
                                           String originalUri,
                                           String tenantName,
                                           String discoveredOrigin,
                                           boolean forceHttpsRedirects,
                                           boolean allowRequestedOrigin) throws Exception {
        AtomicInteger tokenRequestCount = new AtomicInteger();
        AtomicReference<Parameters> tokenRequestParameters = new AtomicReference<>();
        WebServer tokenServer = WebServer.builder()
                .host("localhost")
                .routing(routing -> routing.post("/token",
                                                 (req, res) -> {
                                                     tokenRequestCount.incrementAndGet();
                                                     tokenRequestParameters.set(req.content().as(Parameters.class));
                                                     res.header(HeaderValues.CONTENT_TYPE_JSON)
                                                             .send("{\"access_token\":\"access-token\"}");
                                                 }))
                .build()
                .start();

        try {
            URI identityUri = URI.create("http://localhost:" + tokenServer.port() + "/identity");
            URI tokenEndpointUri = URI.create("http://localhost:" + tokenServer.port() + "/token");
            URI authorizationEndpointUri = URI.create("http://localhost:" + tokenServer.port() + "/authorize");
            OidcConfig.Builder builder = OidcConfig.builder()
                    .clientId("id")
                    .clientSecret("secret")
                    .identityUri(identityUri)
                    .tokenEndpointUri(tokenEndpointUri)
                    .authorizationEndpointUri(authorizationEndpointUri)
                    .signJwk(JwkKeys.builder().build())
                    .oidcMetadataWellKnown(false)
                    .forceHttpsRedirects(forceHttpsRedirects)
                    .useParam(useParam)
                    .useCookie(false);
            if (!DEFAULT_TENANT_ID.equals(tenantName)) {
                builder.addTenantConfig(TenantConfig.tenantBuilder()
                                                .name(tenantName)
                                                .realm(TENANT_REALM)
                                                .clientId("id")
                                                .clientSecret("secret")
                                                .identityUri(identityUri)
                                                .tokenEndpointUri(tokenEndpointUri)
                                                .authorizationEndpointUri(authorizationEndpointUri)
                                                .signJwk(JwkKeys.builder().build())
                                                .oidcMetadataWellKnown(false)
                                                .build());
            }
            if (discoveredOrigin == null) {
                builder.frontendUriAllowList(AllowList.builder()
                        .addAllowedPattern(Pattern.compile((forceHttpsRedirects ? "https" : "http")
                                                                   + "://localhost:[0-9]+")).build());
            } else {
                builder.frontendUriAllowList(AllowList.builder()
                        .addAllowed(allowRequestedOrigin ? discoveredOrigin : "https://allowed.example").build());
            }
            OidcConfig config = builder.build();
            var callbackServerBuilder = WebServer.builder()
                    .host("localhost")
                    .routing(routing -> OidcFeature.create(config).setup(routing));
            if (discoveredOrigin != null) {
                callbackServerBuilder.requestedUriDiscoveryContext(context -> context
                        .trustedProxies(AllowList.builder().allowAll(true).build()));
            }
            WebServer callbackServer = callbackServerBuilder.build().start();
            try {
                String state = "state-one";
                String stateJson = JsonObject.builder()
                        .set("state", state)
                        .set("originalUri", originalUri)
                        .build()
                        .toString();
                String encodedState = Base64.getEncoder()
                        .encodeToString(stateJson.getBytes(StandardCharsets.UTF_8));
                String stateCookie = config.stateCookieHandler().createCookie(encodedState).build().toString();
                int cookieOptions = stateCookie.indexOf(';');
                if (cookieOptions > 0) {
                    stateCookie = stateCookie.substring(0, cookieOptions);
                }

                String callbackUri = "http://localhost:" + callbackServer.port()
                        + config.redirectUri()
                        + "?code=code&state=" + state;
                if (!DEFAULT_TENANT_ID.equals(tenantName)) {
                    callbackUri += "&" + config.tenantParamName() + "=" + tenantName;
                }
                String expectedRedirectUri = (discoveredOrigin == null
                        ? (forceHttpsRedirects ? "https" : "http") + "://localhost:" + callbackServer.port()
                        : discoveredOrigin) + config.redirectUri();
                if (!DEFAULT_TENANT_ID.equals(tenantName)) {
                    expectedRedirectUri += "?" + config.tenantParamName() + "=" + tenantName;
                }

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(callbackUri))
                        .header(HeaderNames.COOKIE.defaultCase(), stateCookie);
                if (discoveredOrigin != null) {
                    requestBuilder.header(HeaderNames.FORWARDED.defaultCase(),
                                          "for=client.example;host=frontend.example;proto=https");
                }
                HttpRequest request = requestBuilder.GET().build();
                HttpResponse<Void> response = HttpClient.newHttpClient()
                        .send(request, HttpResponse.BodyHandlers.discarding());

                if (!allowRequestedOrigin) {
                    assertThat(response.statusCode(), is(Status.UNAUTHORIZED_401.code()));
                    String expectedRealm = DEFAULT_TENANT_ID.equals(tenantName) ? "helidon" : TENANT_REALM;
                    assertThat(response.headers().firstValue(HeaderNames.WWW_AUTHENTICATE.defaultCase()),
                               is(Optional.of("Bearer realm=\"" + expectedRealm + "\"")));
                    assertThat(tokenRequestCount.get(), is(0));
                    assertThat(response.headers().firstValue(HeaderNames.LOCATION.defaultCase()), is(Optional.empty()));
                    return "";
                }
                assertThat(response.statusCode(), is(Status.TEMPORARY_REDIRECT_307.code()));
                Parameters tokenParams = tokenRequestParameters.get();
                assertThat(tokenRequestCount.get(), is(1));
                assertThat(tokenParams.first("grant_type").orElseThrow(), is("authorization_code"));
                assertThat(tokenParams.first("code").orElseThrow(), is("code"));
                assertThat(tokenParams.first("redirect_uri").orElseThrow(), is(expectedRedirectUri));
                return response.headers()
                        .firstValue(HeaderNames.LOCATION.defaultCase())
                        .orElseThrow();
            } finally {
                callbackServer.stop();
            }
        } finally {
            tokenServer.stop();
        }
    }

    private static String stateCookie(OidcConfig oidcConfig, String originalUri, String state) {
        JsonObject stateJson = JsonObject.builder()
                .set("originalUri", originalUri)
                .set("state", state)
                .build();
        String encoded = Base64.getEncoder()
                .encodeToString(stateJson.toString().getBytes(StandardCharsets.UTF_8));
        return oidcConfig.stateCookieHandler()
                .createCookie(encoded)
                .build()
                .toString();
    }

    private static String callbackLocation(boolean useParam, String originalUri, String tenantName) throws Exception {
        return callbackLocation(useParam, originalUri, tenantName, null);
    }

    private ServerRequest request(String... cookies) {
        ServerRequest request = Mockito.mock(ServerRequest.class);
        WritableHeaders<?> writableHeaders = WritableHeaders.create();
        for (String cookie : cookies) {
            writableHeaders.add(HeaderNames.COOKIE, cookie);
        }
        when(request.headers()).thenReturn(ServerRequestHeaders.create(writableHeaders));
        return request;
    }

    private ServerRequest requestWithQuery(String query, String... cookies) {
        ServerRequest request = request(cookies);
        when(request.query()).thenReturn(UriQuery.create(query));
        return request;
    }
}
