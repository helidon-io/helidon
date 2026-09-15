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

package io.helidon.webclient.api;

import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.common.LruCache;
import io.helidon.common.tls.Tls;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.ReadableEntity;
import io.helidon.http.media.ReadableEntityBase;
import io.helidon.webclient.spi.HttpClientSpi;
import io.helidon.webclient.spi.Protocol;
import io.helidon.webclient.spi.ProtocolConfig;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

class HttpClientHeaderNormalizationTest {
    private static final HeaderName ORIGIN_ALIAS = HeaderNames.create("X-Test-Origin");

    @Test
    void usesNormalizedAuthorityForProtocolCacheWithoutChangingConfiguredHeaders() {
        Tls tls = Tls.builder().trustAll(true).build();
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("https://route.test")
                .tls(tls)
                .buildPrototype();
        TestClient client = new TestClient(config);
        HttpClientRequest request = client.method(Method.GET)
                .header(HeaderNames.HOST, "configured.test")
                .header(ORIGIN_ALIAS, "first.test");

        try (HttpClientResponse response = request.request()) {
            assertThat(response.status(), is(Status.OK_200));
        }
        request.header(ORIGIN_ALIAS, "second.test");
        try (HttpClientResponse response = request.request()) {
            assertThat(response.status(), is(Status.OK_200));
        }
        request.header(ORIGIN_ALIAS, "first.test");
        try (HttpClientResponse response = request.request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(client.cache.size(), is(2));
        SniSupport.State sni = SniSupport.tlsDefault(request.resolvedUri(), tls).state();
        for (String authority : List.of("first.test:443", "second.test:443")) {
            LoomClient.EndpointKey key = new LoomClient.EndpointKey("https",
                                                                    authority,
                                                                    null,
                                                                    tls,
                                                                    sni,
                                                                    config.proxy());
            assertThat("cached protocol for " + authority, client.cache.get(key).orElseThrow(), sameInstance(client.spi));
        }
        assertThat(request.headers().get(HeaderNames.HOST).get(), is("configured.test"));
        assertThat(request.headers().get(ORIGIN_ALIAS).get(), is("first.test"));
        assertThat(client.spi.submittedHeaders.stream().map(headers -> headers.get(HeaderNames.HOST).get()).toList(),
                   is(List.of("first.test", "second.test", "first.test")));
    }

    @Test
    void normalizesFinalServiceHeadersAndRetargetsCookies() {
        AtomicInteger serviceInvocations = new AtomicInteger();
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://route.test")
                .addService((chain, request) -> {
                    serviceInvocations.incrementAndGet();
                    assertThat(request.headers().get(HeaderNames.HOST).get(), is("configured.test"));
                    assertThat(request.headers().get(ORIGIN_ALIAS).get(), is("source.test"));
                    String cookies = String.join("; ", request.headers().get(HeaderNames.COOKIE).allValues());
                    assertThat(cookies, containsString("source="));
                    assertThat(cookies, not(containsString("configured=")));
                    request.headers().set(ORIGIN_ALIAS, "service.test");
                    return chain.proceed(request);
                })
                .buildPrototype();
        TestClient client = new TestClient(config);
        for (String host : List.of("configured", "source", "service")) {
            HttpCookie cookie = new HttpCookie(host, "cookie");
            cookie.setPath("/");
            client.cookieManager().getCookieStore().add(URI.create("http://" + host + ".test/"), cookie);
        }
        HttpClientRequest request = client.method(Method.GET)
                .header(HeaderNames.HOST, "configured.test")
                .header(ORIGIN_ALIAS, "source.test");

        for (int i = 0; i < 2; i++) {
            try (HttpClientResponse response = request.request()) {
                assertThat(response.status(), is(Status.OK_200));
            }
        }

        assertThat(serviceInvocations.get(), is(2));
        for (ClientRequestHeaders headers : client.spi.selectedHeaders) {
            assertThat(headers.get(HeaderNames.HOST).get(), is("service.test"));
            assertThat(headers.contains(ORIGIN_ALIAS), is(false));
            String cookies = String.join("; ", headers.get(HeaderNames.COOKIE).allValues());
            assertThat(cookies, containsString("service="));
            assertThat(cookies, not(containsString("source=")));
            assertThat(cookies, not(containsString("configured=")));
        }
        assertThat(client.spi.selectedHeaders.size(), is(2));
        assertThat(request.headers().get(HeaderNames.HOST).get(), is("configured.test"));
        assertThat(request.headers().get(ORIGIN_ALIAS).get(), is("source.test"));
    }

    @Test
    void sanitizesChangedNormalizedOriginAfterSyntheticRedirectWithoutProtocolDiscovery() {
        AtomicInteger serviceInvocations = new AtomicInteger();
        AtomicReference<TestClient> clientReference = new AtomicReference<>();
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://route.test")
                .addService((chain, request) -> {
                    assertThat("protocol support checks before terminal dispatch",
                               clientReference.get().spi.supportsInvocations,
                               is(0));
                    if (serviceInvocations.getAndIncrement() == 0) {
                        request.headers().set(ORIGIN_ALIAS, "source.test");
                        WritableHeaders<?> responseHeaders = WritableHeaders.create();
                        responseHeaders.set(HeaderNames.LOCATION, "/target");
                        return WebClientServiceResponse.builder()
                                .serviceRequest(request)
                                .whenComplete(new CompletableFuture<>())
                                .connection(() -> { })
                                .status(Status.SEE_OTHER_303)
                                .headers(ClientResponseHeaders.create(responseHeaders))
                                .build();
                    }
                    request.headers().set(ORIGIN_ALIAS, "target.test");
                    request.headers().set(HeaderNames.AUTHORIZATION, "target-service-secret");
                    return chain.proceed(request);
                })
                .buildPrototype();
        TestClient client = new TestClient(config);
        clientReference.set(client);

        try (HttpClientResponse response = client.method(Method.GET)
                .header(HeaderNames.AUTHORIZATION, "source-secret")
                .followRedirects(true)
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(serviceInvocations.get(), is(2));
        assertThat(client.spi.supportsInvocations, is(1));
        assertThat(client.spi.selectedHeaders.size(), is(1));
        ClientRequestHeaders headers = client.spi.selectedHeaders.getFirst();
        assertThat(headers.get(HeaderNames.HOST).get(), is("target.test"));
        assertThat(headers.contains(ORIGIN_ALIAS), is(false));
        assertThat(headers.contains(HeaderNames.AUTHORIZATION), is(false));
    }

    @Test
    void retainsNormalizedFailureOriginWhenServiceRecovers() {
        IllegalStateException preparationFailure = new IllegalStateException("preparation failed");
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://route.test")
                .addService((chain, request) -> {
                    try {
                        return chain.proceed(request);
                    } catch (IllegalStateException failure) {
                        assertThat(failure, sameInstance(preparationFailure));
                        request.headers().set(ORIGIN_ALIAS, "late.test");
                        WritableHeaders<?> responseHeaders = WritableHeaders.create();
                        responseHeaders.set(HeaderNames.SET_COOKIE, "recovered=stored; Path=/");
                        return WebClientServiceResponse.builder()
                                .serviceRequest(request)
                                .whenComplete(new CompletableFuture<>())
                                .connection(() -> { })
                                .status(Status.ACCEPTED_202)
                                .headers(ClientResponseHeaders.create(responseHeaders))
                                .build();
                    }
                })
                .buildPrototype();
        TestClient client = new TestClient(config);
        client.spi.requestPrepare = request -> {
            request.headers().set(ORIGIN_ALIAS, "prepared.test");
            throw preparationFailure;
        };

        try (HttpClientResponse response = client.method(Method.GET).request()) {
            assertThat(response.status(), is(Status.ACCEPTED_202));
        }

        assertThat(client.cookieManager().getCookieStore().get(URI.create("http://prepared.test/"))
                           .stream().map(HttpCookie::getName).toList(),
                   is(List.of("recovered")));
        assertThat(client.cookieManager().getCookieStore().get(URI.create("http://late.test/")), is(List.of()));
        assertThat(client.cookieManager().getCookieStore().get(URI.create("http://route.test/")), is(List.of()));
        assertThat(client.spi.submittedHeaders.size(), is(0));
    }

    @Test
    void retainsFailureOriginWhenServiceRecoversFromInvalidHeaders() {
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://route.test")
                .addService((chain, request) -> {
                    request.headers().set(HeaderNames.HOST, "failed.test");
                    request.headers().set(ORIGIN_ALIAS, "");
                    try {
                        return chain.proceed(request);
                    } catch (IllegalArgumentException failure) {
                        assertThat(failure.getMessage(), is("Test origin must not be blank"));
                        request.uri().host("late.test");
                        request.headers().set(HeaderNames.HOST, "late.test");
                        request.headers().remove(ORIGIN_ALIAS);
                        WritableHeaders<?> responseHeaders = WritableHeaders.create();
                        responseHeaders.set(HeaderNames.SET_COOKIE, "recovered=stored; Path=/");
                        return WebClientServiceResponse.builder()
                                .serviceRequest(request)
                                .whenComplete(new CompletableFuture<>())
                                .connection(() -> { })
                                .status(Status.ACCEPTED_202)
                                .headers(ClientResponseHeaders.create(responseHeaders))
                                .build();
                    }
                })
                .buildPrototype();
        TestClient client = new TestClient(config);

        try (HttpClientResponse response = client.method(Method.GET).request()) {
            assertThat(response.status(), is(Status.ACCEPTED_202));
        }

        assertThat(client.cookieManager().getCookieStore().get(URI.create("http://failed.test/"))
                           .stream().map(HttpCookie::getName).toList(),
                   is(List.of("recovered")));
        assertThat(client.cookieManager().getCookieStore().get(URI.create("http://late.test/")), is(List.of()));
        assertThat(client.spi.supportsInvocations, is(0));
        assertThat(client.spi.submittedHeaders.size(), is(0));
    }

    private static final class TestClient implements WebClient {
        private final WebClientConfig config;
        private final WebClientCookieManager cookieManager = WebClientCookieManager.builder()
                .automaticStoreEnabled(true)
                .build();
        private final LruCache<LoomClient.EndpointKey, HttpClientSpi> cache = LruCache.create();
        private final TestClientSpi spi = new TestClientSpi(this);

        private TestClient(WebClientConfig config) {
            this.config = config;
        }

        @Override
        public HttpClientRequest method(Method method) {
            LoomClient.ProtocolSpi protocol = new LoomClient.ProtocolSpi("test", spi);
            return new HttpClientRequest(this,
                                         config,
                                         method,
                                         config.baseUri().map(ClientUri::create).orElseGet(ClientUri::create),
                                         Map.of("test", protocol),
                                         List.of(protocol),
                                         List.of(protocol),
                                         List.of("test"),
                                         cache);
        }

        @Override
        public <T, C extends ProtocolConfig> T client(Protocol<T, C> protocol, C protocolConfig) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T, C extends ProtocolConfig> T client(Protocol<T, C> protocol) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExecutorService executor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WebClientCookieManager cookieManager() {
            return cookieManager;
        }

        @Override
        public WebClientConfig prototype() {
            return config;
        }

        @Override
        public void closeResource() {
        }
    }

    private static final class TestClientSpi implements HttpClientSpi {
        private final TestClient client;
        private final List<ClientRequestHeaders> selectedHeaders = new ArrayList<>();
        private final List<ClientRequestHeaders> submittedHeaders = new ArrayList<>();
        private Consumer<WebClientServiceRequest> requestPrepare = _ -> { };
        private int supportsInvocations;

        private TestClientSpi(TestClient client) {
            this.client = client;
        }

        @Override
        public ClientRequestHeaders normalizedRequestHeaders(ClientRequestHeaders headers) {
            if (!headers.contains(ORIGIN_ALIAS)) {
                return headers;
            }
            if (headers.get(ORIGIN_ALIAS).get().isBlank()) {
                throw new IllegalArgumentException("Test origin must not be blank");
            }
            ClientRequestHeaders normalized = ClientRequestHeaders.create((Headers) headers);
            normalized.set(HeaderNames.HOST, headers.get(ORIGIN_ALIAS).get());
            normalized.remove(ORIGIN_ALIAS);
            return normalized;
        }

        @Override
        public SupportLevel supports(FullClientRequest<?> request, ClientUri uri) {
            supportsInvocations++;
            return SupportLevel.SUPPORTED;
        }

        @Override
        public ClientRequest<?> clientRequest(FullClientRequest<?> request, ClientUri uri) {
            selectedHeaders.add(ClientRequestHeaders.create((Headers) request.headers()));
            return new TestRequest(client, request, uri);
        }

        @Override
        public boolean supportsServiceHandoff() {
            return true;
        }

        @Override
        public void closeResource() {
        }
    }

    private static final class TestRequest extends ClientRequestBase<TestRequest, HttpClientResponse> {
        private final TestClient client;

        private TestRequest(TestClient client, FullClientRequest<?> request, ClientUri uri) {
            super(client.config, client.cookieManager, "test", request.method(), uri, request.properties());
            this.client = client;
            headers().clear();
            headers(request.headers());
            redirectSecurityState(request.redirectSecurityState());
        }

        @Override
        protected ClientRequestHeaders normalizedRequestHeaders(ClientRequestHeaders headers) {
            return client.spi.normalizedRequestHeaders(headers);
        }

        @Override
        protected HttpClientResponse doSubmit(Object entity) {
            CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
            CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
            WebClientService.WireProtocolChain terminal = new WebClientService.WireProtocolChain() {
                @Override
                public WebClientServiceResponse proceed(WebClientServiceRequest request) {
                    client.spi.submittedHeaders.add(ClientRequestHeaders.create((Headers) request.headers()));
                    whenSent.complete(request);
                    return WebClientServiceResponse.builder()
                            .serviceRequest(request)
                            .whenComplete(whenComplete)
                            .connection(() -> { })
                            .status(Status.OK_200)
                            .headers(ClientResponseHeaders.create(WritableHeaders.create()))
                            .build();
                }

                @Override
                public String protocolId() {
                    return "test";
                }
            };
            WebClientServiceResponse response = invokeServices(terminal,
                                                                 whenSent,
                                                                 whenComplete,
                                                                 resolvedUri(),
                                                                 client.spi.requestPrepare);
            return new TestResponse(response, whenComplete);
        }

        @Override
        protected HttpClientResponse doOutputStream(OutputStreamHandler handler) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TestResponse implements HttpClientResponse {
        private final WebClientServiceResponse response;
        private final CompletableFuture<WebClientServiceResponse> whenComplete;

        private TestResponse(WebClientServiceResponse response, CompletableFuture<WebClientServiceResponse> whenComplete) {
            this.response = response;
            this.whenComplete = whenComplete;
        }

        @Override
        public Status status() {
            return response.status();
        }

        @Override
        public ClientResponseHeaders headers() {
            return response.headers();
        }

        @Override
        public ClientResponseTrailers trailers() {
            return ClientResponseTrailers.create();
        }

        @Override
        public ClientUri lastEndpointUri() {
            return response.serviceRequest().uri();
        }

        @Override
        public ReadableEntity entity() {
            return ReadableEntityBase.empty();
        }

        @Override
        public void close() {
            whenComplete.complete(response);
        }
    }
}
