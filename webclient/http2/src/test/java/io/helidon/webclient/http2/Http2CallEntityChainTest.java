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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.LazyString;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.InstanceWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientProtocolResponse;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http2CallEntityChainTest {

    @Test
    void http1FallbackPublishesAndHandsOffExactProtocolResponseOnce() {
        Http2ClientImpl client = (Http2ClientImpl) Http2Client.builder()
                .baseUri("https://localhost")
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .build();
        try {
            ClientUri uri = ClientUri.create(URI.create("https://localhost"));
            Http2ClientRequestImpl request = new Http2ClientRequestImpl(client,
                                                                        null,
                                                                        Method.GET,
                                                                        uri,
                                                                        Map.of());
            WebClientServiceRequest serviceRequest = mock(WebClientServiceRequest.class);
            when(serviceRequest.context()).thenReturn(Context.create());
            AtomicInteger handoffCount = new AtomicInteger();
            AtomicReference<WebClientProtocolResponse> handedOff = new AtomicReference<>();
            request.serviceRequestAfterServices(serviceRequest,
                                                _ -> {
                                                },
                                                new CompletableFuture<>(),
                                                _ -> {
                                                },
                                                true,
                                                response -> {
                                                    handoffCount.incrementAndGet();
                                                    handedOff.set(response);
                                                });

            Http1FallbackHandler fallbackHandler = new Http1FallbackHandler(new CompletableFuture<>(),
                                                                             _ -> null,
                                                                             () -> false,
                                                                             () -> false,
                                                                             request::handoffProtocolResponse);
            Context context = Http1FallbackService.context(serviceRequest, fallbackHandler);
            AtomicInteger publicationCount = new AtomicInteger();
            AtomicReference<WebClientProtocolResponse> published = new AtomicReference<>();
            Http2ResponseForwardingWebClient forwardingClient =
                    new Http2ResponseForwardingWebClient(mock(WebClient.class), response -> {
                        publicationCount.incrementAndGet();
                        published.set(response);
                    });
            var connectionKey = Http2ConnectionKeys.create(uri, request, client.clientConfig());
            var target = ClientConnectionTarget.create(connectionKey, uri, request.headers()).resolve();
            WebClientProtocolResponse protocolResponse = WebClientProtocolResponse.create(
                    target,
                    false,
                    Http1Client.PROTOCOL_ID,
                    Status.OK_200,
                    ClientResponseHeaders.create(WritableHeaders.create()),
                    Instant.EPOCH);

            Contexts.runInContext(context, () -> forwardingClient.responseReceived(protocolResponse));

            assertThat(publicationCount.get(), is(1));
            assertThat(published.get(), sameInstance(protocolResponse));
            assertThat(handoffCount.get(), is(1));
            assertThat(handedOff.get(), sameInstance(protocolResponse));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void emptyByteArrayIsEmptyBeforePreparation() {
        Http2CallEntityChain.RequestEntity entity = Http2CallEntityChain.RequestEntity.create(new byte[0]);

        assertThat(entity.isEmpty(), is(true));
    }

    @Test
    void nullRequestEntityRemainsUnsupported() {
        Http2CallEntityChain.RequestEntity entity = Http2CallEntityChain.RequestEntity.create(null);

        assertThrows(NullPointerException.class,
                     () -> entity.prepare(ClientRequestHeaders.create(WritableHeaders.create()),
                                          mock(MediaContext.class),
                                          1024,
                                          1024,
                                          Context.create()));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void knownLengthNonInstanceWriterIsSerializedOnlyOnce() {
        AtomicInteger writes = new AtomicInteger();
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        headers.contentLength(7);
        MediaContext mediaContext = mock(MediaContext.class);
        EntityWriter<Object> writer = new EntityWriter<>() {
            @Override
            public void write(GenericType<Object> type,
                              Object object,
                              OutputStream outputStream,
                              Headers requestHeaders,
                              WritableHeaders<?> responseHeaders) {
                throw new AssertionError("Server writer must not be used");
            }

            @Override
            public void write(GenericType<Object> type,
                              Object object,
                              OutputStream outputStream,
                              WritableHeaders<?> requestHeaders) {
                writes.incrementAndGet();
                try (outputStream) {
                    outputStream.write(object.toString().getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        stubWriter(mediaContext, writer);
        Http2CallEntityChain.RequestEntity entity = Http2CallEntityChain.RequestEntity.create("payload");

        entity.prepare(headers, mediaContext, 1024, 1024, Context.create());
        byte[] first = entity.bytes();
        entity.prepare(headers, mediaContext, 1024, 1024, Context.create());

        assertThat(writes.get(), is(1));
        assertThat(entity.bytes(), is(first));
        assertThat(new String(first, StandardCharsets.UTF_8), is("payload"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void unknownLengthNonInstanceWriterPreflightsAndStreamsOnlyOnce() throws Exception {
        HeaderName writerHeader = HeaderNames.create("X-Preflight-Writer");
        AtomicInteger writes = new AtomicInteger();
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        MediaContext mediaContext = mock(MediaContext.class);
        EntityWriter<Object> writer = new EntityWriter<>() {
            @Override
            public void write(GenericType<Object> type,
                              Object object,
                              OutputStream outputStream,
                              Headers requestHeaders,
                              WritableHeaders<?> responseHeaders) {
                throw new AssertionError("Server writer must not be used");
            }

            @Override
            public void write(GenericType<Object> type,
                              Object object,
                              OutputStream outputStream,
                              WritableHeaders<?> requestHeaders) {
                writes.incrementAndGet();
                requestHeaders.set(writerHeader, "committed");
                try (outputStream) {
                    outputStream.write(object.toString().getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        stubWriter(mediaContext, writer);
        Http2CallEntityChain.RequestEntity entity = Http2CallEntityChain.RequestEntity.create("payload");

        entity.prepare(headers, mediaContext, 4, 4, Context.create());

        assertThat(writes.get(), is(1));
        assertThat(headers.get(writerHeader).get(), is("committed"));
        assertThrows(IllegalStateException.class, entity::bytes);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        entity.writeTo(output);
        assertThat(new String(output.toByteArray(), StandardCharsets.UTF_8), is("payload"));
        assertThat(writes.get(), is(1));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void unknownLengthEntityStreamsOnlyOnce() throws Exception {
        AtomicInteger writes = new AtomicInteger();
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        MediaContext mediaContext = mock(MediaContext.class);
        InstanceWriter instanceWriter = new InstanceWriter() {
            @Override
            public OptionalLong contentLength() {
                return OptionalLong.empty();
            }

            @Override
            public boolean alwaysInMemory() {
                return false;
            }

            @Override
            public void write(OutputStream outputStream) {
                writes.incrementAndGet();
                try (outputStream) {
                    outputStream.write("payload".getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public byte[] instanceBytes() {
                throw new AssertionError("Unknown-length entity must not be materialized");
            }
        };
        EntityWriter<Object> writer = mock(EntityWriter.class);
        when(writer.supportsInstanceWriter()).thenReturn(true);
        when(writer.instanceWriter(any(GenericType.class),
                                   eq("payload"),
                                   any(ClientRequestHeaders.class))).thenReturn(instanceWriter);
        stubWriter(mediaContext, writer);
        Http2CallEntityChain.RequestEntity entity = Http2CallEntityChain.RequestEntity.create("payload");

        entity.prepare(headers, mediaContext, 4, 4, Context.create());

        assertThat("preparation must not materialize an unknown-length entity", writes.get(), is(0));
        assertThrows(IllegalStateException.class, entity::bytes);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        entity.writeTo(output);
        assertThat(new String(output.toByteArray(), StandardCharsets.UTF_8), is("payload"));
        assertThat(writes.get(), is(1));
        assertThrows(IllegalStateException.class, () -> entity.writeTo(new ByteArrayOutputStream()));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void oversizedInstanceWriterStreamsWithoutMaterializing() throws Exception {
        AtomicInteger writes = new AtomicInteger();
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        MediaContext mediaContext = mock(MediaContext.class);
        InstanceWriter instanceWriter = new InstanceWriter() {
            @Override
            public OptionalLong contentLength() {
                return OptionalLong.of(7);
            }

            @Override
            public boolean alwaysInMemory() {
                return false;
            }

            @Override
            public void write(OutputStream stream) {
                writes.incrementAndGet();
                try (stream) {
                    stream.write("payload".getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public byte[] instanceBytes() {
                throw new AssertionError("Oversized entity must not be materialized");
            }
        };
        EntityWriter<Object> writer = mock(EntityWriter.class);
        when(writer.supportsInstanceWriter()).thenReturn(true);
        when(writer.instanceWriter(any(GenericType.class),
                                   eq("payload"),
                                   any(ClientRequestHeaders.class))).thenReturn(instanceWriter);
        stubWriter(mediaContext, writer);
        Http2CallEntityChain.RequestEntity entity = Http2CallEntityChain.RequestEntity.create("payload");

        entity.prepare(headers, mediaContext, 4, 4, Context.create());

        assertThat(writes.get(), is(0));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        entity.writeTo(output);
        assertThat(new String(output.toByteArray(), StandardCharsets.UTF_8), is("payload"));
        assertThat(writes.get(), is(1));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void preparedWriterHeadersAreAppliedToRedirectAttempt() {
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        MediaContext mediaContext = mock(MediaContext.class);
        EntityWriter<Object> writer = new EntityWriter<>() {
            @Override
            public void write(GenericType<Object> type,
                              Object object,
                              OutputStream outputStream,
                              Headers requestHeaders,
                              WritableHeaders<?> responseHeaders) {
                throw new AssertionError("Server writer must not be used");
            }

            @Override
            public void write(GenericType<Object> type,
                              Object object,
                              OutputStream outputStream,
                              WritableHeaders<?> requestHeaders) {
                requestHeaders.set(HeaderNames.CONTENT_TYPE, "application/test");
                try (outputStream) {
                    outputStream.write(object.toString().getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        stubWriter(mediaContext, writer);
        Http2CallEntityChain.RequestEntity entity = Http2CallEntityChain.RequestEntity.create("payload");
        headers.contentLength(7);

        entity.prepare(headers, mediaContext, 1024, 1024, Context.create());
        ClientRequestHeaders redirectedHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        entity.applyPreparedHeaders(redirectedHeaders);

        assertThat(redirectedHeaders.get(HeaderNames.CONTENT_TYPE).get(), is("application/test"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void preparedWriterHeadersUseImmutableSnapshotsForRedirectReplay() {
        HeaderName lazyHeader = HeaderNames.create("X-Lazy-Writer");
        HeaderName multipleHeader = HeaderNames.create("X-Multiple-Writer");
        byte[] lazyBefore = "before".getBytes(StandardCharsets.UTF_8);
        byte[] lazyAfter = "writer".getBytes(StandardCharsets.UTF_8);
        List<String> multipleBefore = new ArrayList<>(List.of("before"));
        List<String> multipleAfter = new ArrayList<>(List.of("writer-one", "writer-two"));
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        headers.set(HeaderValues.create(lazyHeader, new LazyString(lazyBefore, StandardCharsets.UTF_8)));
        headers.set(HeaderValues.create(multipleHeader, multipleBefore));
        MediaContext mediaContext = mock(MediaContext.class);
        EntityWriter<Object> writer = new EntityWriter<>() {
            @Override
            public void write(GenericType<Object> type,
                              Object object,
                              OutputStream outputStream,
                              Headers requestHeaders,
                              WritableHeaders<?> responseHeaders) {
                throw new AssertionError("Server writer must not be used");
            }

            @Override
            public void write(GenericType<Object> type,
                              Object object,
                              OutputStream outputStream,
                              WritableHeaders<?> requestHeaders) {
                System.arraycopy(lazyAfter, 0, lazyBefore, 0, lazyAfter.length);
                multipleBefore.clear();
                multipleBefore.addAll(multipleAfter);
                requestHeaders.set(HeaderValues.create(lazyHeader,
                                                        new LazyString(lazyAfter, StandardCharsets.UTF_8)));
                requestHeaders.set(HeaderValues.create(multipleHeader, multipleAfter));
                try (outputStream) {
                    outputStream.write(object.toString().getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        stubWriter(mediaContext, writer);
        Http2CallEntityChain.RequestEntity entity = Http2CallEntityChain.RequestEntity.create("payload");

        entity.prepare(headers, mediaContext, 1024, 1024, Context.create());
        lazyAfter[0] = 'X';
        multipleAfter.clear();
        multipleAfter.add("poisoned");
        ClientRequestHeaders redirectedHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        entity.applyPreparedHeaders(redirectedHeaders);

        assertThat(redirectedHeaders.get(lazyHeader).get(), is("writer"));
        assertThat(redirectedHeaders.get(multipleHeader).allValues(), is(List.of("writer-one", "writer-two")));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void discardingEntityRestoresHeadersFromBeforeWriterPreparation() {
        HeaderName replacedHeader = HeaderNames.create("X-Replaced-By-Writer");
        HeaderName addedHeader = HeaderNames.create("X-Added-By-Writer");
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        headers.set(replacedHeader, "service-default");
        MediaContext mediaContext = mock(MediaContext.class);
        EntityWriter<Object> writer = new EntityWriter<>() {
            @Override
            public void write(GenericType<Object> type,
                              Object object,
                              OutputStream outputStream,
                              Headers requestHeaders,
                              WritableHeaders<?> responseHeaders) {
                throw new AssertionError("Server writer must not be used");
            }

            @Override
            public void write(GenericType<Object> type,
                              Object object,
                              OutputStream outputStream,
                              WritableHeaders<?> requestHeaders) {
                requestHeaders.set(replacedHeader, "writer-final");
                requestHeaders.set(addedHeader, "writer-only");
                try (outputStream) {
                    outputStream.write(object.toString().getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        stubWriter(mediaContext, writer);
        Http2CallEntityChain.RequestEntity entity = Http2CallEntityChain.RequestEntity.create("payload");

        entity.prepare(headers, mediaContext, 1024, 1024, Context.create());
        ClientRequestHeaders redirectBase = ClientRequestHeaders.create(WritableHeaders.create());
        entity.applyPreparedHeaders(redirectBase);
        assertThat(redirectBase.get(replacedHeader).get(), is("writer-final"));
        assertThat(redirectBase.get(addedHeader).get(), is("writer-only"));
        entity.restoreHeadersBeforePreparation(redirectBase);
        entity.discard();

        assertThat("source-service headers must not become the redirect rollback base",
                   redirectBase.contains(replacedHeader),
                   is(false));
        assertThat(redirectBase.contains(addedHeader), is(false));
    }

    @Test
    void whenSentWaitsForMaterializedDataWrite() throws Exception {
        CountDownLatch writeEntered = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        Http2ClientImpl client = (Http2ClientImpl) Http2Client.builder()
                .baseUri("https://localhost")
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .build();
        try {
            Http2ClientRequestImpl request = (Http2ClientRequestImpl) client.post("/entity");
            ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
            Http2CallEntityChain.RequestEntity entity = Http2CallEntityChain.RequestEntity.create("payload");
            Http2CallEntityChain chain = new Http2CallEntityChain(client,
                                                                  request,
                                                                  whenSent,
                                                                  whenComplete,
                                                                  entity);
            WebClientServiceRequest preparationRequest = mock(WebClientServiceRequest.class);
            when(preparationRequest.headers()).thenReturn(headers);
            when(preparationRequest.context()).thenReturn(Context.create());
            chain.prepareRequest(preparationRequest);
            assertThat(new String(entity.bytes(), StandardCharsets.UTF_8), is("payload"));
            chain.requestUri(ClientUri.create(URI.create("https://localhost/entity")));
            WebClientServiceRequest serviceRequest = mock(WebClientServiceRequest.class);
            when(serviceRequest.method()).thenReturn(Method.POST);
            when(serviceRequest.uri()).thenReturn(ClientUri.create(URI.create("https://localhost/entity")));
            Http2ClientStream stream = responseStream();
            doAnswer(invocation -> {
                writeEntered.countDown();
                if (!releaseWrite.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to write HTTP/2 request DATA.");
                }
                return null;
            }).when(stream).writeData(any(BufferData.class), eq(true));

            CompletableFuture<WebClientServiceResponse> response = CompletableFuture.supplyAsync(
                    () -> chain.doProceed(serviceRequest, headers, stream));
            assertThat(writeEntered.await(5, TimeUnit.SECONDS), is(true));
            assertThat("whenSent must remain incomplete while the final DATA write is blocked",
                       whenSent.isDone(),
                       is(false));

            releaseWrite.countDown();
            response.get(5, TimeUnit.SECONDS);
            assertThat(whenSent.get(5, TimeUnit.SECONDS), sameInstance(serviceRequest));
        } finally {
            releaseWrite.countDown();
            client.closeResource();
        }
    }

    @Test
    void whenSentFailsWhenMaterializedDataWriteFails() {
        IllegalStateException expected = new IllegalStateException("simulated DATA write failure");
        AtomicBoolean entityReachedStream = new AtomicBoolean();
        Http2ClientStream stream = responseStream();
        doAnswer(invocation -> {
            BufferData copy = ((BufferData) invocation.getArgument(0)).copy();
            byte[] bytes = new byte[copy.available()];
            copy.read(bytes);
            entityReachedStream.set(new String(bytes, StandardCharsets.US_ASCII).equals("payload"));
            throw expected;
        }).when(stream).writeData(any(BufferData.class), eq(true));

        Http2ClientImpl client = (Http2ClientImpl) Http2Client.builder()
                .baseUri("https://localhost")
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .build();
        try {
            FailingEntityRequest request = new FailingEntityRequest(client, stream);

            IllegalStateException actual = assertThrows(
                    IllegalStateException.class,
                    () -> request.invokeEntity("payload".getBytes(StandardCharsets.US_ASCII)));

            assertThat(actual, sameInstance(expected));
            assertThat(entityReachedStream.get(), is(true));
            ExecutionException sentFailure = assertThrows(
                    ExecutionException.class,
                    () -> request.serviceRequest().whenSent().toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertThat(sentFailure.getCause(), sameInstance(expected));
        } finally {
            client.closeResource();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void stubWriter(MediaContext mediaContext, EntityWriter<?> writer) {
        when(mediaContext.writer(any(GenericType.class), any(ClientRequestHeaders.class)))
                .thenReturn((EntityWriter) writer);
    }

    private static Http2ClientStream responseStream() {
        Http2ClientStream stream = mock(Http2ClientStream.class, RETURNS_DEEP_STUBS);
        when(stream.waitFor100Continue(any())).thenReturn(null);
        when(stream.readHeaders()).thenReturn(Http2Headers.create(WritableHeaders.create()).status(Status.OK_200));
        when(stream.hasEntity()).thenReturn(false);
        return stream;
    }

    private static final class FailingEntityRequest extends Http2ClientRequestImpl {
        private final Http2ClientStream stream;
        private WebClientServiceRequest serviceRequest;

        private FailingEntityRequest(Http2ClientImpl client, Http2ClientStream stream) {
            super(client,
                  null,
                  Method.POST,
                  ClientUri.create(URI.create("https://localhost/entity")),
                  Map.of());
            this.stream = stream;
        }

        @Override
        protected WebClientServiceResponse invokeServices(WebClient webClient,
                                                          WebClientService.TransportChain httpCallChain,
                                                          CompletableFuture<WebClientServiceRequest> whenSent,
                                                          CompletableFuture<WebClientServiceResponse> whenComplete,
                                                          ClientUri usedUri,
                                                          Consumer<WebClientServiceRequest> requestPrepare) {
            ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
            serviceRequest = mock(WebClientServiceRequest.class);
            when(serviceRequest.method()).thenReturn(Method.POST);
            when(serviceRequest.uri()).thenReturn(usedUri);
            when(serviceRequest.headers()).thenReturn(headers);
            when(serviceRequest.whenSent()).thenReturn(whenSent);
            when(serviceRequest.whenComplete()).thenReturn(whenComplete);

            requestPrepare.accept(serviceRequest);
            Http2CallEntityChain callEntityChain = (Http2CallEntityChain) httpCallChain;
            return callEntityChain.doProceed(serviceRequest, headers, stream);
        }

        private WebClientServiceRequest serviceRequest() {
            return serviceRequest;
        }
    }

}
