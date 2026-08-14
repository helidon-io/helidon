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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.IntFunction;

import io.helidon.common.context.Context;
import io.helidon.common.tls.Tls;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.spi.DnsResolver;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http2ConnectionCacheTest {
    @Test
    void retiredHandlerTimeoutDoesNotRemoveSuccessor() {
        Tls tls = Tls.builder().enabled(false).build();
        Proxy proxy = Proxy.builder().host("proxy.example").port(8080).build();
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

        when(http2Client.http1FallbackClient()).thenReturn(http1Client);
        when(http1Client.method(Method.GET)).thenReturn(fallbackRequest);
        when(fallbackRequest.headers()).thenReturn(fallbackHeaders);
        when(request.connection()).thenReturn(Optional.empty());
        when(request.tcpProtocolIds()).thenReturn(List.of(Http1Client.PROTOCOL_ID));
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
            for (int i = 1; i <= 1_000; i++) {
                newStream.apply(target.apply(i));
            }
            ClientConnectionTarget equalTarget = target.apply(0);
            Http2ConnectionAttemptResult successor = newStream.apply(equalTarget);

            assertThat(equalTarget, is(firstTarget));
            assertThat(successor.handler(), not(sameInstance(predecessor.handler())));

            cache.remove(firstTarget, predecessor.handler());
            Http2ConnectionAttemptResult retained = newStream.apply(equalTarget);

            assertThat(retained.handler(), sameInstance(successor.handler()));
        } finally {
            cache.closeResource();
        }
    }
}
