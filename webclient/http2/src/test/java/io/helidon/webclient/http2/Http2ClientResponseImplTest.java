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

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.GenericType;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.MediaSupport;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.api.RedirectSecurityState;
import io.helidon.webclient.api.ReleasableResource;
import io.helidon.webclient.api.WebClientServiceRequest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http2ClientResponseImplTest {
    private static final HeaderName DECODER_HEADER = HeaderNames.create("X-Decoder-Request");

    @Test
    void truncatedEntityCompletesLifecycleExceptionally() throws Exception {
        EOFException expected = new EOFException("simulated truncated HTTP/2 entity");
        InputStream truncatedEntity = new InputStream() {
            private boolean byteReturned;

            @Override
            public int read() throws IOException {
                if (!byteReturned) {
                    byteReturned = true;
                    return 'd';
                }
                throw expected;
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                if (!byteReturned) {
                    byteReturned = true;
                    bytes[offset] = 'd';
                    return 1;
                }
                throw expected;
            }
        };
        CompletableFuture<Void> lifecycle = new CompletableFuture<>();
        AtomicInteger cleanupCount = new AtomicInteger();
        Http2ClientResponseImpl response = response(truncatedEntity,
                                                    CompletableFuture.completedFuture(
                                                            ClientResponseTrailers.create()),
                                                    lifecycle,
                                                    cleanupCount);

        UncheckedIOException actual = assertThrows(UncheckedIOException.class,
                                                   () -> response.entity().as(byte[].class));

        assertThat(actual.getCause(), sameInstance(expected));
        ExecutionException lifecycleFailure = assertThrows(
                ExecutionException.class,
                () -> lifecycle.get(5, TimeUnit.SECONDS));
        assertThat(lifecycleFailure.getCause(), sameInstance(actual));
        assertThat(cleanupCount.get(), is(1));
    }

    @Test
    void trailerFutureFailureCompletesLifecycleExceptionally() throws Exception {
        IllegalStateException expected = new IllegalStateException("simulated HTTP/2 trailer failure");
        CompletableFuture<Void> lifecycle = new CompletableFuture<>();
        AtomicInteger cleanupCount = new AtomicInteger();
        Http2ClientResponseImpl response = response(null,
                                                    CompletableFuture.failedFuture(expected),
                                                    lifecycle,
                                                    cleanupCount);
        response.serviceEntityConsumed();

        IllegalStateException actual = assertThrows(IllegalStateException.class, response::trailers);

        assertThat(actual, sameInstance(expected));
        ExecutionException lifecycleFailure = assertThrows(
                ExecutionException.class,
                () -> lifecycle.get(5, TimeUnit.SECONDS));
        assertThat(lifecycleFailure.getCause(), sameInstance(expected));
        assertThat(cleanupCount.get(), is(1));
    }

    @Test
    void transportCleanupFailureStillClosesDecoratorOnce() throws Exception {
        IllegalStateException transportFailure = new IllegalStateException("transport cleanup failed");
        IllegalStateException decoratorFailure = new IllegalStateException("decorator cleanup failed");
        List<String> closedResources = new ArrayList<>();
        ReleasableResource rawResource = () -> {
            closedResources.add("transport");
            throw transportFailure;
        };
        ReleasableResource returnedResource = () -> {
            closedResources.add("decorator");
            throw decoratorFailure;
        };
        CompletableFuture<ClientResponseTrailers> trailers = new CompletableFuture<>();
        CompletableFuture<Void> lifecycle = new CompletableFuture<>();
        Http2ClientResponseImpl response = response(null,
                                                    trailers,
                                                    lifecycle,
                                                    returnedResource,
                                                    rawResource,
                                                    rawResource::closeResource);

        IllegalStateException actual = assertThrows(IllegalStateException.class, response::close);

        assertThat(actual, sameInstance(transportFailure));
        assertThat(actual.getSuppressed(), arrayContaining(decoratorFailure));
        ExecutionException completion = assertThrows(ExecutionException.class,
                                                     () -> lifecycle.get(5, TimeUnit.SECONDS));
        assertThat(completion.getCause(), sameInstance(transportFailure));
        ExecutionException trailerCompletion = assertThrows(ExecutionException.class,
                                                            () -> trailers.get(5, TimeUnit.SECONDS));
        assertThat(trailerCompletion.getCause(), sameInstance(transportFailure));

        response.close();
        assertThat(closedResources, contains("transport", "decorator"));
    }

    @Test
    void trailerFailureRetainsItsCauseWhenBothResourcesFailToClose() throws Exception {
        IllegalStateException trailerFailure = new IllegalStateException("trailers failed");
        IllegalStateException transportFailure = new IllegalStateException("transport cleanup failed");
        IllegalStateException decoratorFailure = new IllegalStateException("decorator cleanup failed");
        List<String> closedResources = new ArrayList<>();
        ReleasableResource rawResource = () -> {
            closedResources.add("transport");
            throw transportFailure;
        };
        ReleasableResource returnedResource = () -> {
            closedResources.add("decorator");
            throw decoratorFailure;
        };
        CompletableFuture<Void> lifecycle = new CompletableFuture<>();
        Http2ClientResponseImpl response = response(null,
                                                    CompletableFuture.failedFuture(trailerFailure),
                                                    lifecycle,
                                                    returnedResource,
                                                    rawResource,
                                                    rawResource::closeResource);
        response.serviceEntityConsumed();

        IllegalStateException actual = assertThrows(IllegalStateException.class, response::trailers);

        assertThat(actual, sameInstance(trailerFailure));
        assertThat(actual.getSuppressed(), arrayContaining(transportFailure, decoratorFailure));
        ExecutionException completion = assertThrows(ExecutionException.class,
                                                     () -> lifecycle.get(5, TimeUnit.SECONDS));
        assertThat(completion.getCause(), sameInstance(trailerFailure));

        response.close();
        assertThat(closedResources, contains("transport", "decorator"));
    }

    @Test
    void responseDecodingUsesFinalizedRequestHeaderSnapshot() {
        ClientRequestHeaders finalizedHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        finalizedHeaders.set(DECODER_HEADER, "sent");
        ClientRequestHeaders liveServiceHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        liveServiceHeaders.set(DECODER_HEADER, "sent");
        WebClientServiceRequest serviceRequest = mock(WebClientServiceRequest.class);
        when(serviceRequest.headers()).thenReturn(liveServiceHeaders);
        ReleasableResource resource = () -> { };
        Http2ClientResponseImpl response = new Http2ClientResponseImpl(
                HttpClientConfig.builder().build(),
                Http2Client.PROTOCOL_ID,
                Status.OK_200,
                serviceRequest,
                finalizedHeaders,
                RedirectSecurityState.initial(),
                ClientResponseHeaders.create(WritableHeaders.create()),
                CompletableFuture.failedFuture(new IllegalStateException("No trailers are expected.")),
                new ByteArrayInputStream(new byte[] {'x'}),
                decoderMediaContext(),
                ClientUri.create(URI.create("https://localhost/test")),
                resource,
                resource,
                null,
                new CompletableFuture<>(),
                () -> { },
                1024);

        liveServiceHeaders.set(DECODER_HEADER, "mutated-after-proceed");

        assertThat(response.entity().as(DecodedRequestHeader.class).value(), is("sent"));
    }

    private static Http2ClientResponseImpl response(InputStream inputStream,
                                                    CompletableFuture<ClientResponseTrailers> trailers,
                                                    CompletableFuture<Void> lifecycle,
                                                    AtomicInteger cleanupCount) {
        ReleasableResource resource = () -> { };
        return response(inputStream, trailers, lifecycle, resource, resource, cleanupCount::incrementAndGet);
    }

    private static Http2ClientResponseImpl response(InputStream inputStream,
                                                    CompletableFuture<ClientResponseTrailers> trailers,
                                                    CompletableFuture<Void> lifecycle,
                                                    ReleasableResource returnedResource,
                                                    ReleasableResource rawResource,
                                                    Runnable cleanup) {
        ClientRequestHeaders requestHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        WritableHeaders<?> responseHeaders = WritableHeaders.create();
        responseHeaders.add(HeaderNames.TRAILER, "checksum");
        WebClientServiceRequest serviceRequest = mock(WebClientServiceRequest.class);
        when(serviceRequest.headers()).thenReturn(requestHeaders);
        return new Http2ClientResponseImpl(HttpClientConfig.builder().build(),
                                           Http2Client.PROTOCOL_ID,
                                           Status.OK_200,
                                           serviceRequest,
                                           requestHeaders,
                                           RedirectSecurityState.initial(),
                                           ClientResponseHeaders.create(responseHeaders),
                                           trailers,
                                           inputStream,
                                           MediaContext.create(),
                                           ClientUri.create(URI.create("https://localhost/test")),
                                           returnedResource,
                                           rawResource,
                                           null,
                                           lifecycle,
                                           cleanup,
                                           1024);
    }

    private static MediaContext decoderMediaContext() {
        EntityReader<DecodedRequestHeader> reader = new EntityReader<>() {
            @Override
            public DecodedRequestHeader read(GenericType<DecodedRequestHeader> type,
                                             InputStream stream,
                                             Headers headers) {
                throw new AssertionError("Server reader must not be used");
            }

            @Override
            public DecodedRequestHeader read(GenericType<DecodedRequestHeader> type,
                                             InputStream stream,
                                             Headers requestHeaders,
                                             Headers responseHeaders) {
                try (stream) {
                    return new DecodedRequestHeader(requestHeaders.get(DECODER_HEADER).get());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        MediaSupport support = new MediaSupport() {
            @Override
            public String name() {
                return "finalized-request-header-test";
            }

            @Override
            public String type() {
                return "finalized-request-header-test";
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> ReaderResponse<T> reader(GenericType<T> type,
                                                Headers requestHeaders,
                                                Headers responseHeaders) {
                return new ReaderResponse<>(SupportLevel.SUPPORTED, () -> (EntityReader<T>) reader);
            }
        };
        return MediaContext.builder()
                .registerDefaults(false)
                .mediaSupportsDiscoverServices(false)
                .addMediaSupport(support)
                .build();
    }

    private record DecodedRequestHeader(String value) {
    }
}
