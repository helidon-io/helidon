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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.LruCache;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.ReadableEntity;
import io.helidon.http.media.ReadableEntityBase;
import io.helidon.webclient.spi.HttpClientSpi;
import io.helidon.webclient.spi.Protocol;
import io.helidon.webclient.spi.ProtocolConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class HttpClientRequestProtocolCacheTest {
    @Test
    void invalidatesCachedProtocolWhenSupportIsLost() {
        TestContext context = TestContext.create();
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);

        context.request().request();
        assertThat("initial protocol", context.selected().get(), is("dynamic"));

        context.dynamic().support(HttpClientSpi.SupportLevel.NOT_SUPPORTED);
        context.request().request();

        assertThat("protocol after cached support was lost", context.selected().get(), is("fallback"));
        assertThat("dynamic protocol support checks", context.dynamic().supportsInvocations(), is(3));
        assertThat("fallback protocol support checks", context.fallback().supportsInvocations(), is(1));
    }

    @Test
    void promotesHigherPriorityProtocolFromCachedTcpFallback() {
        TestContext context = TestContext.create();
        context.dynamic().support(HttpClientSpi.SupportLevel.NOT_SUPPORTED);

        context.request().request();
        assertThat("initial protocol", context.selected().get(), is("fallback"));

        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.request().request();

        assertThat("protocol after higher-priority support appeared", context.selected().get(), is("dynamic"));
        assertThat("higher-priority protocol support checks", context.dynamic().supportsInvocations(), is(2));
        assertThat("fallback protocol support checks", context.fallback().supportsInvocations(), is(1));
    }

    @Test
    void reusesRevalidatedCompatibleProtocol() {
        TestContext context = TestContext.create();
        context.dynamic().support(HttpClientSpi.SupportLevel.NOT_SUPPORTED);

        context.request().request();
        context.selected().set(null);
        context.dynamic().support(HttpClientSpi.SupportLevel.COMPATIBLE);
        context.request().request();

        assertThat("revalidated cached protocol", context.selected().get(), is("fallback"));
        assertThat("higher-priority compatible protocol support checks",
                   context.dynamic().supportsInvocations(),
                   is(2));
        assertThat("cached compatible protocol support checks", context.fallback().supportsInvocations(), is(2));
    }

    @Test
    void explicitProtocolSelectionBypassesAutomaticRevalidation() {
        TestContext context = TestContext.create();

        context.request().protocolId("dynamic").request();

        assertThat("explicitly selected protocol", context.selected().get(), is("dynamic"));
        assertThat("explicit protocol support checks", context.dynamic().supportsInvocations(), is(0));
        assertThat("fallback protocol support checks", context.fallback().supportsInvocations(), is(0));
    }

    private record TestContext(WebClient webClient,
                               WebClientConfig config,
                               List<LoomClient.ProtocolSpi> protocols,
                               Map<String, LoomClient.ProtocolSpi> clients,
                               LruCache<LoomClient.EndpointKey, HttpClientSpi> cache,
                               TestClientSpi dynamic,
                               TestClientSpi fallback,
                               AtomicReference<String> selected) {
        private static TestContext create() {
            WebClientConfig config = WebClientConfig.builder()
                    .baseUri("http://example.test")
                    .buildPrototype();
            TestWebClient webClient = new TestWebClient(config);
            AtomicReference<String> selected = new AtomicReference<>();
            TestClientSpi dynamic = new TestClientSpi("dynamic", false, selected, config);
            TestClientSpi fallback = new TestClientSpi("fallback", true, selected, config);
            fallback.support(HttpClientSpi.SupportLevel.COMPATIBLE);
            LoomClient.ProtocolSpi dynamicProtocol = new LoomClient.ProtocolSpi("dynamic", dynamic);
            LoomClient.ProtocolSpi fallbackProtocol = new LoomClient.ProtocolSpi("fallback", fallback);
            return new TestContext(webClient,
                                   config,
                                   List.of(dynamicProtocol, fallbackProtocol),
                                   Map.of("dynamic", dynamicProtocol, "fallback", fallbackProtocol),
                                   LruCache.create(),
                                   dynamic,
                                   fallback,
                                   selected);
        }

        private HttpClientRequest request() {
            return new HttpClientRequest(webClient,
                                         config,
                                         Method.GET,
                                         ClientUri.create(),
                                         clients,
                                         protocols,
                                         List.of(protocols.get(1)),
                                         List.of("fallback"),
                                         cache);
        }
    }

    private static final class TestClientSpi implements HttpClientSpi {
        private final String id;
        private final boolean tcp;
        private final AtomicReference<String> selected;
        private final HttpClientConfig clientConfig;
        private final AtomicReference<SupportLevel> support = new AtomicReference<>(SupportLevel.NOT_SUPPORTED);
        private final AtomicInteger supportsInvocations = new AtomicInteger();

        private TestClientSpi(String id,
                              boolean tcp,
                              AtomicReference<String> selected,
                              HttpClientConfig clientConfig) {
            this.id = id;
            this.tcp = tcp;
            this.selected = selected;
            this.clientConfig = clientConfig;
        }

        @Override
        public SupportLevel supports(FullClientRequest<?> clientRequest, ClientUri clientUri) {
            supportsInvocations.incrementAndGet();
            return support.get();
        }

        @Override
        public ClientRequest<?> clientRequest(FullClientRequest<?> clientRequest, ClientUri clientUri) {
            selected.set(id);
            return new TestClientRequest(clientConfig, clientRequest, clientUri);
        }

        @Override
        public boolean isTcp() {
            return tcp;
        }

        @Override
        public void closeResource() {
        }

        private void support(SupportLevel support) {
            this.support.set(support);
        }

        private int supportsInvocations() {
            return supportsInvocations.get();
        }
    }

    private static final class TestClientRequest extends ClientRequestBase<TestClientRequest, HttpClientResponse> {
        private TestClientRequest(HttpClientConfig clientConfig,
                                  FullClientRequest<?> clientRequest,
                                  ClientUri clientUri) {
            super(clientConfig,
                  WebClientCookieManager.builder().build(),
                  "test",
                  clientRequest.method(),
                  clientUri,
                  clientRequest.properties());
        }

        @Override
        protected HttpClientResponse doSubmit(Object entity) {
            return new TestClientResponse(resolvedUri());
        }

        @Override
        protected HttpClientResponse doOutputStream(OutputStreamHandler outputStreamHandler) {
            return new TestClientResponse(resolvedUri());
        }
    }

    private static final class TestClientResponse implements HttpClientResponse {
        private final ClientUri endpointUri;

        private TestClientResponse(ClientUri endpointUri) {
            this.endpointUri = endpointUri;
        }

        @Override
        public Status status() {
            return Status.OK_200;
        }

        @Override
        public ClientResponseHeaders headers() {
            return ClientResponseHeaders.create(WritableHeaders.create());
        }

        @Override
        public ClientResponseTrailers trailers() {
            return ClientResponseTrailers.create();
        }

        @Override
        public ClientUri lastEndpointUri() {
            return endpointUri;
        }

        @Override
        public ReadableEntity entity() {
            return ReadableEntityBase.empty();
        }

        @Override
        public void close() {
        }
    }

    private static final class TestWebClient implements WebClient {
        private final WebClientConfig config;
        private final WebClientCookieManager cookieManager = WebClientCookieManager.builder().build();

        private TestWebClient(WebClientConfig config) {
            this.config = config;
        }

        @Override
        public HttpClientRequest method(Method method) {
            throw new UnsupportedOperationException();
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
}
