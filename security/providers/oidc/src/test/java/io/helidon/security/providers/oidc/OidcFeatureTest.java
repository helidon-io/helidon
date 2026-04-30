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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriQuery;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
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
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.common.TokenCredential;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.helidon.security.providers.oidc.common.RedirectAttemptCounterStrategy.COOKIE;
import static io.helidon.security.providers.oidc.common.RedirectAttemptCounterStrategy.NONE;
import static io.helidon.security.providers.oidc.common.spi.TenantConfigFinder.DEFAULT_TENANT_ID;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link io.helidon.security.providers.oidc.OidcFeature}.
 */
class OidcFeatureTest {
    private static final String PARAM_NAME = "my-param-attempts";

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
            OidcConfig config = OidcConfig.builder()
                    .clientId("id")
                    .clientSecret("secret")
                    .identityUri(URI.create("http://localhost:" + tokenServer.port() + "/identity"))
                    .tokenEndpointUri(URI.create("http://localhost:" + tokenServer.port() + "/token"))
                    .authorizationEndpointUri(URI.create("http://localhost:" + tokenServer.port() + "/authorize"))
                    .signJwk(JwkKeys.builder().build())
                    .oidcMetadataWellKnown(false)
                    .useParam(useParam)
                    .useCookie(false)
                    .build();
            WebServer callbackServer = WebServer.builder()
                    .host("localhost")
                    .routing(routing -> OidcFeature.create(config).setup(routing))
                    .build()
                    .start();
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
                String expectedRedirectUri = "http://localhost:" + callbackServer.port() + config.redirectUri();
                if (!DEFAULT_TENANT_ID.equals(tenantName)) {
                    expectedRedirectUri += "?" + config.tenantParamName() + "=" + tenantName;
                }

                HttpRequest request = HttpRequest.newBuilder(URI.create(callbackUri))
                        .header(HeaderNames.COOKIE.defaultCase(), stateCookie)
                        .GET()
                        .build();
                HttpResponse<Void> response = HttpClient.newHttpClient()
                        .send(request, HttpResponse.BodyHandlers.discarding());

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
}
