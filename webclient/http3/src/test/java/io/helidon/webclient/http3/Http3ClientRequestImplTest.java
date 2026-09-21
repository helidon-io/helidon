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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ReleasableResource;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http3ClientRequestImplTest {

    @Test
    void adaptationFailureClosesDistinctDecoratorAndRawResources() {
        AdaptationFailure result = invokeAdaptationFailure(false);

        verify(result.returnedResource(), times(1)).closeResource();
        verify(result.rawResource(), times(1)).closeResource();
        assertFailedCompletions(result);
    }

    @Test
    void adaptationFailureClosesAliasedDecoratorAndRawResourceOnce() {
        AdaptationFailure result = invokeAdaptationFailure(true);

        verify(result.rawResource(), times(1)).closeResource();
        assertThat(result.returnedResource(), sameInstance(result.rawResource()));
        assertFailedCompletions(result);
    }

    @Test
    void normalizesBufferedContentLengthAfterServiceMutation() {
        assertBufferedServicePreparation(Method.POST, new byte[5], true, Optional.of("5"));
    }

    @Test
    void normalizesEmptyContentLengthAfterServiceMutation() {
        assertBufferedServicePreparation(Method.POST, new byte[0], true, Optional.of("0"));
    }

    @Test
    void leavesContentLengthAbsentForEmptyRequest() {
        assertBufferedServicePreparation(Method.GET, new byte[0], false, Optional.empty());
    }

    private static void assertBufferedServicePreparation(Method method,
                                                         byte[] body,
                                                         boolean serviceChangesLength,
                                                         Optional<String> expectedLength) {
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        AtomicReference<WebClientServiceResponse> rawResponse = new AtomicReference<>();
        ReleasableResource resource = mock(ReleasableResource.class);
        Http3CallEntityChain callChain = mock(Http3CallEntityChain.class);
        when(callChain.protocolId()).thenReturn(Http3Client.PROTOCOL_ID);
        when(callChain.rawResponse()).thenAnswer(_ -> rawResponse.get());
        when(callChain.proceed(any())).thenAnswer(invocation -> {
            WebClientServiceRequest serviceRequest = invocation.getArgument(0);
            assertThat("content length at terminal transport dispatch",
                       serviceRequest.headers().first(HeaderNames.CONTENT_LENGTH),
                       is(expectedLength));
            whenSent.complete(serviceRequest);
            WebClientServiceResponse response = WebClientServiceResponse.builder()
                    .serviceRequest(serviceRequest)
                    .connection(resource)
                    .whenComplete(whenComplete)
                    .status(Status.OK_200)
                    .headers(ClientResponseHeaders.create(WritableHeaders.create()))
                    .build();
            rawResponse.set(response);
            return response;
        });

        Http3ClientImpl client = (Http3ClientImpl) Http3Client.builder()
                .baseUri("https://example.test")
                .servicesDiscoverServices(false)
                .addService((chain, request) -> {
                    Optional<String> initialLength = serviceChangesLength
                            ? Optional.of(Integer.toString(body.length))
                            : Optional.empty();
                    assertThat("content length before service mutation",
                               request.headers().first(HeaderNames.CONTENT_LENGTH),
                               is(initialLength));
                    if (serviceChangesLength) {
                        request.headers().set(HeaderNames.CONTENT_LENGTH, body.length + 1);
                    }
                    // Continue to terminal preparation; only the transport response is stubbed.
                    WebClientServiceResponse response = chain.proceed(request);
                    assertThat("prepared content length visible to the service",
                               request.headers().first(HeaderNames.CONTENT_LENGTH),
                               is(expectedLength));
                    return response;
                })
                .shareConnectionCache(false)
                .build();
        try {
            Http3ClientRequestImpl request = (Http3ClientRequestImpl) client.method(method);
            if (serviceChangesLength) {
                request.headers().set(HeaderNames.CONTENT_LENGTH, body.length);
            }
            try (Http3ClientResponse response = request.invokeWithServices(callChain,
                                                                           whenSent,
                                                                           whenComplete,
                                                                           Http3RequestBody.create(body))) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat("finalized request content length",
                           request.headers().first(HeaderNames.CONTENT_LENGTH),
                           is(expectedLength));
            }
            verify(callChain, times(1)).proceed(any());
            verify(resource, times(1)).closeResource();
        } finally {
            client.closeResource();
        }
    }

    private static AdaptationFailure invokeAdaptationFailure(boolean aliasResources) {
        IllegalStateException adaptationFailure = new IllegalStateException("simulated trailer adaptation failure");
        ReleasableResource rawResource = mock(ReleasableResource.class);
        ReleasableResource returnedResource = aliasResources ? rawResource : mock(ReleasableResource.class);
        WebClientServiceResponse rawResponse = mock(WebClientServiceResponse.class);
        WebClientServiceResponse decoratedResponse = mock(WebClientServiceResponse.class);
        AtomicReference<WebClientServiceRequest> serviceRequest = new AtomicReference<>();
        CompletableFuture<WebClientServiceResponse> decoratedCompletion = new CompletableFuture<>();
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        ClientResponseHeaders responseHeaders = ClientResponseHeaders.create(WritableHeaders.create());

        when(rawResponse.serviceRequest()).thenAnswer(_ -> serviceRequest.get());
        when(rawResponse.connection()).thenReturn(rawResource);
        when(decoratedResponse.serviceRequest()).thenAnswer(_ -> serviceRequest.get());
        when(decoratedResponse.connection()).thenReturn(returnedResource);
        when(decoratedResponse.whenComplete()).thenReturn(decoratedCompletion);
        when(decoratedResponse.status()).thenReturn(Status.OK_200);
        when(decoratedResponse.headers()).thenReturn(responseHeaders);
        when(decoratedResponse.trailers()).thenThrow(adaptationFailure);

        Http3ClientImpl client = (Http3ClientImpl) Http3Client.builder()
                .baseUri("https://example.test")
                .servicesDiscoverServices(false)
                .addService((chain, request) -> {
                    serviceRequest.set(request);
                    chain.proceed(request);
                    return decoratedResponse;
                })
                .shareConnectionCache(false)
                .build();
        try {
            Http3ClientRequestImpl request = (Http3ClientRequestImpl) client.get("/request");
            Http3CallEntityChain callChain = mock(Http3CallEntityChain.class);
            Http3RequestBody requestBody = mock(Http3RequestBody.class);
            when(callChain.proceed(any())).thenReturn(rawResponse);
            when(callChain.rawResponse()).thenReturn(rawResponse);
            when(callChain.protocolId()).thenReturn(Http3Client.PROTOCOL_ID);

            assertThat(assertThrows(IllegalStateException.class,
                                    () -> request.invokeWithServices(callChain,
                                                                     whenSent,
                                                                     whenComplete,
                                                                     requestBody)),
                       sameInstance(adaptationFailure));
        } finally {
            client.closeResource();
        }
        return new AdaptationFailure(rawResource,
                                     returnedResource,
                                     decoratedCompletion,
                                     whenSent,
                                     whenComplete);
    }

    private static void assertFailedCompletions(AdaptationFailure result) {
        assertThat(result.decoratedCompletion().isCompletedExceptionally(), is(true));
        assertThat(result.whenSent().isCompletedExceptionally(), is(true));
        assertThat(result.whenComplete().isCompletedExceptionally(), is(true));
    }

    private record AdaptationFailure(ReleasableResource rawResource,
                                     ReleasableResource returnedResource,
                                     CompletableFuture<WebClientServiceResponse> decoratedCompletion,
                                     CompletableFuture<WebClientServiceRequest> whenSent,
                                     CompletableFuture<WebClientServiceResponse> whenComplete) {
    }
}
