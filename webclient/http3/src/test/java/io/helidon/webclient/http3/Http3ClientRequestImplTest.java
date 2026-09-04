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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.ClientResponseHeaders;
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
