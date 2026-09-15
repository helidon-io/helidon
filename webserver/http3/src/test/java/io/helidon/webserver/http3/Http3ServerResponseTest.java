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

package io.helidon.webserver.http3;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpException;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.gzip.GzipEncoding;
import io.helidon.http.media.MediaContext;
import io.helidon.quic.QuicConnection;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.TransportBindingContext;
import io.helidon.webserver.http.HttpSecurity;
import io.helidon.webserver.http.spi.SinkProvider;
import io.helidon.webserver.sse.SseSink;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http3ServerResponseTest {
    private static final List<Status> NO_ENTITY_STATUSES = List.of(Status.NO_CONTENT_204,
                                                                  Status.RESET_CONTENT_205,
                                                                  Status.NOT_MODIFIED_304);

    @Test
    void headRejectsEntityBeforeFiltersAndEncoders() {
        AtomicBoolean encoderCalled = new AtomicBoolean();
        AtomicBoolean filterCalled = new AtomicBoolean();
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(testEncoder(() -> encoderCalled.set(true)));
        Http3ServerStream stream = mock(Http3ServerStream.class);
        Http3ServerResponse response = response(stream, Method.HEAD, contentEncodingContext);
        response.streamFilter(outputStream -> {
            filterCalled.set(true);
            return outputStream;
        });

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                        () -> response.send("entity".getBytes(StandardCharsets.UTF_8)));

        assertAll(
                () -> assertThat(exception.getMessage(), containsString("HEAD request")),
                () -> assertThat(encoderCalled.get(), is(false)),
                () -> assertThat(filterCalled.get(), is(false)),
                () -> assertThat(response.isSent(), is(false))
        );
        verifyNoWrites(stream);
    }

    @Test
    void streamingHeadRejectsEntityBeforeFilterAndEncoderWrites() throws IOException {
        AtomicBoolean encoderWritten = new AtomicBoolean();
        AtomicBoolean filterWritten = new AtomicBoolean();
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class)))
                .thenReturn(testEncoder(() -> encoderWritten.set(true)));
        Http3ServerStream stream = mock(Http3ServerStream.class);
        Http3ServerResponse response = response(stream, Method.HEAD, contentEncodingContext);
        response.streamFilter(network -> new FilterOutputStream(network) {
            @Override
            public void write(int value) throws IOException {
                filterWritten.set(true);
                super.write(value);
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                filterWritten.set(true);
                super.write(bytes, offset, length);
            }
        });
        OutputStream outputStream = response.outputStream();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                        () -> outputStream.write(
                                                                "entity".getBytes(StandardCharsets.UTF_8)));

        assertAll(
                () -> assertThat(exception.getMessage(), containsString("HEAD request")),
                () -> assertThat(encoderWritten.get(), is(false)),
                () -> assertThat(filterWritten.get(), is(false)),
                () -> assertThat(response.isSent(), is(false))
        );
        verifyNoWrites(stream);
    }

    @Test
    void directNoEntityStatusesNormalizeFraming() {
        for (Status status : NO_ENTITY_STATUSES) {
            Http3ServerStream stream = mock(Http3ServerStream.class);
            Http3ServerResponse response = response(stream, Method.GET, ContentEncodingContext.create());
            response.status(status);
            response.contentLength(23);
            response.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            response.header(HeaderValues.create(HeaderNames.TRAILER, "test-trailer"));

            response.send("entity".getBytes(StandardCharsets.UTF_8));

            ArgumentCaptor<Headers> sentHeaders = ArgumentCaptor.forClass(Headers.class);
            verify(stream).writeResponseHeaders(eq(status.code()), sentHeaders.capture(), eq(true));
            verify(stream, never()).writeData(any(), anyInt(), anyInt(), anyBoolean());
            verify(stream, never()).writeTrailers(any(), anyBoolean());
            Headers headers = sentHeaders.getValue();
            assertAll(
                    () -> assertNoEntityContentLength(status, headers),
                    () -> assertThat(headers.contains(HeaderNames.TRANSFER_ENCODING), is(false)),
                    () -> assertThat(headers.contains(HeaderNames.TRAILER), is(false)),
                    () -> assertThat(response.isSent(), is(true))
            );
        }
    }

    @Test
    void noEntityStatusesRetainExplicitEncodingMetadata() {
        for (Status status : NO_ENTITY_STATUSES) {
            Http3ServerStream stream = mock(Http3ServerStream.class);
            Http3ServerResponse response = response(stream, Method.GET, ContentEncodingContext.create());
            response.status(status);
            response.contentEncoder(testEncoderWithContentLength());

            response.send();

            ArgumentCaptor<Headers> sentHeaders = ArgumentCaptor.forClass(Headers.class);
            verify(stream).writeResponseHeaders(eq(status.code()), sentHeaders.capture(), eq(true));
            Headers headers = sentHeaders.getValue();
            assertAll(
                    () -> assertThat(headers.get(HeaderNames.CONTENT_ENCODING).get(), is("test")),
                    () -> assertNoEntityContentLength(status, headers)
            );
        }
    }

    @Test
    void eagerNoEntityFlushWritesOneFinalHeaderSection() {
        for (Status status : NO_ENTITY_STATUSES) {
            AtomicInteger whenSentCalls = new AtomicInteger();
            Http3ServerStream stream = mock(Http3ServerStream.class);
            Http3ServerResponse response = response(stream, Method.GET, ContentEncodingContext.create());
            response.status(status);
            response.contentLength(23);
            response.header(HeaderValues.create(HeaderNames.TRAILER, "test-trailer"));
            response.whenSent(whenSentCalls::incrementAndGet);
            response.outputStream();

            response.flushHeaders();
            response.commit();
            response.commit();

            ArgumentCaptor<Headers> sentHeaders = ArgumentCaptor.forClass(Headers.class);
            verify(stream, times(1)).writeResponseHeaders(eq(status.code()), sentHeaders.capture(), eq(true));
            verify(stream, never()).writeData(any(), anyInt(), anyInt(), anyBoolean());
            verify(stream, never()).writeFin();
            verify(stream, never()).writeTrailers(any(), anyBoolean());
            assertAll(
                    () -> assertThat(response.isSent(), is(true)),
                    () -> assertThat(whenSentCalls.get(), is(1)),
                    () -> assertNoEntityContentLength(status, sentHeaders.getValue()),
                    () -> assertThat(sentHeaders.getValue().contains(HeaderNames.TRAILER), is(false))
            );
        }
    }

    @Test
    void informationalStatusIsNotSentAsFinalResponse() {
        Http3ServerStream stream = mock(Http3ServerStream.class);
        Http3ServerResponse response = response(stream, Method.GET, ContentEncodingContext.create());
        response.status(Status.CONTINUE_100);
        response.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        response.header(HeaderValues.create(HeaderNames.TRAILER, "test-trailer"));

        response.send("entity".getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<Headers> sentHeaders = ArgumentCaptor.forClass(Headers.class);
        verify(stream).writeResponseHeaders(eq(Status.INTERNAL_SERVER_ERROR_500.code()), sentHeaders.capture(), eq(true));
        verify(stream, never()).writeData(any(), anyInt(), anyInt(), anyBoolean());
        verify(stream, never()).writeTrailers(any(), anyBoolean());
        Headers headers = sentHeaders.getValue();
        assertAll(
                () -> assertThat(headers.contentLength().orElseThrow(), is(0L)),
                () -> assertThat(headers.contains(HeaderNames.TRANSFER_ENCODING), is(false)),
                () -> assertThat(headers.contains(HeaderNames.TRAILER), is(false))
        );
    }

    @Test
    void beforeSendNoEntityStatusBypassesEncodingAndFilters() {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(testEncoder());
        AtomicBoolean filterCalled = new AtomicBoolean();
        Http3ServerStream stream = mock(Http3ServerStream.class);
        Http3ServerResponse response = response(stream, Method.GET, contentEncodingContext);
        response.streamFilter(outputStream -> {
            filterCalled.set(true);
            return outputStream;
        });
        response.beforeSend(() -> response.status(Status.NO_CONTENT_204));

        response.send("entity".getBytes(StandardCharsets.UTF_8));
        response.commit();

        verify(contentEncodingContext, never()).encoder(any(Headers.class));
        verify(stream).writeResponseHeaders(eq(Status.NO_CONTENT_204.code()), any(), eq(true));
        verify(stream, never()).writeData(any(), anyInt(), anyInt(), anyBoolean());
        assertThat(filterCalled.get(), is(false));
    }

    @Test
    void filteredEmptySendSkipsAutomaticEncoding() {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(testEncoder());
        Http3ServerStream stream = mock(Http3ServerStream.class);
        Http3ServerResponse response = response(stream, Method.GET, contentEncodingContext);
        response.streamFilter(outputStream -> outputStream);

        response.send(BufferData.EMPTY_BYTES);
        response.commit();

        ArgumentCaptor<Headers> sentHeaders = ArgumentCaptor.forClass(Headers.class);
        verify(stream).writeResponseHeaders(eq(Status.OK_200.code()), sentHeaders.capture(), anyBoolean());
        verify(contentEncodingContext, never()).encoder(any(Headers.class));
        assertAll(
                () -> assertThat(sentHeaders.getValue().contains(HeaderNames.CONTENT_ENCODING), is(false)),
                () -> assertThat(sentHeaders.getValue().contains(HeaderNames.VARY), is(false))
        );
    }

    @Test
    void filteredEmptySendHonorsExplicitEncoding() {
        Http3ServerStream stream = mock(Http3ServerStream.class);
        Http3ServerResponse response = response(stream, Method.GET, ContentEncodingContext.create());
        response.streamFilter(outputStream -> outputStream);
        response.contentEncoder(testEncoder());

        response.send(BufferData.EMPTY_BYTES);
        response.commit();

        ArgumentCaptor<Headers> sentHeaders = ArgumentCaptor.forClass(Headers.class);
        verify(stream).writeResponseHeaders(eq(Status.OK_200.code()), sentHeaders.capture(), anyBoolean());
        assertAll(
                () -> assertThat(sentHeaders.getValue().get(HeaderNames.CONTENT_ENCODING).get(), is("test")),
                () -> assertThat(sentHeaders.getValue().contains(HeaderNames.VARY), is(false))
        );
    }

    @Test
    void resetEntityDiscardsOnlyEntityFiltersAndTrailers() {
        AtomicInteger responseFilterCalls = new AtomicInteger();
        AtomicInteger entityFilterCalls = new AtomicInteger();
        Http3ServerResponse response = response(mock(Http3ServerStream.class),
                                                Method.GET,
                                                ContentEncodingContext.create());
        response.streamFilter(outputStream -> {
            responseFilterCalls.incrementAndGet();
            return outputStream;
        });
        response.entityStreamFilter(outputStream -> {
            entityFilterCalls.incrementAndGet();
            return outputStream;
        });
        response.header(HeaderValues.create(HeaderNames.TRAILER, "test-trailer"));
        response.trailers().set(HeaderNames.create("test-trailer"), "old");

        assertThat(response.resetEntity(), is(true));
        response.send("new".getBytes(StandardCharsets.UTF_8));
        response.commit();

        assertAll(
                () -> assertThat(responseFilterCalls.get(), is(1)),
                () -> assertThat(entityFilterCalls.get(), is(0)),
                () -> assertThat(response.headers().contains(HeaderNames.TRAILER), is(true)),
                () -> assertThat(response.trailers().contains(HeaderNames.create("test-trailer")), is(false))
        );
    }

    @Test
    void resetClearsExplicitEncodingAndAllowsAutomaticReselection() {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(namedEncoder("automatic"));
        Http3ServerResponse response = response(mock(Http3ServerStream.class), Method.GET, contentEncodingContext);
        response.contentEncoder(namedEncoder("explicit"));
        response.outputStream();
        assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).get(), is("explicit"));

        assertThat(response.reset(), is(true));
        response.outputStream();

        assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).get(), is("automatic"));
        verify(contentEncodingContext, times(1)).encoder(any(Headers.class));
    }

    @Test
    void resetStreamRetainsExplicitEncodingButResetsNoOpAutomaticSelection() {
        ContentEncodingContext explicitContext = mock(ContentEncodingContext.class);
        Http3ServerResponse explicitResponse = response(mock(Http3ServerStream.class), Method.GET, explicitContext);
        explicitResponse.contentEncoder(namedEncoder("explicit"));
        explicitResponse.outputStream();

        assertThat(explicitResponse.resetStream(), is(true));
        explicitResponse.outputStream();

        assertThat(explicitResponse.headers().get(HeaderNames.CONTENT_ENCODING).get(), is("explicit"));
        verify(explicitContext, never()).encoder(any(Headers.class));

        ContentEncodingContext automaticContext = mock(ContentEncodingContext.class);
        when(automaticContext.contentEncodingEnabled()).thenReturn(true);
        when(automaticContext.encoder(any(Headers.class))).thenReturn(ContentEncoder.NO_OP);
        Http3ServerResponse automaticResponse = response(mock(Http3ServerStream.class), Method.GET, automaticContext);
        automaticResponse.outputStream();

        assertThat(automaticResponse.resetStream(), is(true));
        automaticResponse.outputStream();

        verify(automaticContext, times(2)).encoder(any(Headers.class));
    }

    @Test
    void streamingFailureIsLatchedAndNeverRetried() throws IOException {
        Http3ServerStream stream = mock(Http3ServerStream.class);
        IllegalStateException failure = new IllegalStateException("dispatch failed");
        doThrow(failure).when(stream).writeData(any(), anyInt(), anyInt(), eq(false));
        Http3ServerResponse response = response(stream, Method.GET, ContentEncodingContext.create(), 0, 8);
        OutputStream outputStream = response.outputStream();

        IllegalStateException firstFailure = assertThrows(IllegalStateException.class, () -> outputStream.write('a'));
        IllegalStateException repeatedFailure = assertThrows(IllegalStateException.class, () -> outputStream.write('b'));
        outputStream.close();
        IllegalStateException firstCommitFailure = assertThrows(IllegalStateException.class, response::commit);
        IllegalStateException repeatedCommitFailure = assertThrows(IllegalStateException.class, response::commit);

        assertAll(
                () -> assertThat(firstFailure, sameInstance(failure)),
                () -> assertThat(repeatedFailure, sameInstance(failure)),
                () -> assertThat(firstCommitFailure, sameInstance(failure)),
                () -> assertThat(repeatedCommitFailure, sameInstance(failure)),
                () -> assertThat(response.isSent(), is(true))
        );
        verify(stream, times(1)).writeData(any(), anyInt(), anyInt(), eq(false));
        verify(stream, never()).writeFin();
        verify(stream, never()).writeTrailers(any(), anyBoolean());
    }

    @Test
    void filterCloseFailureIsLatched() throws IOException {
        Http3ServerStream stream = mock(Http3ServerStream.class);
        IllegalStateException failure = new IllegalStateException("filter close failed");
        Http3ServerResponse response = response(stream, Method.GET, ContentEncodingContext.create());
        response.streamFilter(outputStream -> new FilterOutputStream(outputStream) {
            @Override
            public void close() {
                throw failure;
            }
        });
        OutputStream outputStream = response.outputStream();

        IllegalStateException closeFailure = assertThrows(IllegalStateException.class, outputStream::close);
        IllegalStateException commitFailure = assertThrows(IllegalStateException.class, response::commit);
        IllegalStateException repeatedCommitFailure = assertThrows(IllegalStateException.class, response::commit);

        assertAll(
                () -> assertThat(closeFailure, sameInstance(failure)),
                () -> assertThat(commitFailure, sameInstance(failure)),
                () -> assertThat(repeatedCommitFailure, sameInstance(failure))
        );
        verifyNoWrites(stream);
    }

    @Test
    void flushedFinalHeadersMarkResponseSentAndCompleteOnce() {
        AtomicInteger whenSentCalls = new AtomicInteger();
        Http3ServerStream stream = mock(Http3ServerStream.class);
        Http3ServerResponse response = response(stream, Method.GET, ContentEncodingContext.create());
        response.whenSent(whenSentCalls::incrementAndGet);
        response.outputStream();

        response.flushHeaders();

        assertAll(
                () -> assertThat(response.isSent(), is(true)),
                () -> assertThat(whenSentCalls.get(), is(0))
        );
        response.commit();
        response.commit();
        assertThat(whenSentCalls.get(), is(1));
        verify(stream, times(1)).writeResponseHeaders(eq(Status.OK_200.code()), any(), eq(false));
        verify(stream, times(1)).writeTrailers(any(), eq(true));
        verify(stream, never()).writeFin();
    }

    @Test
    void rejectsStatusChangeAfterOutputStreamIsRequested() {
        Http3ServerResponse response = response();
        response.outputStream();

        assertThrows(IllegalStateException.class, () -> response.status(Status.CREATED_201));
    }
    @Test
    void rejectsNullStreamConfiguration() {
        Http3ServerResponse response = response();

        assertThrows(NullPointerException.class, () -> response.streamResult(null));
        assertThrows(NullPointerException.class, () -> response.streamFilter(null));
    }

    @Test
    void rejectsNullOutputStreamFilterResult() {
        Http3ServerResponse response = response();
        response.streamFilter(outputStream -> null);

        assertThrows(NullPointerException.class, response::outputStream);
    }

    @Test
    void rejectsNullFilterResultBeforeCallingNextFilter() {
        Http3ServerResponse response = response();
        AtomicBoolean nextFilterCalled = new AtomicBoolean();
        response.streamFilter(outputStream -> null);
        response.streamFilter(outputStream -> {
            nextFilterCalled.set(true);
            return outputStream;
        });

        assertThrows(NullPointerException.class, response::outputStream);
        assertThat(nextFilterCalled.get(), is(false));
    }

    @Test
    void coalescesSmallWritesUntilFlush() throws IOException {
        Http3ServerStream stream = mock(Http3ServerStream.class);
        Http3ServerResponse response = response(stream, 4, 8);
        OutputStream outputStream = response.outputStream();

        outputStream.write('a');
        outputStream.write('b');
        outputStream.write('c');

        verify(stream, never()).writeData(any(), anyInt(), anyInt(), anyBoolean());

        outputStream.flush();

        verify(stream).writeData(any(), eq(0), eq(3), eq(false));
        verify(stream).flushResponseData();
    }

    @Test
    void fullCoalescingBufferDispatchesWithoutFlush() throws IOException {
        Http3ServerStream stream = mock(Http3ServerStream.class);
        Http3ServerResponse response = response(stream, 4, 8);
        OutputStream outputStream = response.outputStream();

        outputStream.write('a');
        outputStream.write('b');
        outputStream.write('c');
        outputStream.write('d');

        verify(stream).writeData(any(), eq(0), eq(4), eq(false));
    }

    @Test
    void capsCoalescingBufferAtDispatchWindow() throws IOException {
        Http3ServerStream stream = mock(Http3ServerStream.class);
        Http3ServerResponse response = response(stream, 16, 4);
        OutputStream outputStream = response.outputStream();

        outputStream.write('a');
        outputStream.write('b');
        outputStream.write('c');
        outputStream.write('d');

        verify(stream).writeData(any(), eq(0), eq(4), eq(false));
    }

    @Test
    void disabledCoalescingStillFlushesTransportWindow() throws IOException {
        Http3ServerStream stream = mock(Http3ServerStream.class);
        Http3ServerResponse response = response(stream, 0, 8);
        OutputStream outputStream = response.outputStream();
        byte[] entity = {'a', 'b', 'c'};

        outputStream.write(entity);
        outputStream.flush();

        verify(stream).writeData(entity, 0, entity.length, false);
        verify(stream).flushResponseData();
    }

    @Test
    void singleByteWriteExposesCheckedDispatchFailure() {
        Http3ServerStream stream = mock(Http3ServerStream.class);
        InterruptedIOException cause = new InterruptedIOException("interrupted");
        doThrow(new UncheckedIOException(cause)).when(stream).writeData(any(), anyInt(), anyInt(), eq(false));
        OutputStream outputStream = response(stream, 0, 8).outputStream();

        IOException thrown = assertThrows(InterruptedIOException.class, () -> outputStream.write('a'));

        assertThat(thrown, sameInstance(cause));
    }

    @Test
    void byteArrayWriteExposesCheckedDispatchFailure() {
        Http3ServerStream stream = mock(Http3ServerStream.class);
        InterruptedIOException cause = new InterruptedIOException("interrupted");
        doThrow(new UncheckedIOException(cause)).when(stream).writeData(any(), anyInt(), anyInt(), eq(false));
        OutputStream outputStream = response(stream, 0, 8).outputStream();

        IOException thrown = assertThrows(InterruptedIOException.class,
                                          () -> outputStream.write(new byte[] {'a'}));

        assertThat(thrown, sameInstance(cause));
    }

    @Test
    void flushExposesCheckedDispatchFailure() throws IOException {
        Http3ServerStream stream = mock(Http3ServerStream.class);
        InterruptedIOException cause = new InterruptedIOException("interrupted");
        OutputStream outputStream = response(stream, 4, 8).outputStream();
        outputStream.write('a');
        doThrow(new UncheckedIOException(cause)).when(stream).flushResponseData();

        IOException thrown = assertThrows(InterruptedIOException.class, outputStream::flush);

        assertThat(thrown, sameInstance(cause));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void usesOnlyInjectedSinkProviders() {
        SseSink injectedSink = mock(SseSink.class);
        SinkProvider injectedProvider = mock(SinkProvider.class);
        when(injectedProvider.supports(eq(SseSink.TYPE), any())).thenReturn(true);
        when(injectedProvider.create(any())).thenReturn(injectedSink);

        Http3ServerResponse response = response(mock(Http3ServerStream.class),
                                                1024,
                                                65_536,
                                                List.of(injectedProvider));

        assertThat(response.sink(SseSink.TYPE), sameInstance(injectedSink));
        verify(injectedProvider).supports(eq(SseSink.TYPE), any());
        verify(injectedProvider).create(any());

        Http3ServerResponse responseWithoutProviders = response(mock(Http3ServerStream.class),
                                                                1024,
                                                                65_536,
                                                                List.of());
        assertThrows(HttpException.class, () -> responseWithoutProviders.sink(SseSink.TYPE));
    }

    private static Http3ServerResponse response() {
        return response(mock(Http3ServerStream.class), 1024, 65_536);
    }

    private static Http3ServerResponse response(Http3ServerStream stream,
                                                int writeBufferSize,
                                                int responseDispatchWindowSize) {
        return response(stream, writeBufferSize, responseDispatchWindowSize, List.of());
    }

    private static Http3ServerResponse response(Http3ServerStream stream,
                                                Method method,
                                                ContentEncodingContext contentEncodingContext) {
        return response(stream, method, contentEncodingContext, 1024, 65_536);
    }

    private static Http3ServerResponse response(Http3ServerStream stream,
                                                Method method,
                                                ContentEncodingContext contentEncodingContext,
                                                int writeBufferSize,
                                                int responseDispatchWindowSize) {
        return response(stream,
                        method,
                        contentEncodingContext,
                        writeBufferSize,
                        responseDispatchWindowSize,
                        List.of());
    }

    @SuppressWarnings("rawtypes")
    private static Http3ServerResponse response(Http3ServerStream stream,
                                                int writeBufferSize,
                                                int responseDispatchWindowSize,
                                                List<SinkProvider> sinkProviders) {
        return response(stream,
                        Method.GET,
                        ContentEncodingContext.create(),
                        writeBufferSize,
                        responseDispatchWindowSize,
                        sinkProviders);
    }

    @SuppressWarnings("rawtypes")
    private static Http3ServerResponse response(Http3ServerStream stream,
                                                Method method,
                                                ContentEncodingContext contentEncodingContext,
                                                int writeBufferSize,
                                                int responseDispatchWindowSize,
                                                List<SinkProvider> sinkProviders) {
        TransportBindingContext bindingContext = mock(TransportBindingContext.class);
        ListenerContext listenerContext = mock(ListenerContext.class);
        ListenerConfig listenerConfig = mock(ListenerConfig.class);
        when(bindingContext.listenerContext()).thenReturn(listenerContext);
        when(listenerContext.contentEncodingContext()).thenReturn(contentEncodingContext);
        when(listenerContext.mediaContext()).thenReturn(mock(MediaContext.class));
        when(listenerContext.config()).thenReturn(listenerConfig);
        when(listenerConfig.maxInMemoryEntity()).thenReturn(1024);
        when(listenerConfig.writeBufferSize()).thenReturn(writeBufferSize);

        PeerInfo remotePeer = mock(PeerInfo.class);
        QuicConnection connection = mock(QuicConnection.class);
        when(connection.remotePeer()).thenReturn(remotePeer);
        when(remotePeer.tlsCertificates()).thenReturn(Optional.empty());
        Http3ConnectionContext context = new Http3ConnectionContext(bindingContext, connection);
        Http3ServerRequest request = request(context, method);
        return new Http3ServerResponse(context, request, stream, responseDispatchWindowSize, sinkProviders);
    }

    private static Http3ServerRequest request(Http3ConnectionContext context, Method method) {
        HttpPrologue prologue = HttpPrologue.create("HTTP/3",
                                                    "HTTP",
                                                    "3",
                                                    method,
                                                    "/before/routing",
                                                    true);
        Http3ServerRequest.RequestMeta metadata = new Http3ServerRequest.RequestMeta(
                1,
                false,
                _ -> BufferData.empty(),
                () -> {
                },
                () -> {
                },
                null,
                new Http3ServerRequest.EntityLimits(1024, 1024));
        return Http3ServerRequest.create(context,
                                         mock(HttpSecurity.class),
                                         prologue,
                                         ServerRequestHeaders.create(),
                                         "example.com",
                                         ContentDecoder.NO_OP,
                                         metadata);
    }

    private static void assertNoEntityContentLength(Status status, Headers headers) {
        if (status.code() == Status.NO_CONTENT_204.code()) {
            assertThat(headers.contains(HeaderNames.CONTENT_LENGTH), is(false));
        } else {
            assertThat(headers.contentLength().orElseThrow(),
                       is(status.code() == Status.RESET_CONTENT_205.code() ? 0L : 23L));
        }
    }

    private static void verifyNoWrites(Http3ServerStream stream) {
        verify(stream, never()).writeResponseHeaders(anyInt(), any(), anyBoolean());
        verify(stream, never()).writeData(any(), anyInt(), anyInt(), anyBoolean());
        verify(stream, never()).writeTrailers(any(), anyBoolean());
        verify(stream, never()).writeFin();
    }

    private static ContentEncoder testEncoder() {
        return testEncoder(() -> {
        });
    }

    private static ContentEncoder testEncoderWithContentLength() {
        return new ContentEncoder() {
            @Override
            public OutputStream apply(OutputStream network) {
                return network;
            }

            @Override
            public void headers(WritableHeaders<?> headers) {
                headers.set(HeaderNames.CONTENT_ENCODING, "test");
                headers.set(HeaderNames.CONTENT_LENGTH, "23");
            }
        };
    }

    private static ContentEncoder namedEncoder(String name) {
        return new ContentEncoder() {
            @Override
            public OutputStream apply(OutputStream network) {
                return network;
            }

            @Override
            public void headers(WritableHeaders<?> headers) {
                headers.set(HeaderNames.CONTENT_ENCODING, name);
            }
        };
    }

    private static ContentEncoder testEncoder(Runnable onWrite) {
        return new ContentEncoder() {
            @Override
            public OutputStream apply(OutputStream network) {
                return new OutputStream() {
                    @Override
                    public void write(int value) throws IOException {
                        onWrite.run();
                        network.write('x');
                        network.write(value);
                    }

                    @Override
                    public void write(byte[] bytes, int offset, int length) throws IOException {
                        onWrite.run();
                        network.write('x');
                        network.write(bytes, offset, length);
                    }

                    @Override
                    public void flush() throws IOException {
                        network.flush();
                    }

                    @Override
                    public void close() throws IOException {
                        network.close();
                    }
                };
            }

            @Override
            public void headers(WritableHeaders<?> headers) {
                headers.set(HeaderNames.CONTENT_ENCODING, "test");
                headers.remove(HeaderNames.CONTENT_LENGTH);
            }
        };
    }
}
