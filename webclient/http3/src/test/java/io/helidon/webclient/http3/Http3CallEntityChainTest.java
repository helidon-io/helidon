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
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.uri.UriAuthority;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.ResolvedClientTarget;
import io.helidon.webclient.api.WebClientProtocolResponse;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class Http3CallEntityChainTest {

    @Test
    void returnsDirectProtocolResponseWithExactTransportContextOnce() {
        Http3ClientImpl http3Client = notificationClient(true);
        Http3Discovery.Selection selection = selection(Http3Discovery.Target.direct(
                ClientUri.create(URI.create("https://origin.example"))));
        Http3StreamedResponse response = mock(Http3StreamedResponse.class);
        ResolvedClientTarget resolvedTarget = mock(ResolvedClientTarget.class);
        Instant receivedAt = Instant.parse("2026-09-03T14:15:16Z");
        ClientResponseHeaders headers = altSvcHeaders();
        when(response.resolvedTarget()).thenReturn(resolvedTarget);
        when(response.receivedAt()).thenReturn(receivedAt);
        Http3CallEntityChain chain = chain(http3Client);

        chain.captureProtocolResponse(selection, response, Status.OK_200, headers);
        WebClientProtocolResponse protocolResponse = chain.protocolResponse(mock(WebClientServiceResponse.class))
                .orElseThrow();

        assertThat(protocolResponse.target(), sameInstance(resolvedTarget));
        assertThat(protocolResponse.receivedAt(), sameInstance(receivedAt));
        assertThat(protocolResponse.protocolId(), is(Http3Client.PROTOCOL_ID));
        assertThat(protocolResponse.status(), sameInstance(Status.OK_200));
        assertThat(protocolResponse.alternativeAuthority().isEmpty(), is(true));
        assertThat(chain.protocolResponse(mock(WebClientServiceResponse.class)).isEmpty(), is(true));
        verify(http3Client).recordSuccess(selection);
    }

    @Test
    void returnsAlternativeProtocolResponseWithExactUsedAuthority() {
        Http3ClientImpl http3Client = notificationClient(true);
        Http3Discovery.Selection selection = selection(Http3Discovery.Target.alternative("alternative.example", 8443));
        Http3StreamedResponse response = mock(Http3StreamedResponse.class);
        ResolvedClientTarget resolvedTarget = mock(ResolvedClientTarget.class);
        UriAuthority routeAuthority = UriAuthority.create("alternative.example:8443");
        Instant receivedAt = Instant.parse("2026-09-03T14:16:17Z");
        when(response.resolvedTarget()).thenReturn(resolvedTarget);
        when(response.receivedAt()).thenReturn(receivedAt);
        when(resolvedTarget.routeAuthority()).thenReturn(routeAuthority);
        Http3CallEntityChain chain = chain(http3Client);

        chain.captureProtocolResponse(selection, response, Status.OK_200, altSvcHeaders());
        WebClientProtocolResponse protocolResponse = chain.protocolResponse(mock(WebClientServiceResponse.class))
                .orElseThrow();

        assertThat(protocolResponse.target(), sameInstance(resolvedTarget));
        assertThat(protocolResponse.receivedAt(), sameInstance(receivedAt));
        assertThat(protocolResponse.alternativeAuthority().orElseThrow(), sameInstance(routeAuthority));
        verify(http3Client).recordSuccess(selection);
    }

    @Test
    void invalidatesMisdirectedSelectionWithoutPublishingOrTeaching() {
        Http3ClientImpl http3Client = mock(Http3ClientImpl.class);
        Http3ConnectionCache connectionCache = mock(Http3ConnectionCache.class);
        Http3Discovery discovery = mock(Http3Discovery.class);
        Http3Discovery.Selection selection = selection(Http3Discovery.Target.alternative("alternative.example", 8443));
        Http3StreamedResponse response = mock(Http3StreamedResponse.class);
        when(http3Client.connectionCache()).thenReturn(connectionCache);
        when(connectionCache.discovery()).thenReturn(discovery);
        Http3CallEntityChain chain = chain(http3Client);

        chain.captureProtocolResponse(selection, response, Status.MISDIRECTED_REQUEST_421, altSvcHeaders());

        verify(discovery).recordMisdirected(selection);
        verify(http3Client, never()).recordSuccess(any());
        verifyNoMoreInteractions(response);
        assertThat(chain.protocolResponse(mock(WebClientServiceResponse.class)).isEmpty(), is(true));
    }

    @Test
    void standaloneClientPublishesLocallyExactlyOnce() {
        Http3ClientImpl http3Client = notificationClient(false);
        Http3Discovery.Selection selection = selection(Http3Discovery.Target.direct(
                ClientUri.create(URI.create("https://origin.example"))));
        Http3StreamedResponse response = mock(Http3StreamedResponse.class);
        ResolvedClientTarget resolvedTarget = mock(ResolvedClientTarget.class);
        when(response.resolvedTarget()).thenReturn(resolvedTarget);
        when(response.receivedAt()).thenReturn(Instant.parse("2026-09-03T14:17:18Z"));
        Http3CallEntityChain chain = chain(http3Client);

        chain.captureProtocolResponse(selection, response, Status.OK_200, altSvcHeaders());

        assertThat(chain.protocolResponse(mock(WebClientServiceResponse.class)).isEmpty(), is(true));
        verify(http3Client, times(1)).publishResponse(any(WebClientProtocolResponse.class));
        assertThat(chain.protocolResponse(mock(WebClientServiceResponse.class)).isEmpty(), is(true));
        verify(http3Client, times(1)).publishResponse(any(WebClientProtocolResponse.class));
    }

    @Test
    void closesFallbackResponseWhenAdaptationFails() {
        Http3ClientImpl http3Client = mock(Http3ClientImpl.class);
        Http3ClientRequestImpl clientRequest = mock(Http3ClientRequestImpl.class);
        Http3RequestBody requestBody = mock(Http3RequestBody.class);
        WebClientServiceRequest serviceRequest = mock(WebClientServiceRequest.class);
        HttpClientResponse fallbackResponse = mock(HttpClientResponse.class);
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        AtomicReference<String> protocolId = new AtomicReference<>(Http3Client.PROTOCOL_ID);
        ClientUri uri = ClientUri.create(URI.create("https://example.test/request"));
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        IllegalStateException adaptationFailure = new IllegalStateException("simulated adaptation failure");

        when(serviceRequest.uri()).thenReturn(uri);
        when(serviceRequest.headers()).thenReturn(headers);
        when(http3Client.requestTarget(clientRequest, uri, headers, true)).thenReturn(Optional.empty());
        when(clientRequest.fallbackResponse(same(requestBody),
                                            same(serviceRequest),
                                            eq(false),
                                            same(whenSent),
                                            any())).thenReturn(fallbackResponse);
        when(fallbackResponse.protocolId()).thenThrow(adaptationFailure);

        Http3CallEntityChain chain = new Http3CallEntityChain(http3Client,
                                                               clientRequest,
                                                               true,
                                                               protocolId,
                                                               whenSent,
                                                               whenComplete,
                                                               requestBody);

        assertThat(assertThrows(IllegalStateException.class, () -> chain.proceed(serviceRequest)),
                   sameInstance(adaptationFailure));
        assertThat(whenSent.isCompletedExceptionally(), is(true));
        verify(fallbackResponse, times(1)).close();
    }

    private static Http3ClientImpl notificationClient(boolean managedByWebClient) {
        Http3ClientImpl client = mock(Http3ClientImpl.class);
        when(client.altSvcNotificationsEnabled()).thenReturn(true);
        when(client.responseNotificationsManagedByWebClient()).thenReturn(managedByWebClient);
        return client;
    }

    private static Http3Discovery.Selection selection(Http3Discovery.Target target) {
        Http3Discovery.Selection selection = mock(Http3Discovery.Selection.class);
        when(selection.target()).thenReturn(target);
        return selection;
    }

    private static Http3CallEntityChain chain(Http3ClientImpl client) {
        return new Http3CallEntityChain(client,
                                        mock(Http3ClientRequestImpl.class),
                                        true,
                                        new AtomicReference<>(Http3Client.PROTOCOL_ID),
                                        new CompletableFuture<>(),
                                        new CompletableFuture<>(),
                                        mock(Http3RequestBody.class));
    }

    private static ClientResponseHeaders altSvcHeaders() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.set(HeaderNames.ALT_SVC, "h3=\":8443\"");
        return ClientResponseHeaders.create(headers);
    }
}
