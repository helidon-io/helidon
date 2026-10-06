/*
 * Copyright (c) 2026 Oracle and/or its affiliates. All rights reserved.
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

import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.common.http.Http;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Security;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.jwt.jwk.JwkOctet;
import io.helidon.security.providers.common.TokenCredential;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;

import org.glassfish.jersey.client.ClientProperties;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OidcRedirectHandoffTest {
    private static final JwkKeys SIGNING_KEYS = JwkKeys.builder()
            .addKey(JwkOctet.create(Json.createObjectBuilder()
                                           .add(Jwk.PARAM_KEY_TYPE, Jwk.KEY_TYPE_OCT)
                                           .add(Jwk.PARAM_KEY_ID, "test-key")
                                           .add(Jwk.PARAM_ALGORITHM, JwkOctet.ALG_HS256)
                                           .add(JwkOctet.PARAM_OCTET_KEY, Base64.getUrlEncoder().encodeToString(
                                                   "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)))
                                           .build()))
            .build();
    private static final String ACCESS_TOKEN = token(Instant.now().plusSeconds(3600));

    @Test
    void queryRedirectDoesNotExposeAccessToken() throws Exception {
        try (Fixture fixture = new Fixture(true, false)) {
            AuthenticationResponse login = fixture.login("/home");
            String state = queryParam(login.responseHeaders().get("Location").get(0), "state");
            try (Response response = fixture.callback(state, cookies(login))) {
                assertThat(response.getStatus(), is(307));
                String location = response.getHeaderString("Location");
                assertThat(location, startsWith("/home?accessToken="));
                assertThat(location, not(containsString(ACCESS_TOKEN)));
                assertThat(queryParam(location, "accessToken"), startsWith("h."));
                assertThat(fixture.tokenRequests.get(), is(1));
                assertThat(fixture.tokenBody.get(), containsString("grant_type=authorization_code"));
            }
        }
    }

    @Test
    void unboundCallbackIsRejectedBeforeTokenExchange() throws Exception {
        try (Fixture fixture = new Fixture(true, false);
             Response response = fixture.callback("/home", "")) {
            assertThat(response.getStatus(), is(401));
            assertThat(fixture.tokenRequests.get(), is(0));
        }
    }

    @Test
    void offOriginCallbackWithoutLoginIsRejectedBeforeTokenExchange() throws Exception {
        try (Fixture fixture = new Fixture(false, true);
             Response response = fixture.callback("https://untrusted.example/return", "")) {
            assertThat(response.getStatus(), is(401));
            assertThat(response.getHeaderString("Location"), nullValue());
            assertThat(response.getHeaderString("Set-Cookie"), nullValue());
            assertThat(fixture.tokenRequests.get(), is(0));
        }
    }

    @Test
    void stateAndCodeFailuresHaveTheSameResponse() throws Exception {
        try (Fixture fixture = new Fixture(false, true)) {
            AuthenticationResponse login = fixture.login("/home");
            String state = queryParam(login.responseHeaders().get("Location").get(0), "state");
            fixture.rejectToken.set(true);
            try (Response invalidState = fixture.callback("/home", cookies(login));
                 Response invalidCode = fixture.callback(state, cookies(login))) {
                assertAll(() -> assertThat(invalidState.getStatus(), is(401)),
                          () -> assertThat(invalidCode.getStatus(), is(401)),
                          () -> assertThat(invalidState.readEntity(String.class), is("Not a valid authorization code")),
                          () -> assertThat(invalidCode.readEntity(String.class), is("Not a valid authorization code")));
                assertThat(fixture.tokenRequests.get(), is(1));
            }
        }
    }

    @Test
    void defaultCookieLoginDoesNotAddQueryHandoff() throws Exception {
        try (Fixture fixture = new Fixture(false, true)) {
            AuthenticationResponse login = fixture.login("/home");
            String state = queryParam(login.responseHeaders().get("Location").get(0), "state");
            assertThat(state, not(containsString("/home")));
            try (Response response = fixture.callback(state, cookies(login))) {
                assertThat(response.getStatus(), is(307));
                assertThat(response.getHeaderString("Location"), is("/home?h_ra=1"));
                String cookie = namedCookie(response, fixture.config.cookieName());
                assertAuthenticated(fixture.authenticate("/home", cookie));
            }
        }
    }

    @Test
    void browserLoginPreservesEncodedOriginalUri() throws Exception {
        String path = "/documents/a%20b%2Fc%3Fd?view=a%2Bb";
        try (Fixture fixture = new Fixture(false, true);
             Response login = fixture.get(path, "")) {
            assertThat(login.getStatus(), is(307));
            String state = queryParam(login.getHeaderString("Location"), "state");
            String cookie = cookieHeader(login.getStringHeaders().get("Set-Cookie"));
            try (Response response = fixture.callback(state, cookie)) {
                assertThat(response.getStatus(), is(307));
                assertThat(response.getHeaderString("Location"), is(path + "&h_ra=1"));
                try (Response resource = fixture.get(response.getHeaderString("Location"),
                                                     namedCookie(response, fixture.config.cookieName()))) {
                    assertThat(resource.getStatus(), is(200));
                    assertThat(resource.readEntity(String.class), is("protected resource"));
                }
            }
        }
    }

    @Test
    void missingOrInvalidOriginalUriHeaderPreservesRawTargetUri() throws Exception {
        String path = "/documents/a%20b%2Fc%3Fd?view=a%2Bb";
        try (Fixture fixture = new Fixture(false, true)) {
            for (String header : Arrays.asList(null, "/documents/a b", "https://attacker.example/")) {
                AuthenticationResponse login = fixture.authenticate(path, "", header);
                String state = queryParam(login.responseHeaders().get("Location").get(0), "state");
                try (Response response = fixture.callback(state, cookies(login))) {
                    assertThat(response.getStatus(), is(307));
                    assertThat(response.getHeaderString("Location"), is(path + "&h_ra=1"));
                }
            }
        }
    }

    @Test
    void nonLocalOriginalUriFallsBackToDefaultRedirect() throws Exception {
        try (Fixture fixture = new Fixture(false, true)) {
            AuthenticationResponse login = fixture.authenticate("//attacker.example/", "", null);
            String state = queryParam(login.responseHeaders().get("Location").get(0), "state");
            try (Response response = fixture.callback(state, cookies(login))) {
                assertThat(response.getStatus(), is(307));
                assertThat(response.getHeaderString("Location"), is("/index.html?h_ra=1"));
            }
        }
    }

    @Test
    void encryptedQueryHandoffAuthenticatesWithoutTokenCookies() throws Exception {
        try (Fixture fixture = new Fixture(true, false)) {
            AuthenticationResponse login = fixture.login("/home");
            try (Response response = fixture.callback(queryParam(login.responseHeaders().get("Location").get(0), "state"),
                                                      cookies(login))) {
                assertThat(response.getStatus(), is(307));
                String location = response.getHeaderString("Location");
                assertAuthenticated(fixture.authenticate(location, ""));
                // The source handoff is short-lived, not consumed on first use.
                assertAuthenticated(fixture.authenticate(location, ""));
                assertAuthenticated(fixture.authenticateMapped(queryParam(location, "accessToken"), ""));
            }
        }
    }

    @Test
    void encryptedHandoffWithCookiesRequiresMatchingNonce() throws Exception {
        try (Fixture fixture = new Fixture(true, true)) {
            AuthenticationResponse login = fixture.login("/home");
            try (Response response = fixture.callback(queryParam(login.responseHeaders().get("Location").get(0), "state"),
                                                      cookies(login))) {
                String location = response.getHeaderString("Location");
                String nonceName = OidcState.queryResultNonceCookieName(fixture.config.cookieName());
                String nonce = namedCookie(response, nonceName);
                assertThat(nonce, startsWith(nonceName + "="));
                // Deliberately omit the session token cookie to exercise query-handoff authentication.
                assertAuthenticated(fixture.authenticate(location, nonce));
                assertAuthenticated(fixture.authenticateMapped(queryParam(location, "accessToken"), nonce));
                assertThat(fixture.authenticate(location, "").status(), not(SecurityResponse.SecurityStatus.SUCCESS));
                assertThat(fixture.authenticate(location, nonceName + "=wrong").status(),
                           not(SecurityResponse.SecurityStatus.SUCCESS));
            }
        }
    }

    @Test
    void invalidHandoffCanFallBackToValidTokenCookie() throws Exception {
        try (Fixture fixture = new Fixture(true, true)) {
            assertAuthenticated(fixture.authenticate("/home?accessToken=h.invalid",
                                                      fixture.config.cookieName() + "=" + ACCESS_TOKEN));
        }
    }

    @Test
    void configuredQueryNameAndExistingValuesArePreserved() throws Exception {
        try (Fixture fixture = new Fixture(true, false, builder -> builder.paramName("custom-token"))) {
            String expiredToken = token(Instant.now().minusSeconds(60));
            AuthenticationResponse login = fixture.login("/home?custom-token=" + expiredToken);
            try (Response response = fixture.callback(queryParam(login.responseHeaders().get("Location").get(0), "state"),
                                                      cookies(login))) {
                String location = response.getHeaderString("Location");
                assertThat(location, containsString("custom-token=" + expiredToken + "&custom-token=h."));
                assertThat(location, not(containsString(ACCESS_TOKEN)));
                assertAuthenticated(fixture.authenticate(location, ""));
                assertThat(fixture.authenticate(location + "&custom-token=h.invalid", "").status(),
                           not(SecurityResponse.SecurityStatus.SUCCESS));
            }
        }
    }

    @Test
    void legacyQueryHandoffRemainsExplicitlyAvailable() throws Exception {
        for (boolean cookie : Arrays.asList(false, true)) {
            try (Fixture fixture = new Fixture(true, cookie, builder -> builder.legacyQueryParamHandoff(true))) {
                AuthenticationResponse login = fixture.login("/home");
                try (Response response = fixture.callback(queryParam(login.responseHeaders().get("Location").get(0), "state"),
                                                          cookies(login))) {
                    String location = response.getHeaderString("Location");
                    assertThat(queryParam(location, "accessToken"), is(ACCESS_TOKEN));
                    assertAuthenticated(fixture.authenticate(location, ""));
                    assertThat(queryParams(location, "h_ra"), is(Collections.singletonList("1")));
                }
            }
        }
    }

    @Test
    void rejectsMissingMismatchedTamperedAndExpiredLoginState() throws Exception {
        try (Fixture fixture = new Fixture(true, false)) {
            AuthenticationResponse login = fixture.login("/home");
            String state = queryParam(login.responseHeaders().get("Location").get(0), "state");
            String cookie = cookies(login);
            String nonce = cookie.substring(cookie.indexOf('=') + 1);
            long now = Instant.now().getEpochSecond();
            String expired = OidcStateTest.encrypt(Json.createObjectBuilder()
                                                          .add("r", "/home").add("n", nonce)
                                                          .add("i", now - 400).add("e", now - 100).build(),
                                                  fixture.config.clientSecret());
            for (String invalid : Arrays.asList("", "plain-state", "https://attacker.example/", state + "x", expired)) {
                try (Response response = fixture.callback(invalid, cookie)) {
                    assertThat(response.getStatus(), is(401));
                    assertThat(response.getHeaderString("Location"), nullValue());
                }
            }
            try (Response response = fixture.callback(state, "")) {
                assertThat(response.getStatus(), is(401));
            }
            try (Response response = fixture.callback(state,
                                                      OidcState.loginStateNonceCookieName(fixture.config.cookieName()) + "=wrong")) {
                assertThat(response.getStatus(), is(401));
            }
            assertThat(fixture.tokenRequests.get(), is(0));
        }
    }

    @Test
    void expiredQueryHandoffIsNotAcceptedAsRawToken() throws Exception {
        try (Fixture fixture = new Fixture(true, false)) {
            long now = Instant.now().getEpochSecond();
            String expired = "h." + OidcStateTest.encrypt(Json.createObjectBuilder()
                                                                 .add("a", ACCESS_TOKEN)
                                                                 .add("i", now - 120).add("e", now - 60).build(),
                                                         fixture.config.clientSecret());
            assertThat(fixture.authenticate("/home?accessToken=" + expired, "").status(),
                       not(SecurityResponse.SecurityStatus.SUCCESS));
            assertThat(fixture.authenticateMapped(expired, "").status(), not(SecurityResponse.SecurityStatus.SUCCESS));
        }
    }

    @Test
    void legacyStateSwitchAllowsOnlyLocalRawState() throws Exception {
        try (Fixture fixture = new Fixture(true, false, builder -> builder.legacyStateParam(true))) {
            AuthenticationResponse login = fixture.login("/home");
            assertThat(queryParam(login.responseHeaders().get("Location").get(0), "state"), is("/home"));
            assertThat(cookies(login), is(""));
            try (Response response = fixture.callback("/home", "")) {
                assertThat(response.getStatus(), is(307));
                assertThat(response.getHeaderString("Location"), not(containsString(ACCESS_TOKEN)));
            }
            try (Response response = fixture.callback("https://attacker.example/", "")) {
                assertThat(response.getStatus(), is(401));
            }
            assertThat(fixture.tokenRequests.get(), is(1));
        }
    }

    @Test
    void legacyStateFallbackAcceptsOlderUnboundEncryptedState() throws Exception {
        try (Fixture fixture = new Fixture(false, true, builder -> builder.legacyStateFallback(true))) {
            long now = Instant.now().getEpochSecond();
            String state = OidcStateTest.encrypt(Json.createObjectBuilder().add("r", "/home")
                                                        .add("i", now).add("e", now + 300).build(),
                                                fixture.config.clientSecret());
            try (Response response = fixture.callback(state, "")) {
                assertThat(response.getStatus(), is(307));
                assertThat(response.getHeaderString("Location"), is("/home?h_ra=1"));
            }
            try (Response response = fixture.callback("/local", "")) {
                assertThat(response.getStatus(), is(307));
            }
            try (Response response = fixture.callback("//attacker.example/", "")) {
                assertThat(response.getStatus(), is(401));
            }
            assertThat(fixture.tokenRequests.get(), is(2));
        }
    }

    @Test
    void loginNonceCookieUsesCallbackPathAndBoundedLifetime() throws Exception {
        try (Fixture fixture = new Fixture(false, true, builder -> builder.redirectUri("/custom/callback")
                .cookieName("TEST_SESSION").cookiePath("/application").cookieMaxAgeSeconds(7200).cookieSameSite("Strict"))) {
            AuthenticationResponse login = fixture.login("/home");
            String setCookie = login.responseHeaders().get("Set-Cookie").get(0);
            assertThat(setCookie, startsWith("TEST_SESSION_STATE_NONCE="));
            assertThat(setCookie, containsString("Max-Age=300"));
            assertThat(setCookie, containsString("Path=/custom/callback"));
            assertThat(setCookie, containsString("SameSite=Lax"));
            assertThat(setCookie, not(containsString("Max-Age=7200")));
            try (Response response = fixture.callback(queryParam(login.responseHeaders().get("Location").get(0), "state"),
                                                      cookies(login))) {
                assertThat(response.getStatus(), is(307));
                String removal = response.getStringHeaders().get("Set-Cookie").stream()
                        .filter(value -> value.startsWith("TEST_SESSION_STATE_NONCE="))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Missing nonce removal cookie"));
                assertThat(removal, containsString("Path=/custom/callback"));
            }
        }
    }

    private static String queryParam(String uri, String name) throws Exception {
        List<String> values = queryParams(uri, name);
        if (!values.isEmpty()) {
            return values.get(0);
        }
        throw new IllegalStateException("Missing query parameter " + name);
    }

    private static String token(Instant expiry) {
        return SignedJwt.sign(Jwt.builder()
                                      .algorithm(JwkOctet.ALG_HS256)
                                      .keyId("test-key")
                                      .issuer("https://issuer.example")
                                      .audience("test-client")
                                      .subject("test-user")
                                      .expirationTime(expiry)
                                      .build(), SIGNING_KEYS).tokenContent();
    }

    private static List<String> queryParams(String uri, String name) throws Exception {
        List<String> values = new ArrayList<>();
        String query = URI.create(uri).getRawQuery();
        if (query != null) {
            for (String part : query.split("&")) {
                String[] pair = part.split("=", 2);
                if (URLDecoder.decode(pair[0], "UTF-8").equals(name)) {
                    values.add(pair.length == 2 ? URLDecoder.decode(pair[1], "UTF-8") : "");
                }
            }
        }
        return values;
    }

    private static String cookies(AuthenticationResponse login) {
        return cookieHeader(login.responseHeaders().getOrDefault("Set-Cookie", Collections.emptyList()));
    }

    private static String cookieHeader(List<String> setCookies) {
        return setCookies.stream().map(value -> value.split(";", 2)[0]).collect(Collectors.joining("; "));
    }

    private static String namedCookie(Response response, String name) {
        return response.getStringHeaders().get("Set-Cookie").stream()
                .filter(value -> value.startsWith(name + "="))
                .map(value -> value.split(";", 2)[0])
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing cookie " + name));
    }

    private static void assertAuthenticated(AuthenticationResponse response) {
        assertThat(response.description().orElse("Authentication failed"), response.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.user().get().principal().getName(), is("test-user"));
        assertThat(response.user().get().publicCredential(TokenCredential.class).get().token(), is(ACCESS_TOKEN));
    }

    private static final class Fixture implements AutoCloseable {
        private final AtomicBoolean rejectToken = new AtomicBoolean();
        private final AtomicInteger tokenRequests = new AtomicInteger();
        private final AtomicReference<String> tokenBody = new AtomicReference<>();
        private final List<AutoCloseable> resources = new ArrayList<>();
        private final WebServer tokenServer;
        private final WebServer callbackServer;
        private final Client client;
        private final OidcProvider provider;
        private final OidcConfig config;

        private Fixture(boolean query, boolean cookie) throws Exception {
            this(query, cookie, UnaryOperator.identity());
        }

        private Fixture(boolean query, boolean cookie, UnaryOperator<OidcConfig.Builder> options) throws Exception {
            try {
                tokenServer = WebServer.create(ServerConfiguration.builder().bindAddress(InetAddress.getByName("127.0.0.1")),
                                               Routing.builder().post("/token", (request, response) ->
                                                       request.content().as(String.class).thenAccept(body -> {
                                                           tokenBody.set(body);
                                                           tokenRequests.incrementAndGet();
                                                           response.headers().add(Http.Header.CONTENT_TYPE, "application/json");
                                                           if (rejectToken.get()) {
                                                               response.status(Http.Status.BAD_REQUEST_400);
                                                               response.send("{\"error\":\"invalid_grant\"}");
                                                               return;
                                                           }
                                                           response.send(Json.createObjectBuilder()
                                                                                 .add("access_token", ACCESS_TOKEN)
                                                                                 .add("expires_in", 3600)
                                                                                 .build().toString());
                                                       })));
                resources.add(() -> tokenServer.shutdown().toCompletableFuture().get(10, TimeUnit.SECONDS));
                tokenServer.start().toCompletableFuture().get(10, TimeUnit.SECONDS);
                config = options.apply(OidcConfig.builder()
                                               .clientId("test-client")
                                               .clientSecret("test-secret")
                                               .identityUri(URI.create("http://127.0.0.1:" + tokenServer.port()))
                                               .tokenEndpointUri(URI.create("http://127.0.0.1:" + tokenServer.port() + "/token"))
                                               .authorizationEndpointUri(URI.create("http://127.0.0.1:" + tokenServer.port()
                                                                                           + "/authorize"))
                                               .frontendUri("http://127.0.0.1")
                                               .oidcMetadataWellKnown(false)
                                               .issuer("https://issuer.example")
                                               .audience("test-client")
                                               .signJwk(SIGNING_KEYS)
                                               .useParam(query)
                                               .useCookie(cookie)).build();
                resources.add(config.generalClient()::close);
                resources.add(config.appClient()::close);
                provider = OidcProvider.create(config);
                callbackServer = WebServer.create(
                        ServerConfiguration.builder().bindAddress(InetAddress.getByName("127.0.0.1")),
                        Routing.builder()
                                .register(OidcSupport.create(config))
                                .register(WebSecurity.create(Security.builder().addProvider(provider).build()))
                                .any(WebSecurity.authenticate())
                                .any((request, response) -> response.send("protected resource")));
                resources.add(() -> callbackServer.shutdown().toCompletableFuture().get(10, TimeUnit.SECONDS));
                callbackServer.start().toCompletableFuture().get(10, TimeUnit.SECONDS);
                client = ClientBuilder.newBuilder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .property(ClientProperties.FOLLOW_REDIRECTS, false)
                        .build();
                resources.add(client::close);
            } catch (Exception | Error failure) {
                try {
                    close();
                } catch (Exception cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }

        @Override
        public void close() throws Exception {
            Exception failure = null;
            for (int i = resources.size() - 1; i >= 0; i--) {
                try {
                    resources.remove(i).close();
                } catch (Exception cleanup) {
                    if (failure == null) {
                        failure = cleanup;
                    } else {
                        failure.addSuppressed(cleanup);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        private AuthenticationResponse login(String path) {
            return authenticate(path, "");
        }

        private AuthenticationResponse authenticate(String path, String cookie) {
            return authenticate(path, cookie, path);
        }

        private AuthenticationResponse authenticate(String path, String cookie, String originalUri) {
            SecurityEnvironment.Builder environment = SecurityEnvironment.builder()
                    .targetUri(URI.create("http://127.0.0.1:" + callbackServer.port() + path))
                    .header("Cookie", cookie);
            if (originalUri != null) {
                environment.header(Security.HEADER_ORIG_URI, originalUri);
            }
            ProviderRequest request = mock(ProviderRequest.class);
            when(request.env()).thenReturn(environment.build());
            when(request.endpointConfig()).thenReturn(EndpointConfig.builder().build());
            return provider.syncAuthenticate(request);
        }

        private AuthenticationResponse authenticateMapped(String value, String cookie) {
            ProviderRequest request = mock(ProviderRequest.class);
            when(request.env()).thenReturn(SecurityEnvironment.builder()
                    .targetUri(URI.create("http://127.0.0.1:" + callbackServer.port() + "/home"))
                    .header(OidcConfig.PARAM_HEADER_NAME, value)
                    .header("Cookie", cookie)
                    .build());
            when(request.endpointConfig()).thenReturn(EndpointConfig.builder().build());
            return provider.syncAuthenticate(request);
        }

        private Response callback(String state, String cookie) {
            WebTarget target = client.target("http://127.0.0.1:" + callbackServer.port() + config.redirectUri())
                    .queryParam("code", "test-code")
                    .queryParam("state", state);
            return target.request().header("Cookie", cookie).get();
        }

        private Response get(String path, String cookie) {
            return client.target("http://127.0.0.1:" + callbackServer.port() + path)
                    .request().header("Cookie", cookie).get();
        }
    }
}
