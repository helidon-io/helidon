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

package io.helidon.webclient.tests.http3;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientAltSvcConfig;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webclient.http3.Http3ClientRequest;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.http.AltSvc;
import io.helidon.webserver.http1.Http1Config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Isolated
class Http3ProxyFallbackTest {
    private static final String PROXY_HOST = "localhost";
    private static final String HELLO = "Hello";
    private static final HeaderName PROXY_CONNECTION = HeaderNames.create("Proxy-Connection");

    private HttpProxy httpProxy;
    private int proxyPort;
    private TestEnvironment environment;

    @BeforeEach
    void beforeEach() throws Exception {
        environment = newSharedListener(Http1Config.create());
        httpProxy = new HttpProxy(0);
        httpProxy.start();
        proxyPort = httpProxy.connectedPort();
        assertThat(httpProxy.counter(), is(0));
    }

    @AfterEach
    void afterEach() {
        if (httpProxy != null) {
            httpProxy.stop();
        }
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void shouldFallbackTypedHttp3ClientToHttp1ThroughProxy() {
        Http3Client client = newHttp3Client(Http3ClientProtocolConfig.create());
        try {
            try (Http3ClientResponse response = client.get("/hello").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(HELLO));
            }
        } finally {
            client.closeResource();
        }

        assertThat(httpProxy.counter(), is(1));
    }

    @Test
    void shouldNotExposeTransportProxyHeaderThroughWhenSent() throws Exception {
        CompletableFuture<Boolean> whenSentProxyConnection = new CompletableFuture<>();
        Http3Client client = Http3Client.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .proxy(proxy())
                .tls(environment.clientTls())
                .addService((chain, request) -> {
                    request.whenSent().whenComplete((sentRequest, failure) -> {
                        if (failure == null) {
                            whenSentProxyConnection.complete(sentRequest.headers().contains(PROXY_CONNECTION));
                        } else {
                            whenSentProxyConnection.completeExceptionally(failure);
                        }
                    });
                    return chain.proceed(request);
                })
                .build();
        try {
            try (Http3ClientResponse response = client.get("/hello").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(HELLO));
            }
            assertThat(whenSentProxyConnection.get(5, TimeUnit.SECONDS), is(false));
        } finally {
            client.closeResource();
        }

        assertThat(httpProxy.counter(), is(1));
    }

    @Test
    void shouldFailTypedHttp3ClientWithProxyWhenPriorKnowledgeIsEnabled() {
        Http3Client client = newHttp3Client(Http3ClientProtocolConfig.builder()
                                                     .priorKnowledge(true)
                                                     .build());
        try {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                          () -> client.get("/hello").request());
            assertThat(exception.getMessage(),
                       is("HTTP/3 priorKnowledge is enabled, but this request cannot use HTTP/3."));
        } finally {
            client.closeResource();
        }

        assertThat(httpProxy.counter(), is(0));
    }

    @Test
    void shouldUseHttp3WhenConfiguredProxyBypassesOrigin() {
        Proxy bypassingProxy = Proxy.builder()
                .type(Proxy.ProxyType.HTTP)
                .host(PROXY_HOST)
                .port(proxyPort)
                .addNoProxy("localhost")
                .build();
        Http3Client client = Http3Client.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .proxy(bypassingProxy)
                .tls(environment.clientTls())
                .build();
        try {
            try (Http3ClientResponse response = client.get("/hello").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(HELLO));
            }
        } finally {
            client.closeResource();
        }

        assertThat(httpProxy.counter(), is(0));
    }

    @Test
    void shouldSelectSystemProxyRouteOnceAcrossHttp3Fallback() {
        ProxySelector previous = ProxySelector.getDefault();
        AtomicInteger selections = new AtomicInteger();
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<java.net.Proxy> select(URI uri) {
                if (selections.incrementAndGet() > 1) {
                    throw new IllegalStateException("System proxy route selected more than once");
                }
                return List.of(new java.net.Proxy(java.net.Proxy.Type.HTTP,
                                                  InetSocketAddress.createUnresolved(PROXY_HOST, proxyPort)));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException exception) {
            }
        });
        Proxy systemProxy;
        try {
            systemProxy = Proxy.create();
        } finally {
            ProxySelector.setDefault(previous);
        }

        Http3Client client = Http3Client.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .proxy(systemProxy)
                .tls(environment.clientTls())
                .build();
        try {
            try (Http3ClientResponse response = client.get("/hello").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(HELLO));
            }
        } finally {
            client.closeResource();
        }

        assertThat(selections.get(), is(1));
        assertThat(httpProxy.counter(), is(1));
    }

    @Test
    void shouldReselectSystemProxyRouteWhenRequestIsReused() {
        ProxySelector previous = ProxySelector.getDefault();
        AtomicInteger selections = new AtomicInteger();
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<java.net.Proxy> select(URI uri) {
                if (selections.incrementAndGet() == 1) {
                    return List.of(new java.net.Proxy(java.net.Proxy.Type.HTTP,
                                                      InetSocketAddress.createUnresolved(PROXY_HOST, proxyPort)));
                }
                return List.of(java.net.Proxy.NO_PROXY);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException exception) {
            }
        });
        Proxy systemProxy;
        try {
            systemProxy = Proxy.create();
        } finally {
            ProxySelector.setDefault(previous);
        }

        Http3Client client = Http3Client.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .proxy(systemProxy)
                .tls(environment.clientTls())
                .build();
        try {
            Http3ClientRequest request = client.get("/hello");
            try (Http3ClientResponse response = request.request()) {
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(HELLO));
            }
            try (Http3ClientResponse response = request.request()) {
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(HELLO));
            }
        } finally {
            client.closeResource();
        }

        assertThat(selections.get(), is(2));
        assertThat(httpProxy.counter(), is(1));
    }

    @Test
    void shouldPartitionGenericProtocolCacheAcrossAlternatingSystemRoutes() {
        ProxySelector previous = ProxySelector.getDefault();
        AtomicInteger selections = new AtomicInteger();
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<java.net.Proxy> select(URI uri) {
                if (selections.incrementAndGet() == 2) {
                    return List.of(java.net.Proxy.NO_PROXY);
                }
                return List.of(new java.net.Proxy(java.net.Proxy.Type.HTTP,
                                                  InetSocketAddress.createUnresolved(PROXY_HOST, proxyPort)));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException exception) {
            }
        });
        Proxy systemProxy;
        try {
            systemProxy = Proxy.create();
        } finally {
            ProxySelector.setDefault(previous);
        }

        WebClient client = WebClient.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .proxy(systemProxy)
                .tls(environment.clientTls())
                .addService((chain, request) -> chain.proceed(request))
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .addProtocolConfig(Http3ClientProtocolConfig.builder().priorKnowledge(true).build())
                .build();
        try {
            var request = client.get("/hello");
            try (HttpClientResponse response = request.request()) {
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(HELLO));
            }
            try (HttpClientResponse response = request.request()) {
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(HELLO));
            }
            try (HttpClientResponse response = request.request()) {
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(HELLO));
            }
        } finally {
            client.closeResource();
        }

        assertThat(selections.get(), is(3));
        assertThat(httpProxy.counter(), is(2));
    }

    @Test
    void shouldFallbackExplicitHttp3RequestToHttp1ThroughProxy() {
        WebClient client = newGenericClient(Http3ClientProtocolConfig.create());
        try {
            try (HttpClientResponse response = client.get("/hello")
                    .protocolId(Http3Client.PROTOCOL_ID)
                    .request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(HELLO));
            }
        } finally {
            client.closeResource();
        }

        assertThat(httpProxy.counter(), is(1));
    }

    @Test
    void shouldFailExplicitHttp3RequestWithProxyWhenPriorKnowledgeIsEnabled() {
        WebClient client = newGenericClient(Http3ClientProtocolConfig.builder()
                                                     .priorKnowledge(true)
                                                     .build());
        try {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                          () -> client.get("/hello")
                                                                  .protocolId(Http3Client.PROTOCOL_ID)
                                                                  .request());
            assertThat(exception.getMessage(),
                       is("HTTP/3 priorKnowledge is enabled, but this request cannot use HTTP/3."));
        } finally {
            client.closeResource();
        }

        assertThat(httpProxy.counter(), is(0));
    }

    @Test
    void shouldIgnoreAltSvcAdvertisementForProxyConfiguredGenericClient() throws Exception {
        environment.close();
        environment = newSharedListener(Http1Config.builder()
                                               .altSvc(AltSvc.builder().build())
                                               .build());

        WebClient client = newGenericClient(Http3ClientProtocolConfig.create(), false);
        try {
            try (HttpClientResponse firstResponse = client.get("/hello").request()) {
                assertThat(firstResponse.status(), is(Status.OK_200));
                assertThat(firstResponse.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(firstResponse.headers().contains(HeaderNames.ALT_SVC), is(true));
                assertThat(firstResponse.entity().as(String.class), is(HELLO));
            }

            try (HttpClientResponse secondResponse = client.get("/hello").request()) {
                assertThat(secondResponse.status(), is(Status.OK_200));
                assertThat(secondResponse.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(secondResponse.entity().as(String.class), is(HELLO));
            }
        } finally {
            client.closeResource();
        }

        assertThat(httpProxy.counter(), is(2));
    }

    private Http3Client newHttp3Client(Http3ClientProtocolConfig protocolConfig) {
        return Http3Client.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .proxy(proxy())
                .tls(environment.clientTls())
                .protocolConfig(protocolConfig)
                .build();
    }

    private WebClient newGenericClient(Http3ClientProtocolConfig protocolConfig) {
        return newGenericClient(protocolConfig, true);
    }

    private WebClient newGenericClient(Http3ClientProtocolConfig protocolConfig, boolean keepAlive) {
        return WebClient.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .keepAlive(keepAlive)
                .proxy(proxy())
                .tls(environment.clientTls())
                .altSvc(ClientAltSvcConfig.create())
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .addProtocolConfig(protocolConfig)
                .build();
    }

    private Proxy proxy() {
        return Proxy.builder()
                .type(Proxy.ProxyType.HTTP)
                .host(PROXY_HOST)
                .port(proxyPort)
                .build();
    }

    private static TestEnvironment newSharedListener(Http1Config http1Config) throws Exception {
        return TestEnvironment.createSharedListener(http1Config,
                                                    routing -> routing.get("/hello", (_, res) -> res.send(HELLO)));
    }
}
