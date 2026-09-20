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

package io.helidon.webclient.api.compatibility;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientRequestBase;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClientCookieManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ClientRequestBaseCompatibilityTest {
    private static final HeaderName CUSTOM_SENSITIVE_HEADER = HeaderNames.create("X-Api-Key");
    private static final HeaderName HOST_ALIAS_HEADER = HeaderNames.create("X-Host-Alias");
    private static final HeaderName PUBLIC_HEADER = HeaderNames.create("X-Public");
    private static final List<HeaderName> SENSITIVE_HEADERS = List.of(HeaderNames.AUTHORIZATION,
                                                                      HeaderNames.PROXY_AUTHORIZATION,
                                                                      HeaderNames.COOKIE,
                                                                      CUSTOM_SENSITIVE_HEADER);
    private static final HttpClientConfig CLIENT_CONFIG = HttpClientConfig.builder()
            .addRedirectSensitiveHeader(CUSTOM_SENSITIVE_HEADER)
            .build();

    @Test
    void releasedMembersKeepProtectedDescriptors() throws ReflectiveOperationException {
        var constructor = ClientRequestBase.class.getDeclaredConstructor(HttpClientConfig.class,
                                                                         WebClientCookieManager.class,
                                                                         String.class,
                                                                         Method.class,
                                                                         ClientUri.class,
                                                                         Boolean.class,
                                                                         Map.class,
                                                                         ClientUri.class,
                                                                         boolean.class);
        assertThat(constructor.getModifiers(), is(Modifier.PROTECTED));

        var sanitize = ClientRequestBase.class.getDeclaredMethod("sanitizeRedirectSensitiveHeaders",
                                                                 ClientUri.class,
                                                                 ClientRequestHeaders.class);
        assertThat(sanitize.getModifiers(), is(Modifier.PROTECTED | Modifier.FINAL));
        assertThat(sanitize.getReturnType().getName(), is(void.class.getName()));

        var crossesOrigin = ClientRequestBase.class.getDeclaredMethod("crossesRedirectOriginBoundary", ClientUri.class);
        assertThat(crossesOrigin.getModifiers(), is(Modifier.PROTECTED | Modifier.FINAL));
        assertThat(crossesOrigin.getReturnType().getName(), is(boolean.class.getName()));
    }

    @Test
    void suppliedCrossOriginFlagSanitizesLaterSameOriginHops() {
        ClientUri currentUri = uri("https://service.example/current");
        ClientUri laterUri = uri("https://service.example/later");
        var request = new ExternalClientRequest(CLIENT_CONFIG,
                                                currentUri,
                                                uri("https://service.example/previous"),
                                                true);
        ClientRequestHeaders requestHeaders = sensitiveHeaders();

        request.sanitize(currentUri, requestHeaders);

        assertSensitiveHeadersStripped(requestHeaders);
        assertThat(request.crossesOrigin(laterUri), is(true));

        var laterRequest = new ExternalClientRequest(CLIENT_CONFIG,
                                                     laterUri,
                                                     currentUri,
                                                     request.crossesOrigin(laterUri));
        ClientRequestHeaders laterHeaders = sensitiveHeaders();

        laterRequest.sanitize(laterUri, laterHeaders);

        assertSensitiveHeadersStripped(laterHeaders);
    }

    @Test
    void sameOriginSourceRetainsSensitiveHeaders() {
        ClientUri currentUri = uri("https://service.example/current");
        var request = new ExternalClientRequest(CLIENT_CONFIG,
                                                currentUri,
                                                uri("https://service.example/previous"),
                                                false);
        ClientRequestHeaders requestHeaders = sensitiveHeaders();

        request.sanitize(currentUri, requestHeaders);

        assertSensitiveHeadersRetained(requestHeaders);
    }

    @Test
    void differentOriginSourceSanitizesRequest() {
        ClientUri currentUri = uri("https://target.example/current");
        var request = new ExternalClientRequest(CLIENT_CONFIG,
                                                currentUri,
                                                uri("https://source.example/previous"),
                                                false);
        ClientRequestHeaders requestHeaders = sensitiveHeaders();

        request.sanitize(currentUri, requestHeaders);

        assertSensitiveHeadersStripped(requestHeaders);
    }

    @Test
    void boundaryQueryComparesCurrentUriWithTarget() {
        ClientUri sourceUri = uri("https://source.example/previous");
        var request = new ExternalClientRequest(CLIENT_CONFIG, uri("https://target.example/current"), sourceUri, false);

        assertThat("A later same-origin hop does not cross the current request's origin",
                   request.crossesOrigin(uri("https://target.example/later")), is(false));
        assertThat("Returning to the source crosses the current request's origin",
                   request.crossesOrigin(sourceUri), is(true));
    }

    @Test
    void boundaryQueryPreservesSuppliedCrossOriginFlag() {
        var request = new ExternalClientRequest(CLIENT_CONFIG,
                                                uri("https://service.example/current"),
                                                uri("https://service.example/previous"),
                                                true);

        assertThat(request.crossesOrigin(uri("https://service.example/later")), is(true));
        assertThat(request.crossesOrigin(uri("https://other.example/later")), is(true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void filterOptOutRetainsSensitiveHeaders(boolean crossOriginRedirect) {
        HttpClientConfig config = HttpClientConfig.builder()
                .addRedirectSensitiveHeader(CUSTOM_SENSITIVE_HEADER)
                .filterRedirectHeaders(false)
                .build();
        ClientUri currentUri = uri("https://target.example/current");
        var request = new ExternalClientRequest(config,
                                                currentUri,
                                                uri("https://source.example/previous"),
                                                crossOriginRedirect);
        ClientRequestHeaders requestHeaders = sensitiveHeaders();

        request.sanitize(currentUri, requestHeaders);

        assertSensitiveHeadersRetained(requestHeaders);
        assertThat("The filter opt-out does not change the origin-boundary query",
                   request.crossesOrigin(uri("https://other.example/later")), is(true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void requestWithoutRedirectSourceHonorsSuppliedCrossOriginFlag(boolean crossOriginRedirect) {
        ClientUri currentUri = uri("https://service.example/current");
        var request = new ExternalClientRequest(CLIENT_CONFIG, currentUri, null, crossOriginRedirect);
        ClientRequestHeaders requestHeaders = sensitiveHeaders();

        request.sanitize(currentUri, requestHeaders);

        if (crossOriginRedirect) {
            assertSensitiveHeadersStripped(requestHeaders);
        } else {
            assertSensitiveHeadersRetained(requestHeaders);
        }
        assertThat(request.crossesOrigin(uri("https://service.example/later")), is(crossOriginRedirect));
    }

    @Test
    void hostOverrideSanitizesForEffectiveOriginAndSelectsItsCookies() {
        WebClientCookieManager cookieManager = cookieManager();
        storeCookie(cookieManager, "https://uri.example/", "uri-cookie=excluded; Path=/");
        storeCookie(cookieManager, "https://virtual.example/", "virtual-cookie=current; Path=/");
        ClientUri currentUri = uri("https://uri.example/current");
        var request = new ExternalClientRequest(CLIENT_CONFIG,
                                                cookieManager,
                                                currentUri,
                                                uri("https://uri.example/previous"),
                                                false);
        ClientRequestHeaders requestHeaders = sensitiveHeaders();
        requestHeaders.set(HeaderNames.HOST, "virtual.example");

        request.sanitize(currentUri, requestHeaders);

        assertThat(requestHeaders.contains(HeaderNames.AUTHORIZATION), is(false));
        assertThat(requestHeaders.get(HeaderNames.HOST).get(), is("virtual.example"));
        assertThat(requestHeaders.get(HeaderNames.COOKIE).allValues(), is(List.of("virtual-cookie=current")));
        assertThat(requestHeaders.get(PUBLIC_HEADER).get(), is("retained"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sensitiveHostRemovalSelectsPostStripOriginCookies(boolean normalizeHeaders) {
        HttpClientConfig config = HttpClientConfig.builder()
                .addRedirectSensitiveHeader(CUSTOM_SENSITIVE_HEADER)
                .addRedirectSensitiveHeader(HeaderNames.HOST)
                .build();
        WebClientCookieManager cookieManager = cookieManager();
        storeCookie(cookieManager, "https://uri.example/", "uri-cookie=current; Path=/");
        storeCookie(cookieManager, "https://virtual.example/", "virtual-cookie=excluded; Path=/");
        ClientUri currentUri = uri("https://uri.example/current");
        ClientUri sourceUri = uri("https://source.example/previous");
        ExternalClientRequest request = normalizeHeaders
                ? new NormalizingExternalClientRequest(config, cookieManager, currentUri, sourceUri)
                : new ExternalClientRequest(config, cookieManager, currentUri, sourceUri, false);
        ClientRequestHeaders requestHeaders = sensitiveHeaders();
        requestHeaders.set(normalizeHeaders ? HOST_ALIAS_HEADER : HeaderNames.HOST, "virtual.example");

        request.sanitize(currentUri, requestHeaders);

        assertThat(requestHeaders.contains(HeaderNames.AUTHORIZATION), is(false));
        assertThat(requestHeaders.contains(HOST_ALIAS_HEADER), is(false));
        assertThat(requestHeaders.contains(HeaderNames.HOST), is(false));
        assertThat(requestHeaders.get(HeaderNames.COOKIE).allValues(), is(List.of("uri-cookie=current")));
        assertThat(requestHeaders.get(PUBLIC_HEADER).get(), is("retained"));
    }

    @Test
    void disabledAutomaticCookiesAreNotReintroduced() {
        WebClientCookieManager cookieManager = cookieManager();
        storeCookie(cookieManager, "https://target.example/", "target-cookie=excluded; Path=/");
        ClientUri currentUri = uri("https://target.example/current");
        var request = new ExternalClientRequest(CLIENT_CONFIG,
                                                cookieManager,
                                                currentUri,
                                                uri("https://source.example/previous"),
                                                false);
        request.redirectSecurityState(request.redirectSecurityState().cookiePolicy(false, Set.of()));
        ClientRequestHeaders requestHeaders = sensitiveHeaders();

        request.sanitize(currentUri, requestHeaders);

        assertSensitiveHeadersStripped(requestHeaders);
    }

    @Test
    void suppressedCookieNamesAreNotReintroduced() {
        WebClientCookieManager cookieManager = cookieManager();
        storeCookie(cookieManager, "https://target.example/", "allowed=current; Path=/");
        storeCookie(cookieManager, "https://target.example/", "suppressed=excluded; Path=/");
        ClientUri currentUri = uri("https://target.example/current");
        var request = new ExternalClientRequest(CLIENT_CONFIG,
                                                cookieManager,
                                                currentUri,
                                                uri("https://source.example/previous"),
                                                false);
        request.redirectSecurityState(request.redirectSecurityState().cookiePolicy(true, Set.of("suppressed")));
        ClientRequestHeaders requestHeaders = sensitiveHeaders();

        request.sanitize(currentUri, requestHeaders);

        assertThat(requestHeaders.contains(HeaderNames.AUTHORIZATION), is(false));
        assertThat(requestHeaders.get(HeaderNames.COOKIE).allValues(), is(List.of("allowed=current")));
        assertThat(requestHeaders.get(PUBLIC_HEADER).get(), is("retained"));
    }

    private static ClientUri uri(String uri) {
        return ClientUri.create(URI.create(uri));
    }

    private static WebClientCookieManager cookieManager() {
        return WebClientCookieManager.builder()
                .automaticStoreEnabled(true)
                .putDefaultCookie("default", "excluded")
                .build();
    }

    private static void storeCookie(WebClientCookieManager cookieManager, String uri, String cookie) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.set(HeaderNames.SET_COOKIE, cookie);
        cookieManager.response(uri(uri), ClientResponseHeaders.create(headers));
    }

    private static ClientRequestHeaders sensitiveHeaders() {
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        headers.set(HeaderNames.AUTHORIZATION, "Bearer origin-token");
        headers.set(HeaderNames.PROXY_AUTHORIZATION, "Basic proxy-token");
        headers.set(HeaderNames.COOKIE, "session=origin-cookie");
        headers.set(CUSTOM_SENSITIVE_HEADER, "origin-key");
        headers.set(PUBLIC_HEADER, "retained");
        return headers;
    }

    private static void assertSensitiveHeadersStripped(ClientRequestHeaders headers) {
        for (HeaderName name : SENSITIVE_HEADERS) {
            assertThat("Sensitive header " + name, headers.contains(name), is(false));
        }
        assertThat(headers.get(PUBLIC_HEADER).get(), is("retained"));
    }

    private static void assertSensitiveHeadersRetained(ClientRequestHeaders headers) {
        assertThat(headers.get(HeaderNames.AUTHORIZATION).get(), is("Bearer origin-token"));
        assertThat(headers.get(HeaderNames.PROXY_AUTHORIZATION).get(), is("Basic proxy-token"));
        assertThat(headers.get(HeaderNames.COOKIE).get(), is("session=origin-cookie"));
        assertThat(headers.get(CUSTOM_SENSITIVE_HEADER).get(), is("origin-key"));
        assertThat(headers.get(PUBLIC_HEADER).get(), is("retained"));
    }

    private static class ExternalClientRequest
            extends ClientRequestBase<ExternalClientRequest, HttpClientResponse> {
        private ExternalClientRequest(HttpClientConfig clientConfig,
                                      ClientUri clientUri,
                                      ClientUri redirectSourceUri,
                                      boolean crossOriginRedirect) {
            this(clientConfig,
                 WebClientCookieManager.builder().build(),
                 clientUri,
                 redirectSourceUri,
                 crossOriginRedirect);
        }

        private ExternalClientRequest(HttpClientConfig clientConfig,
                                      WebClientCookieManager cookieManager,
                                      ClientUri clientUri,
                                      ClientUri redirectSourceUri,
                                      boolean crossOriginRedirect) {
            super(clientConfig,
                  cookieManager,
                  "compatibility",
                  Method.GET,
                  clientUri,
                  null,
                  Map.of(),
                  redirectSourceUri,
                  crossOriginRedirect);
        }

        @Override
        protected HttpClientResponse doSubmit(Object entity) {
            throw new AssertionError("Request must not be submitted");
        }

        @Override
        protected HttpClientResponse doOutputStream(OutputStreamHandler outputStreamHandler) {
            throw new AssertionError("Output stream must not be requested");
        }

        private void sanitize(ClientUri requestUri, ClientRequestHeaders requestHeaders) {
            sanitizeRedirectSensitiveHeaders(requestUri, requestHeaders);
        }

        private boolean crossesOrigin(ClientUri requestUri) {
            return crossesRedirectOriginBoundary(requestUri);
        }
    }

    private static final class NormalizingExternalClientRequest extends ExternalClientRequest {
        private NormalizingExternalClientRequest(HttpClientConfig clientConfig,
                                                 WebClientCookieManager cookieManager,
                                                 ClientUri clientUri,
                                                 ClientUri redirectSourceUri) {
            super(clientConfig, cookieManager, clientUri, redirectSourceUri, false);
        }

        @Override
        protected ClientRequestHeaders normalizedRequestHeaders(ClientRequestHeaders requestHeaders) {
            ClientRequestHeaders normalized = ClientRequestHeaders.create(WritableHeaders.create(requestHeaders));
            requestHeaders.first(HOST_ALIAS_HEADER).ifPresent(host -> {
                normalized.set(HeaderNames.HOST, host);
                normalized.remove(HOST_ALIAS_HEADER);
            });
            return normalized;
        }
    }
}
