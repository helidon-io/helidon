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

package io.helidon.webclient.http2;

import java.net.InetAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.IntFunction;

import io.helidon.common.context.Context;
import io.helidon.common.tls.Tls;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.AltSvcHeader;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.http1.UpgradeResponse;
import io.helidon.webclient.spi.DnsResolver;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http2ConnectionCacheTest {
    @Test
    void forwardProxyFallbackDoesNotCreateHttp2Handler() {
        Tls tls = Tls.builder().enabled(false).build();
        Proxy proxy = Proxy.builder()
                .host("proxy.example")
                .port(8181)
                .build();
        ClientConnectionTarget connectionTarget = ClientConnectionTarget.create(
                ConnectionKey.create("http",
                                     "target.example",
                                     80,
                                     tls,
                                     (_, _) -> InetAddress.getLoopbackAddress(),
                                     DnsAddressLookup.IPV4,
                                     proxy),
                "http");
        ClientUri initialUri = ClientUri.create(URI.create("http://target.example"));
        ClientRequestHeaders requestHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        ClientRequestHeaders fallbackHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        ClientRequestHeaders serviceHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        Http2ClientImpl http2Client = mock(Http2ClientImpl.class);
        Http1Client http1Client = mock(Http1Client.class);
        Http1ClientRequest fallbackRequest = mock(Http1ClientRequest.class, Answers.RETURNS_SELF);
        Http1ClientResponse fallbackResponse = mock(Http1ClientResponse.class);
        Http2ClientRequestImpl request = mock(Http2ClientRequestImpl.class);
        WebClientServiceRequest serviceRequest = mock(WebClientServiceRequest.class);

        when(http2Client.clientConfig()).thenReturn(Http2ClientConfig.create());
        when(http2Client.http1FallbackClient()).thenReturn(http1Client);
        when(http1Client.method(Method.GET)).thenReturn(fallbackRequest);
        when(fallbackRequest.headers()).thenReturn(fallbackHeaders);
        when(request.connection()).thenReturn(Optional.empty());
        when(request.tcpProtocolIds()).thenReturn(List.of(Http1Client.PROTOCOL_ID));
        when(request.tls()).thenReturn(tls);
        when(request.proxy()).thenReturn(proxy);
        when(request.method()).thenReturn(Method.GET);
        when(request.headers()).thenReturn(requestHeaders);
        when(request.properties()).thenReturn(Map.of());
        when(request.address()).thenReturn(Optional.empty());
        when(request.sni()).thenReturn(Optional.empty());
        when(request.sendExpectContinue()).thenReturn(Optional.empty());
        when(serviceRequest.context()).thenReturn(Context.create());
        when(serviceRequest.headers()).thenReturn(serviceHeaders);

        Http1FallbackHandler fallbackHandler = new Http1FallbackHandler(new CompletableFuture<>(),
                                                                        _ -> fallbackResponse,
                                                                        true);
        Http2ConnectionCache cache = Http2ConnectionCache.create();
        try {
            Http2ConnectionAttemptResult result = cache.newStream(http2Client,
                                                                  connectionTarget,
                                                                  request,
                                                                  initialUri,
                                                                  serviceRequest,
                                                                  fallbackHandler);

            assertThat(result.result(), is(Http2ConnectionAttemptResult.Result.HTTP_1));
            assertThat(result.response(), sameInstance(fallbackResponse));
            assertThat(result.connectionTarget(), sameInstance(connectionTarget));
            assertThat(result.handler(), is(nullValue()));
        } finally {
            cache.closeResource();
        }
    }

    @Test
    void rejectsNonPositiveConnectionCacheSize() {
        assertThrows(IllegalArgumentException.class, () -> new Http2ClientConnectionHandler(0));
        assertThrows(IllegalArgumentException.class, () -> new Http2ClientConnectionHandler(-1));
    }

    @Test
    void expiredAlternativeRaceDoesNotCompleteRequestAsFailed() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC);
        Http2AltSvcCache alternatives = Http2AltSvcCache.create(clock, _ -> { });
        Tls tls = Tls.builder().trustAll(true).build();
        ClientConnectionTarget originTarget = ClientConnectionTarget.create(
                ConnectionKey.create("https",
                                     "origin.example",
                                     443,
                                     tls,
                                     (_, _) -> InetAddress.getLoopbackAddress(),
                                     DnsAddressLookup.IPV4,
                                     Proxy.noProxy()),
                "https");
        WritableHeaders<?> responseHeaders = WritableHeaders.create();
        responseHeaders.add(HeaderValues.create(HeaderNames.ALT_SVC, "h2=\":8443\"; ma=0"));
        AltSvcHeader advertisement = AltSvcHeader.create(ClientResponseHeaders.create(responseHeaders), clock.instant())
                .orElseThrow();
        alternatives.record(originTarget, advertisement, true, false, clock.instant());
        Http2AltSvcCache.Selection selection = alternatives.select(originTarget, false, _ -> true);
        Http2ClientConnectionHandler handler = new Http2ClientConnectionHandler(1, alternatives::current);
        Http2ClientImpl http2Client = mock(Http2ClientImpl.class);
        Http2ClientRequestImpl request = mock(Http2ClientRequestImpl.class);
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        Http1FallbackHandler fallbackHandler = new Http1FallbackHandler(whenSent, _ -> null, true);
        when(http2Client.protocolConfig()).thenReturn(Http2ClientProtocolConfig.create());

        AlternativeConnectionException failure = assertThrows(
                AlternativeConnectionException.class,
                () -> handler.newAlternativeStream(http2Client, selection, request, fallbackHandler));

        assertThat(failure.selection(), sameInstance(selection));
        assertThat(failure.reason(), is(AlternativeConnectionException.Reason.STALE));
        assertThat(whenSent.isDone(), is(false));
        alternatives.close();
    }

    @Test
    void removingOneTargetRetainsSharedHttp2Support() {
        Tls tls = Tls.builder().enabled(false).build();
        Proxy proxy = Proxy.noProxy();
        DnsResolver dnsResolver = (_, _) -> InetAddress.getLoopbackAddress();
        ConnectionKey connectionKey = ConnectionKey.create("http",
                                                           "target.example",
                                                           80,
                                                           tls,
                                                           dnsResolver,
                                                           DnsAddressLookup.IPV4,
                                                           proxy);
        ClientUri initialUri = ClientUri.create(URI.create("http://target.example"));
        ClientRequestHeaders firstTargetHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        firstTargetHeaders.set(HeaderNames.HOST, "first.example");
        ClientRequestHeaders secondTargetHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        secondTargetHeaders.set(HeaderNames.HOST, "second.example");
        ClientConnectionTarget firstTarget = ClientConnectionTarget.create(connectionKey,
                                                                            initialUri,
                                                                            firstTargetHeaders);
        ClientConnectionTarget secondTarget = ClientConnectionTarget.create(connectionKey,
                                                                             initialUri,
                                                                             secondTargetHeaders);
        ClientRequestHeaders requestHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        ClientRequestHeaders fallbackHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        ClientRequestHeaders serviceHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        Http2ClientImpl http2Client = mock(Http2ClientImpl.class);
        Http1Client http1Client = mock(Http1Client.class);
        Http1ClientRequest fallbackRequest = mock(Http1ClientRequest.class, Answers.RETURNS_SELF);
        Http1ClientResponse fallbackResponse = mock(Http1ClientResponse.class);
        Http2ClientRequestImpl request = mock(Http2ClientRequestImpl.class);
        WebClientServiceRequest serviceRequest = mock(WebClientServiceRequest.class);

        when(http2Client.clientConfig()).thenReturn(Http2ClientConfig.create());
        when(http2Client.protocolConfig()).thenReturn(Http2ClientProtocolConfig.create());
        when(http2Client.http1FallbackClient()).thenReturn(http1Client);
        when(http1Client.method(Method.GET)).thenReturn(fallbackRequest);
        when(fallbackRequest.headers()).thenReturn(fallbackHeaders);
        when(fallbackRequest.upgrade("h2c")).thenReturn(UpgradeResponse.failure(fallbackResponse));
        when(request.connection()).thenReturn(Optional.empty());
        when(request.tcpProtocolIds()).thenReturn(List.of(Http1Client.PROTOCOL_ID));
        when(request.tls()).thenReturn(tls);
        when(request.proxy()).thenReturn(proxy);
        when(request.method()).thenReturn(Method.GET);
        when(request.headers()).thenReturn(requestHeaders);
        when(request.properties()).thenReturn(Map.of());
        when(request.address()).thenReturn(Optional.empty());
        when(request.sni()).thenReturn(Optional.empty());
        when(request.sendExpectContinue()).thenReturn(Optional.empty());
        when(serviceRequest.context()).thenReturn(Context.create());
        when(serviceRequest.headers()).thenReturn(serviceHeaders);

        Http1FallbackHandler fallbackHandler = new Http1FallbackHandler(new CompletableFuture<>(),
                                                                        _ -> fallbackResponse,
                                                                        true);
        Http2ConnectionCache cache = Http2ConnectionCache.create();
        try {
            Http2ClientConnectionHandler firstHandler = cache.newStream(http2Client,
                                                                         firstTarget,
                                                                         request,
                                                                         initialUri,
                                                                         serviceRequest,
                                                                         fallbackHandler)
                    .handler();
            Http2ClientConnectionHandler secondHandler = cache.newStream(http2Client,
                                                                          secondTarget,
                                                                          request,
                                                                          initialUri,
                                                                          serviceRequest,
                                                                          fallbackHandler)
                    .handler();
            cache.markSupported(firstTarget);

            assertThat(firstTarget, not(secondTarget));
            assertThat(firstHandler, not(sameInstance(secondHandler)));
            assertThat(cache.supports(connectionKey), is(true));

            cache.remove(firstTarget, firstHandler);

            assertThat(cache.supports(connectionKey), is(false));

            firstHandler = cache.newStream(http2Client,
                                           firstTarget,
                                           request,
                                           initialUri,
                                           serviceRequest,
                                           fallbackHandler)
                    .handler();
            cache.markSupported(secondTarget);

            assertThat(firstHandler, not(sameInstance(secondHandler)));
            assertThat(cache.supports(connectionKey), is(true));

            cache.remove(firstTarget, firstHandler);

            assertThat(cache.supports(connectionKey), is(true));
            assertThat(cache.newStream(http2Client,
                                       secondTarget,
                                       request,
                                       initialUri,
                                       serviceRequest,
                                       fallbackHandler)
                               .handler(),
                       sameInstance(secondHandler));

            cache.remove(secondTarget, secondHandler);

            assertThat(cache.supports(connectionKey), is(false));
        } finally {
            cache.closeResource();
        }
    }

    @Test
    void oldestHandlerEvictionDoesNotAllowRetiredHandlerToRemoveSuccessor() {
        Tls tls = Tls.builder().enabled(false).build();
        Proxy proxy = Proxy.noProxy();
        DnsResolver dnsResolver = (_, _) -> InetAddress.getLoopbackAddress();
        IntFunction<ClientConnectionTarget> target = id -> ClientConnectionTarget.create(
                ConnectionKey.create("http",
                                     "target-" + id + ".example",
                                     80,
                                     tls,
                                     dnsResolver,
                                     DnsAddressLookup.IPV4,
                                     proxy),
                "http");

        ClientRequestHeaders requestHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        ClientRequestHeaders fallbackHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        ClientRequestHeaders serviceHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        Http2ClientImpl http2Client = mock(Http2ClientImpl.class);
        Http1Client http1Client = mock(Http1Client.class);
        Http1ClientRequest fallbackRequest = mock(Http1ClientRequest.class, Answers.RETURNS_SELF);
        Http1ClientResponse fallbackResponse = mock(Http1ClientResponse.class);
        Http2ClientRequestImpl request = mock(Http2ClientRequestImpl.class);
        WebClientServiceRequest serviceRequest = mock(WebClientServiceRequest.class);

        when(http2Client.clientConfig()).thenReturn(Http2ClientConfig.create());
        when(http2Client.protocolConfig()).thenReturn(Http2ClientProtocolConfig.create());
        when(http2Client.http1FallbackClient()).thenReturn(http1Client);
        when(http1Client.method(Method.GET)).thenReturn(fallbackRequest);
        when(fallbackRequest.headers()).thenReturn(fallbackHeaders);
        when(fallbackRequest.upgrade("h2c")).thenReturn(UpgradeResponse.failure(fallbackResponse));
        when(request.connection()).thenReturn(Optional.empty());
        when(request.tcpProtocolIds()).thenReturn(List.of(Http1Client.PROTOCOL_ID));
        when(request.tls()).thenReturn(tls);
        when(request.method()).thenReturn(Method.GET);
        when(request.headers()).thenReturn(requestHeaders);
        when(request.properties()).thenReturn(Map.of());
        when(request.address()).thenReturn(Optional.empty());
        when(request.sni()).thenReturn(Optional.empty());
        when(request.sendExpectContinue()).thenReturn(Optional.empty());
        when(serviceRequest.context()).thenReturn(Context.create());
        when(serviceRequest.headers()).thenReturn(serviceHeaders);

        ClientUri initialUri = ClientUri.create(URI.create("http://target.example"));
        Http1FallbackHandler fallbackHandler = new Http1FallbackHandler(new CompletableFuture<>(),
                                                                        _ -> fallbackResponse,
                                                                        true);
        Http2ConnectionCache cache = Http2ConnectionCache.create();
        Function<ClientConnectionTarget, Http2ConnectionAttemptResult> newStream = connectionTarget -> cache.newStream(
                http2Client,
                connectionTarget,
                request,
                initialUri,
                serviceRequest,
                fallbackHandler);

        try {
            ClientConnectionTarget firstTarget = target.apply(0);
            Http2ConnectionAttemptResult predecessor = newStream.apply(firstTarget);
            Http2ClientConnectionHandler predecessorHandler = predecessor.handler();
            boolean predecessorLease = predecessorHandler.acquire();
            ClientConnectionTarget equalTarget = target.apply(0);
            try {
                assertThat(predecessorLease, is(true));
                for (int i = 1; i < 1_000; i++) {
                    newStream.apply(target.apply(i));
                }
                Http2ConnectionAttemptResult cached = newStream.apply(equalTarget);

                assertThat(cached.handler(), sameInstance(predecessorHandler));

                newStream.apply(target.apply(1_000));
                boolean acquiredAfterRetirement = predecessorHandler.acquire();
                if (acquiredAfterRetirement) {
                    predecessorHandler.release();
                }
                assertThat(acquiredAfterRetirement, is(false));
            } finally {
                if (predecessorLease) {
                    predecessorHandler.release();
                }
            }
            Http2ConnectionAttemptResult successor = newStream.apply(equalTarget);

            assertThat(equalTarget, is(firstTarget));
            assertThat(successor.handler(), not(sameInstance(predecessorHandler)));

            cache.remove(firstTarget, predecessorHandler);
            Http2ConnectionAttemptResult retained = newStream.apply(equalTarget);

            assertThat(retained.handler(), sameInstance(successor.handler()));

            Http2ClientConnectionHandler retainedHandler = retained.handler();
            boolean retainedLease = retainedHandler.acquire();
            try {
                assertThat(retainedLease, is(true));
                cache.closeResource();
                boolean acquiredAfterClose = retainedHandler.acquire();
                if (acquiredAfterClose) {
                    retainedHandler.release();
                }
                assertThat(acquiredAfterClose, is(false));
            } finally {
                if (retainedLease) {
                    retainedHandler.release();
                }
            }
        } finally {
            cache.closeResource();
        }
    }
}
