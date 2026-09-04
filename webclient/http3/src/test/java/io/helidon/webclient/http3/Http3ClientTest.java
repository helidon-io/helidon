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

package io.helidon.webclient.http3;

import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientAltSvcConfig;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ClientRequestOrigin;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.ProxyRoute;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientProtocolResponse;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.api.WebClientTransportObserverSupport;
import io.helidon.webclient.spi.ClientProtocolProvider;
import io.helidon.webclient.spi.ClientProtocolProviderCacheLifecycle;
import io.helidon.webclient.spi.HttpClientSpi;
import io.helidon.webclient.spi.Protocol;
import io.helidon.webclient.spi.ProtocolConfig;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webclient.spi.WebClientTransportObserverProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class Http3ClientTest {
    @Test
    void directClientClosesOwnedTransportRegistrationExactlyOnce() {
        ObserverState state = new ObserverState(new Object());
        Http3Client client = Http3Client.builder()
                .servicesDiscoverServices(false)
                .addService(new ObserverService("observer", state))
                .shareConnectionCache(false)
                .build();

        assertThat(state.opened.get(), equalTo(1));

        client.closeResource();
        client.closeResource();

        assertThat(state.closed.get(), equalTo(1));
        assertThat(state.completionRequested.get(), equalTo(1));
    }

    @Test
    void concurrentCloseWaitsForCleanupBeforeCacheReplacement() throws InterruptedException {
        CountDownLatch closeAllowed = new CountDownLatch(1);
        ObserverState state = new ObserverState(new Object(), closeAllowed);
        Http3Client client = Http3Client.builder()
                .servicesDiscoverServices(false)
                .addService(new ObserverService("observer", state))
                .shareConnectionCache(false)
                .build();
        Http3ProtocolProvider provider = new Http3ProtocolProvider();
        AtomicReference<Throwable> ownerFailure = new AtomicReference<>();
        AtomicReference<Throwable> followerFailure = new AtomicReference<>();
        Thread owner = Thread.ofPlatform()
                .unstarted(() -> {
                    try {
                        client.closeResource();
                    } catch (Throwable failure) {
                        ownerFailure.set(failure);
                    }
                });
        Thread follower = Thread.ofPlatform()
                .unstarted(() -> {
                    try {
                        client.closeResource();
                    } catch (Throwable failure) {
                        followerFailure.set(failure);
                    }
                });

        owner.start();
        try {
            assertThat(state.closeStarted.await(5, TimeUnit.SECONDS), equalTo(true));
            assertThat(provider.cacheReplacementReady(client), equalTo(false));

            follower.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (follower.isAlive()
                    && follower.getState() != Thread.State.WAITING
                    && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(follower.getState(), equalTo(Thread.State.WAITING));
        } finally {
            closeAllowed.countDown();
            owner.join();
            follower.join();
        }

        assertThat(ownerFailure.get(), nullValue());
        assertThat(followerFailure.get(), nullValue());
        assertThat(state.closed.get(), equalTo(1));
        assertThat(provider.cacheReplacementReady(client), equalTo(true));
    }

    @Test
    void concurrentCloseCallersObserveSameAggregatedCleanupFailure() throws InterruptedException {
        CountDownLatch closeAllowed = new CountDownLatch(1);
        CompletionException primaryFailure =
                new CompletionException("primary close failure", new IllegalStateException("primary cause"));
        RuntimeException secondaryFailure = new RuntimeException("secondary close failure");
        ObserverState primaryState = new ObserverState(new Object(), closeAllowed, primaryFailure);
        ObserverState secondaryState = new ObserverState(new Object(), new CountDownLatch(0), secondaryFailure);
        Http3Client client = Http3Client.builder()
                .servicesDiscoverServices(false)
                .addService(new ObserverService("primary-observer", primaryState))
                .addService(new ObserverService("secondary-observer", secondaryState))
                .shareConnectionCache(false)
                .build();
        AtomicReference<Throwable> ownerFailure = new AtomicReference<>();
        AtomicReference<Throwable> followerFailure = new AtomicReference<>();
        Thread owner = Thread.ofPlatform()
                .unstarted(() -> {
                    try {
                        client.closeResource();
                    } catch (Throwable failure) {
                        ownerFailure.set(failure);
                    }
                });
        Thread follower = Thread.ofPlatform()
                .unstarted(() -> {
                    try {
                        client.closeResource();
                    } catch (Throwable failure) {
                        followerFailure.set(failure);
                    }
                });

        owner.start();
        try {
            assertThat(primaryState.closeStarted.await(5, TimeUnit.SECONDS), equalTo(true));
            follower.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (follower.isAlive()
                    && follower.getState() != Thread.State.WAITING
                    && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(follower.getState(), equalTo(Thread.State.WAITING));
        } finally {
            closeAllowed.countDown();
            owner.join();
            follower.join();
        }

        assertThat(ownerFailure.get(), sameInstance(primaryFailure));
        assertThat(followerFailure.get(), sameInstance(primaryFailure));
        assertThat(primaryFailure.getSuppressed(), hasItemInArray(sameInstance(secondaryFailure)));
        assertThat(primaryState.closed.get(), equalTo(1));
        assertThat(secondaryState.closed.get(), equalTo(1));
    }

    @Test
    void ordinaryProtocolUsesCachedInstance() {
        AtomicInteger creationCount = new AtomicInteger();
        Protocol<Object, TestProtocolConfig> protocol = () -> new TestProtocolProvider("test-ordinary", creationCount);
        WebClient parent = WebClient.builder()
                .servicesDiscoverServices(false)
                .build();
        try {
            Object first = parent.client(protocol);
            Object second = parent.client(protocol);

            assertThat(second, sameInstance(first));
            assertThat(creationCount.get(), equalTo(1));
        } finally {
            parent.closeResource();
        }
    }

    @Test
    void openLifecycleProtocolCacheHitsDoNotSerialize() throws InterruptedException {
        CountDownLatch callbacksStarted = new CountDownLatch(2);
        CountDownLatch callbacksAllowed = new CountDownLatch(1);
        AtomicInteger creationCount = new AtomicInteger();
        TestLifecycleProtocolProvider provider =
                new TestLifecycleProtocolProvider(callbacksStarted, callbacksAllowed, creationCount);
        Protocol<Object, TestProtocolConfig> protocol = () -> provider;
        WebClient parent = WebClient.builder()
                .servicesDiscoverServices(false)
                .build();
        AtomicReference<Object> firstResult = new AtomicReference<>();
        AtomicReference<Object> secondResult = new AtomicReference<>();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        Thread first = Thread.ofPlatform()
                .unstarted(() -> {
                    try {
                        firstResult.set(parent.client(protocol));
                    } catch (Throwable failure) {
                        firstFailure.set(failure);
                    }
                });
        Thread second = Thread.ofPlatform()
                .unstarted(() -> {
                    try {
                        secondResult.set(parent.client(protocol));
                    } catch (Throwable failure) {
                        secondFailure.set(failure);
                    }
                });
        Object cached = parent.client(protocol);
        try {
            first.start();
            second.start();
            assertThat(callbacksStarted.await(5, TimeUnit.SECONDS), equalTo(true));
            assertThat(creationCount.get(), equalTo(1));
        } finally {
            callbacksAllowed.countDown();
            first.join();
            second.join();
            parent.closeResource();
        }

        assertThat(firstFailure.get(), nullValue());
        assertThat(secondFailure.get(), nullValue());
        assertThat(firstResult.get(), sameInstance(cached));
        assertThat(secondResult.get(), sameInstance(cached));
        assertThat(creationCount.get(), equalTo(1));
    }

    @Test
    void borrowedClientCloseReleasesViewWithoutClosingParent() {
        ObserverState state = new ObserverState(new Object());
        WebClient parent = newWebClient(state);
        try {
            Http3ClientImpl http3Client = (Http3ClientImpl) parent.client(Http3Client.PROTOCOL);

            WebClient fallback = http3Client.fallbackClient();

            assertThat(state.opened.get(), equalTo(1));
            assertThat(WebClientTransportObserverSupport.observerIdentity(fallback), sameInstance(state.identity));
            assertThat(fallback.prototype().services().contains(state.service), equalTo(false));

            http3Client.closeResource();

            assertThat(state.closed.get(), equalTo(0));
            assertThat(state.completionRequested.get(), equalTo(0));
            assertThrows(IllegalStateException.class, http3Client::get);
        } finally {
            parent.closeResource();
        }

        assertThat(state.closed.get(), equalTo(1));
        assertThat(state.completionRequested.get(), equalTo(1));
    }

    @Test
    void parentCloseDoesNotReplaceBorrowedClientClose() {
        ObserverState state = new ObserverState(new Object());
        WebClient parent = newWebClient(state);
        Http3Client http3Client = parent.client(Http3Client.PROTOCOL);

        parent.closeResource();

        assertThat(state.closed.get(), equalTo(1));
        assertThat(state.completionRequested.get(), equalTo(1));
        assertThat(http3Client.get(), notNullValue());

        http3Client.closeResource();
        http3Client.closeResource();

        assertThrows(IllegalStateException.class, http3Client::get);
        assertThat(state.closed.get(), equalTo(1));
        assertThat(state.completionRequested.get(), equalTo(1));
    }

    @Test
    void closedCachedBorrowedClientIsRecreated() {
        WebClient parent = WebClient.builder()
                .baseUri("https://localhost")
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .build();
        try {
            Http3Client first = parent.client(Http3Client.PROTOCOL);
            first.closeResource();

            Http3Client replacement = parent.client(Http3Client.PROTOCOL);
            try {
                assertThat(replacement, not(sameInstance(first)));
                assertThat(replacement.get(), notNullValue());
            } finally {
                replacement.closeResource();
            }
        } finally {
            parent.closeResource();
        }
    }

    @Test
    void sharedCacheHasOneLifecycleOwner() {
        assertThat(Http3ConnectionCache.shared(), sameInstance(Http3ConnectionCache.shared()));
    }

    @Test
    void directClientRejectsRequestsAfterClose() {
        Http3Client client = Http3Client.builder()
                .baseUri("https://localhost")
                .servicesDiscoverServices(false)
                .build();
        Http3ClientRequest pendingRequest = client.get();

        client.closeResource();

        assertThrows(IllegalStateException.class, client::get);
        assertThrows(IllegalStateException.class, pendingRequest::request);
    }

    @Test
    void responseCallbackDoesNothingAfterClose() {
        Http3ClientImpl client = (Http3ClientImpl) Http3Client.builder()
                .baseUri("https://localhost")
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .build();
        WebClientProtocolResponse response = mock(WebClientProtocolResponse.class);

        client.closeResource();
        client.responseReceived(response);

        verifyNoMoreInteractions(response);
    }

    @Test
    void managedNotificationPolicyIsIndependentOfHttp3SelectionPolicy() {
        WebClient webClient = mock(WebClient.class);
        when(webClient.tcpProtocolIds()).thenReturn(List.of("http/1.1"));
        Http3ClientConfig config = Http3Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .altSvc(ClientAltSvcConfig.builder().addProtocol("h2").build())
                .buildPrototype();
        Http3ClientImpl client = new Http3ClientImpl(webClient, config, true, false);
        WebClientProtocolResponse response = mock(WebClientProtocolResponse.class);
        try {
            assertThat(client.altSvcNotificationsEnabled(), equalTo(true));
            assertThat(client.altSvcEnabled(), equalTo(false));
            assertThat(client.responseNotificationsManagedByWebClient(), equalTo(true));

            client.publishResponse(response);

            verify(webClient).responseReceived(response);
        } finally {
            client.closeResource();
        }
    }

    @Test
    void explicitConnectionDisablesHttp3SupportAndTargetSelection() {
        Http3ClientImpl client = (Http3ClientImpl) Http3Client.builder()
                .baseUri("https://localhost")
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .build();
        try {
            Http3ClientRequestImpl request = (Http3ClientRequestImpl) client.get();
            request.connection(mock(ClientConnection.class));

            assertThat(client.supports(request, request.resolvedUri()),
                       sameInstance(HttpClientSpi.SupportLevel.NOT_SUPPORTED));
            assertThat(client.requestTarget(request, request.resolvedUri(), request.headers(), true).isEmpty(),
                       equalTo(true));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void explicitProtocolResponseIsIgnoredBeforeTargetOrHeadersAreConsulted() {
        Http3ClientImpl client = (Http3ClientImpl) Http3Client.builder()
                .baseUri("https://localhost")
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .altSvc(ClientAltSvcConfig.create())
                .build();
        WebClientProtocolResponse response = mock(WebClientProtocolResponse.class);
        when(response.secure()).thenReturn(true);
        when(response.explicitConnection()).thenReturn(true);
        try {
            client.responseReceived(response);

            verify(response).secure();
            verify(response).explicitConnection();
            verifyNoMoreInteractions(response);
        } finally {
            client.closeResource();
        }
    }

    @Test
    void configuredHostnameNoProxyResponseTeachesHttp3Alternative() {
        Proxy configuredProxy = Proxy.builder()
                .host("proxy.invalid")
                .port(8080)
                .addNoProxy("localhost")
                .build();
        Http3ClientImpl client = (Http3ClientImpl) Http3Client.builder()
                .baseUri("https://localhost:8443")
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .proxy(configuredProxy)
                .altSvc(ClientAltSvcConfig.create())
                .build();
        try {
            Http3ClientRequestImpl request = (Http3ClientRequestImpl) client.get("/hello");
            ClientUri uri = request.resolvedUri();
            ConnectionKey connectionKey = client.connectionKey(request, uri, request.headers());
            var responseTarget = ClientConnectionTarget.create(connectionKey, uri, request.headers());
            WritableHeaders<?> responseHeaders = WritableHeaders.create();
            responseHeaders.add(HeaderValues.create(HeaderNames.ALT_SVC, "h3=\":9443\"; ma=3600"));

            assertThat(connectionKey.proxy().type(), equalTo(Proxy.ProxyType.HTTP));
            assertThat(responseTarget.proxyRoute().direct(), equalTo(true));
            assertThat(responseTarget.proxyRoute().addressBound(), equalTo(false));

            client.responseReceived(WebClientProtocolResponse.create(responseTarget.resolve(),
                                                                     false,
                                                                     "http/1.1",
                                                                     Status.OK_200,
                                                                     ClientResponseHeaders.create(responseHeaders),
                                                                     Instant.now()));

            Http3Discovery.Target learned = client.requestTarget(request, uri, request.headers(), true)
                    .orElseThrow()
                    .target();
            assertThat(learned.alternative(), equalTo(true));
            assertThat(learned.peerHost(), equalTo("localhost"));
            assertThat(learned.peerPort(), equalTo(9443));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void absoluteRedirectRetainsExplicitHostAndAddressOnlyForSameUriOrigin(@TempDir Path tempDir) {
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(tempDir.resolve("http3-redirect.sock"));
        Http3ClientImpl client = (Http3ClientImpl) Http3Client.builder()
                .baseUri("http://source.invalid")
                .baseAddress(address)
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .build();
        try {
            Http3ClientRequestImpl request = (Http3ClientRequestImpl) client.get("/source");
            request.headers().set(HeaderNames.HOST, "virtual.invalid");
            ClientUri sourceUri = ClientUri.create(URI.create("http://source.invalid/source"));
            ClientUri sameOriginUri = ClientUri.create(URI.create("http://source.invalid/target"));
            ClientUri crossOriginUri = ClientUri.create(URI.create("http://target.invalid/target"));
            Http3RequestBody emptyBody = Http3RequestBody.create(new byte[0]);
            Http3ClientRequestImpl sameOrigin = new Http3ClientRequestImpl(request,
                                                                           Method.GET,
                                                                           sameOriginUri,
                                                                           Map.of(),
                                                                           sourceUri,
                                                                           false,
                                                                           emptyBody,
                                                                           false);
            Http3ClientRequestImpl crossOrigin = new Http3ClientRequestImpl(request,
                                                                            Method.GET,
                                                                            crossOriginUri,
                                                                            Map.of(),
                                                                            sourceUri,
                                                                            false,
                                                                            emptyBody,
                                                                            false);

            assertThat(request.address().orElseThrow(), equalTo(address));
            assertThat(sameOrigin.address().orElseThrow(), equalTo(address));
            assertThat(sameOrigin.headers().first(HeaderNames.HOST).orElseThrow(), equalTo("virtual.invalid"));
            assertThat(crossOrigin.address().isEmpty(), equalTo(true));
            assertThat(crossOrigin.headers().contains(HeaderNames.HOST), equalTo(false));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void fallbackDropsUriMismatchedInheritedTransportAndKeepsExplicitAddress(@TempDir Path tempDir) {
        Http3ClientImpl client = (Http3ClientImpl) Http3Client.builder()
                .baseUri("https://source.invalid")
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .build();
        try {
            Http3ClientRequestImpl request = (Http3ClientRequestImpl) client.get("/source");
            ClientRequestOrigin sourceOrigin = ClientRequestOrigin.create(request.resolvedUri(), request.headers());
            ClientConnection inheritedConnection = mock(ClientConnection.class);
            UnixDomainSocketAddress explicitAddress = UnixDomainSocketAddress.of(tempDir.resolve("explicit.sock"));
            ProxyRoute inheritedRoute = mock(ProxyRoute.class);
            request.inheritedConnection(inheritedConnection, sourceOrigin);
            request.address(explicitAddress);
            request.inheritedSelectedProxyRoute(inheritedRoute, sourceOrigin);

            FullClientRequest<?> fallback = fallbackRequest(request,
                                                             URI.create("https://other.invalid/target"),
                                                             ClientRequestHeaders.create(WritableHeaders.create()));

            assertThat(fallback.connection().isEmpty(), equalTo(true));
            assertThat(fallback.address().orElseThrow(), sameInstance(explicitAddress));
            assertThat(fallback.selectedProxyRoute().isEmpty(), equalTo(true));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void fallbackDropsHostMismatchedInheritedTransportAndKeepsExplicitConnection(@TempDir Path tempDir) {
        Http3ClientImpl client = (Http3ClientImpl) Http3Client.builder()
                .baseUri("https://source.invalid")
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .build();
        try {
            Http3ClientRequestImpl request = (Http3ClientRequestImpl) client.get("/source");
            ClientRequestOrigin sourceOrigin = ClientRequestOrigin.create(request.resolvedUri(), request.headers());
            ClientConnection explicitConnection = mock(ClientConnection.class);
            UnixDomainSocketAddress inheritedAddress = UnixDomainSocketAddress.of(tempDir.resolve("inherited.sock"));
            ProxyRoute inheritedRoute = mock(ProxyRoute.class);
            request.connection(explicitConnection);
            request.inheritedAddress(inheritedAddress, sourceOrigin);
            request.inheritedSelectedProxyRoute(inheritedRoute, sourceOrigin);
            WritableHeaders<?> headerValues = WritableHeaders.create();
            headerValues.set(HeaderNames.HOST, "other.invalid");

            FullClientRequest<?> fallback = fallbackRequest(request,
                                                             URI.create("https://source.invalid/target"),
                                                             ClientRequestHeaders.create(headerValues));

            assertThat(fallback.connection().orElseThrow(), sameInstance(explicitConnection));
            assertThat(fallback.address().isEmpty(), equalTo(true));
            assertThat(fallback.selectedProxyRoute().isEmpty(), equalTo(true));
        } finally {
            client.closeResource();
        }
    }

    private static FullClientRequest<?> fallbackRequest(Http3ClientRequestImpl request,
                                                        URI uri,
                                                        ClientRequestHeaders headers) {
        Http3RequestBody body = mock(Http3RequestBody.class);
        HttpClientResponse response = mock(HttpClientResponse.class);
        WebClientServiceRequest serviceRequest = mock(WebClientServiceRequest.class);
        when(serviceRequest.method()).thenReturn(Method.GET);
        when(serviceRequest.uri()).thenReturn(ClientUri.create(uri));
        when(serviceRequest.headers()).thenReturn(headers);
        when(body.submit(any())).thenReturn(response);

        request.fallbackResponse(body,
                                 serviceRequest,
                                 false,
                                 new CompletableFuture<>(),
                                 _ -> { });

        ArgumentCaptor<HttpClientRequest> captor = ArgumentCaptor.forClass(HttpClientRequest.class);
        verify(body).submit(captor.capture());
        return (FullClientRequest<?>) captor.getValue();
    }

    private static WebClient newWebClient(ObserverState state) {
        ObserverService service = new ObserverService("observer", state);
        state.service = service;
        return WebClient.builder()
                .servicesDiscoverServices(false)
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference("http/1.1")
                .addService(service)
                .build();
    }

    private static final class ObserverState {
        private final Object identity;
        private final AtomicInteger opened = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();
        private final AtomicInteger completionRequested = new AtomicInteger();
        private final CountDownLatch closeStarted = new CountDownLatch(1);
        private final CountDownLatch closeAllowed;
        private final RuntimeException closeFailure;
        private ObserverService service;

        private ObserverState(Object identity) {
            this(identity, new CountDownLatch(0), null);
        }

        private ObserverState(Object identity, CountDownLatch closeAllowed) {
            this(identity, closeAllowed, null);
        }

        private ObserverState(Object identity, CountDownLatch closeAllowed, RuntimeException closeFailure) {
            this.identity = identity;
            this.closeAllowed = closeAllowed;
            this.closeFailure = closeFailure;
        }
    }

    private record ObserverService(String type, ObserverState state)
            implements WebClientService, WebClientTransportObserverProvider {
        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            return chain.proceed(request);
        }

        @Override
        public Object transportObserverIdentity() {
            return state.identity;
        }

        @Override
        public Registration openTransportObserver() {
            state.opened.incrementAndGet();
            return new Registration() {
                @Override
                public HttpTransportObserver observer() {
                    return (_, _, _) -> ConnectionObservation.noop();
                }

                @Override
                public void close() {
                    state.closeStarted.countDown();
                    try {
                        state.closeAllowed.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while waiting to close transport observer", e);
                    }
                    state.closed.incrementAndGet();
                    if (state.closeFailure != null) {
                        throw state.closeFailure;
                    }
                }

                @Override
                public CompletionStage<Void> completion() {
                    state.completionRequested.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                }
            };
        }
    }

    private static final class TestProtocolConfig implements ProtocolConfig {
        @Override
        public String name() {
            return "test";
        }

        @Override
        public String type() {
            return "test";
        }
    }

    private static class TestProtocolProvider implements ClientProtocolProvider<Object, TestProtocolConfig> {
        private final String protocolId;
        private final AtomicInteger creationCount;

        private TestProtocolProvider(String protocolId, AtomicInteger creationCount) {
            this.protocolId = protocolId;
            this.creationCount = creationCount;
        }

        @Override
        public String protocolId() {
            return protocolId;
        }

        @Override
        public Class<TestProtocolConfig> configType() {
            return TestProtocolConfig.class;
        }

        @Override
        public TestProtocolConfig defaultConfig() {
            return new TestProtocolConfig();
        }

        @Override
        public Object protocol(WebClient client, TestProtocolConfig config) {
            creationCount.incrementAndGet();
            return new Object();
        }
    }

    private static final class TestLifecycleProtocolProvider extends TestProtocolProvider
            implements ClientProtocolProviderCacheLifecycle<Object, TestProtocolConfig> {
        private final CountDownLatch callbacksStarted;
        private final CountDownLatch callbacksAllowed;

        private TestLifecycleProtocolProvider(CountDownLatch callbacksStarted,
                                              CountDownLatch callbacksAllowed,
                                              AtomicInteger creationCount) {
            super("test-lifecycle", creationCount);
            this.callbacksStarted = callbacksStarted;
            this.callbacksAllowed = callbacksAllowed;
        }

        @Override
        public boolean cacheReplacementReady(Object client) {
            callbacksStarted.countDown();
            try {
                callbacksAllowed.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while checking protocol cache lifecycle", e);
            }
            return false;
        }
    }

}
