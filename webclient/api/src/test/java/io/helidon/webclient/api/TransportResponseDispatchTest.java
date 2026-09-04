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

import java.net.InetAddress;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.context.Context;
import io.helidon.common.tls.Tls;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

@Isolated
class TransportResponseDispatchTest {

    @Test
    void publishesRawResponseAfterNetworkProceedAndBeforeDecoratedServicesUnwind() {
        TestHttpClientSpiProvider.reset();
        WebClientProtocolResponse protocolResponse = protocolResponse(false);
        AtomicBoolean networkProceeded = new AtomicBoolean();
        AtomicBoolean serviceObservedPublication = new AtomicBoolean();
        AtomicInteger responseExtractions = new AtomicInteger();
        HttpClientConfig requestConfig = HttpClientConfig.builder()
                .addService((chain, request) -> {
                    WebClientServiceResponse rawResponse = chain.proceed(request);
                    serviceObservedPublication.set(TestHttpClientSpiProvider.protocolResponse() == protocolResponse);
                    return response(rawResponse.serviceRequest(), Status.CREATED_201);
                })
                .build();
        DispatchRequest request = new DispatchRequest(requestConfig);
        WebClient client = WebClient.builder()
                .servicesDiscoverServices(false)
                .protocolPreference(List.of(TestHttpClientSpiProvider.PROTOCOL_ID))
                .build();
        try {
            WebClientService.TransportChain transport = new WebClientService.TransportChain() {
                @Override
                public String protocolId() {
                    return "http/1.1";
                }

                @Override
                public WebClientServiceResponse proceed(WebClientServiceRequest serviceRequest) {
                    networkProceeded.set(true);
                    return response(serviceRequest, Status.OK_200);
                }

                @Override
                public Optional<WebClientProtocolResponse> protocolResponse(WebClientServiceResponse response) {
                    assertThat(networkProceeded.get(), is(true));
                    assertThat(response.status(), sameInstance(Status.OK_200));
                    responseExtractions.incrementAndGet();
                    return Optional.of(protocolResponse);
                }
            };

            WebClientServiceResponse response = request.invoke(client, transport);

            assertThat(response.status(), sameInstance(Status.CREATED_201));
            assertThat(TestHttpClientSpiProvider.protocolResponse(), sameInstance(protocolResponse));
            assertThat(serviceObservedPublication.get(), is(true));
            assertThat(responseExtractions.get(), is(1));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void forwardsExactProtocolResponseBeforeHandoffResponseConsumerWithoutDuplicatePublication() {
        TestHttpClientSpiProvider.reset();
        DispatchRequest request = new DispatchRequest(HttpClientConfig.create());
        WebClient client = WebClient.builder()
                .servicesDiscoverServices(false)
                .protocolPreference(List.of(TestHttpClientSpiProvider.PROTOCOL_ID))
                .build();
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        WebClientServiceRequest serviceRequest = new ServiceRequestImpl(
                ClientUri.create(URI.create("http://origin.example")),
                Method.GET,
                "http/1.1",
                null,
                ClientRequestHeaders.create(WritableHeaders.create()),
                Context.create(),
                "handoff",
                whenComplete,
                whenSent,
                Map.of());
        WebClientProtocolResponse protocolResponse = protocolResponse(true);
        AtomicBoolean protocolForwarded = new AtomicBoolean();
        AtomicBoolean responseObservedForwarding = new AtomicBoolean();
        AtomicInteger responseExtractions = new AtomicInteger();
        AtomicReference<WebClientProtocolResponse> forwardedResponse = new AtomicReference<>();
        assertThat(request.serviceRequestAfterServices(serviceRequest,
                                                       _ -> responseObservedForwarding.set(protocolForwarded.get()),
                                                       whenSent,
                                                       _ -> { },
                                                       true,
                                                       response -> {
                                                           forwardedResponse.set(response);
                                                           protocolForwarded.set(true);
                                                       }),
                   is(true));
        try {
            WebClientService.TransportChain transport = new WebClientService.TransportChain() {
                @Override
                public String protocolId() {
                    return "http/1.1";
                }

                @Override
                public WebClientServiceResponse proceed(WebClientServiceRequest request) {
                    return response(request, Status.OK_200);
                }

                @Override
                public Optional<WebClientProtocolResponse> protocolResponse(WebClientServiceResponse response) {
                    responseExtractions.incrementAndGet();
                    return Optional.of(protocolResponse);
                }
            };

            request.invoke(client, transport);

            assertThat(forwardedResponse.get(), sameInstance(protocolResponse));
            assertThat(protocolResponse.explicitConnection(), is(true));
            assertThat(responseObservedForwarding.get(), is(true));
            assertThat(responseExtractions.get(), is(1));
            assertThat(TestHttpClientSpiProvider.protocolResponse() == null, is(true));
        } finally {
            client.closeResource();
        }
    }

    private static WebClientServiceResponse response(WebClientServiceRequest request, Status status) {
        return WebClientServiceResponse.builder()
                .serviceRequest(request)
                .whenComplete(new CompletableFuture<>())
                .connection(() -> { })
                .status(status)
                .headers(ClientResponseHeaders.create(WritableHeaders.create()))
                .build();
    }

    private static WebClientProtocolResponse protocolResponse(boolean explicitConnection) {
        ClientUri uri = ClientUri.create(URI.create("http://origin.example"));
        ConnectionKey connectionKey = ConnectionKey.create(uri,
                                                           Tls.builder().enabled(false).build(),
                                                           (_, _) -> InetAddress.getLoopbackAddress(),
                                                           DnsAddressLookup.IPV4,
                                                           Proxy.noProxy());
        ResolvedClientTarget target = ClientConnectionTarget.create(connectionKey, "http").resolve();
        return WebClientProtocolResponse.create(target,
                                                explicitConnection,
                                                "http/1.1",
                                                Status.OK_200,
                                                ClientResponseHeaders.create(WritableHeaders.create()),
                                                Instant.parse("2026-08-23T00:01:30Z"));
    }

    private static final class DispatchRequest extends ClientRequestBase<DispatchRequest, HttpClientResponse> {
        private DispatchRequest(HttpClientConfig config) {
            super(config,
                  WebClientCookieManager.builder().build(),
                  "http/1.1",
                  Method.GET,
                  ClientUri.create(URI.create("http://origin.example")),
                  Map.of());
        }

        @Override
        protected HttpClientResponse doSubmit(Object entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected HttpClientResponse doOutputStream(OutputStreamHandler outputStreamHandler) {
            throw new UnsupportedOperationException();
        }

        private WebClientServiceResponse invoke(WebClient client, WebClientService.TransportChain transport) {
            return invokeServices(client,
                                  transport,
                                  new CompletableFuture<>(),
                                  new CompletableFuture<>(),
                                  resolvedUri());
        }
    }
}
