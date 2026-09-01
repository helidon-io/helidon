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

package io.helidon.webserver.http2;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.DirectHandler;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RequestException;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.gzip.GzipEncoding;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Exception;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.DirectHandlers;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http2ServerResponseTest {
    private static final List<Status> NO_ENTITY_STATUSES = List.of(Status.NO_CONTENT_204,
                                                                  Status.RESET_CONTENT_205,
                                                                  Status.NOT_MODIFIED_304);

    @Test
    void headRejectsEntityBeforeSendingResponse() {
        byte[] entity = "entity".getBytes(StandardCharsets.UTF_8);
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(testEncoder());

        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.HEAD, contentEncodingContext);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> response.send(entity));

        assertAll(
                () -> assertThat(exception.getMessage(), containsString("HEAD request")),
                () -> assertThat(response.isSent(), is(false))
        );
        verifyNoWrites(stream);
    }

    @Test
    void directHandlerReplacesContentLengthWithEncodedLength() {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(testEncoder());
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.GET, contentEncodingContext);
        response.contentLength(17);
        DirectHandlers directHandlers = DirectHandlers.builder()
                .addHandler(DirectHandler.EventType.BAD_REQUEST,
                            (_, _, _, _, _) -> DirectHandler.TransportResponse.builder()
                                    .entity("error")
                                    .header(HeaderNames.CONTENT_LENGTH, "23")
                                    .build())
                .build();
        RequestException requestException = RequestException.builder()
                .type(DirectHandler.EventType.BAD_REQUEST)
                .message("bad request")
                .build();

        directHandlers.handle(requestException, response, true);

        var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
        var responseEntity = ArgumentCaptor.forClass(BufferData.class);
        verify(stream).writeHeadersWithData(responseHeaders.capture(), eq(6), responseEntity.capture(), eq(true));
        assertAll(
                () -> assertThat(responseHeaders.getValue().httpHeaders().contentLength().orElseThrow(), is(6L)),
                () -> assertThat(new String(responseEntity.getValue().readBytes(), StandardCharsets.UTF_8), is("xerror"))
        );
    }

    @Test
    void failedStreamingWriteDiscardsEncoderCloseBytes() throws IOException {
        AtomicInteger dataWrites = new AtomicInteger();
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(testEncoder(() -> { }, true));
        Http2ServerStream stream = mock(Http2ServerStream.class);
        when(stream.writeData(any(BufferData.class), eq(false))).thenAnswer(_ -> {
            dataWrites.incrementAndGet();
            throw new Http2Exception(Http2ErrorCode.FLOW_CONTROL, "Flow control update wait time-out.");
        });
        Http2ServerResponse response = createResponse(stream, Method.GET, contentEncodingContext);
        OutputStream outputStream = response.outputStream();
        response.flushHeaders();

        Http2Exception failure = assertThrows(Http2Exception.class,
                                              () -> outputStream.write("hello".getBytes(StandardCharsets.UTF_8)));
        Http2Exception repeatedFailure = assertThrows(Http2Exception.class, () -> outputStream.write('y'));
        outputStream.close();
        Http2Exception commitFailure = assertThrows(Http2Exception.class, response::commit);

        assertAll(
                () -> assertThat(failure.code(), is(Http2ErrorCode.FLOW_CONTROL)),
                () -> assertThat(repeatedFailure, sameInstance(failure)),
                () -> assertThat(commitFailure, sameInstance(failure)),
                () -> assertThat(dataWrites.get(), is(1))
        );
        verify(stream, never()).writeData(any(BufferData.class), eq(true));
    }

    @Test
    void failedFilterWriteDiscardsEncoderCloseBytes() throws IOException {
        AtomicInteger dataWrites = new AtomicInteger();
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(testEncoder(() -> { }, true));
        Http2ServerStream stream = mock(Http2ServerStream.class);
        when(stream.writeData(any(BufferData.class), eq(false))).thenAnswer(_ -> dataWrites.incrementAndGet());
        Http2ServerResponse response = createResponse(stream, Method.GET, contentEncodingContext);
        Http2Exception filterFailure = new Http2Exception(Http2ErrorCode.FLOW_CONTROL, "Filter write failed.");
        response.streamFilter(delegate -> new FilterOutputStream(delegate) {
            @Override
            public void write(int value) {
                throw filterFailure;
            }

            @Override
            public void write(byte[] bytes, int offset, int length) {
                throw filterFailure;
            }
        });
        OutputStream outputStream = response.outputStream();
        response.flushHeaders();

        Http2Exception failure = assertThrows(Http2Exception.class,
                                              () -> outputStream.write("hello".getBytes(StandardCharsets.UTF_8)));
        outputStream.close();
        Http2Exception commitFailure = assertThrows(Http2Exception.class, response::commit);

        assertAll(
                () -> assertThat(failure, sameInstance(filterFailure)),
                () -> assertThat(commitFailure, sameInstance(filterFailure)),
                () -> assertThat(dataWrites.get(), is(0))
        );
        verify(stream, never()).writeData(any(BufferData.class), anyBoolean());
    }

    @Test
    void failedFilterCloseDoesNotCompleteResponse() throws IOException {
        ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.GET, contentEncodingContext);
        Http2Exception filterFailure = new Http2Exception(Http2ErrorCode.FLOW_CONTROL, "Filter close failed.");
        response.streamFilter(delegate -> new FilterOutputStream(delegate) {
            @Override
            public void close() {
                throw filterFailure;
            }
        });
        OutputStream outputStream = response.outputStream();
        response.flushHeaders();

        Http2Exception failure = assertThrows(Http2Exception.class, outputStream::close);
        Http2Exception commitFailure = assertThrows(Http2Exception.class, response::commit);

        assertAll(
                () -> assertThat(failure, sameInstance(filterFailure)),
                () -> assertThat(commitFailure, sameInstance(filterFailure))
        );
        verify(stream, never()).writeData(any(BufferData.class), anyBoolean());
        verify(stream, never()).writeTrailers(any());
    }

    @Test
    void failedEagerGzipFlushDoesNotCompleteResponse() {
        ContentEncodingContext contentEncodingContext = ContentEncodingContext.builder()
                .addContentEncoding(GzipEncoding.create())
                .build();
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ACCEPT_ENCODING, "gzip");
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2Exception flowControlFailure =
                new Http2Exception(Http2ErrorCode.FLOW_CONTROL, "Flow control update wait time-out.");
        when(stream.writeData(any(BufferData.class), eq(false))).thenThrow(flowControlFailure);
        Http2ServerResponse response = createResponse(stream,
                                                      Method.GET,
                                                      contentEncodingContext,
                                                      ServerRequestHeaders.create(requestHeaders));
        response.outputStream();

        Http2Exception failure = assertThrows(Http2Exception.class, response::flushHeaders);
        Http2Exception commitFailure = assertThrows(Http2Exception.class, response::commit);

        assertAll(
                () -> assertThat(failure, sameInstance(flowControlFailure)),
                () -> assertThat(commitFailure, sameInstance(flowControlFailure))
        );
        verify(stream).writeData(any(BufferData.class), eq(false));
        verify(stream, never()).writeData(any(BufferData.class), eq(true));
        verify(stream, never()).writeTrailers(any());
    }

    @Test
    void directHandlerHeadPreservesContentLengthWithoutEntity() {
        ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.HEAD, contentEncodingContext);
        DirectHandler.TransportRequest request = mock(DirectHandler.TransportRequest.class);
        when(request.method()).thenReturn(Method.HEAD_NAME);
        DirectHandlers directHandlers = DirectHandlers.builder()
                .addHandler(DirectHandler.EventType.BAD_REQUEST,
                            (_, _, _, _, _) -> DirectHandler.TransportResponse.builder()
                                    .status(Status.BAD_REQUEST_400)
                                    .header(HeaderNames.CONTENT_LENGTH, "23")
                                    .build())
                .build();
        RequestException requestException = RequestException.builder()
                .request(request)
                .type(DirectHandler.EventType.BAD_REQUEST)
                .message("bad request")
                .build();

        directHandlers.handle(requestException, response, true);

        var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
        verify(stream).writeHeaders(responseHeaders.capture(), eq(true));
        verify(stream, never()).writeHeadersWithData(any(), anyInt(), any(), anyBoolean());
        verify(stream, never()).writeData(any(), anyBoolean());
        assertAll(
                () -> assertThat(responseHeaders.getValue().status(), is(Status.BAD_REQUEST_400)),
                () -> assertThat(responseHeaders.getValue().httpHeaders().contentLength().orElseThrow(), is(23L))
        );
    }

    @Test
    void directHandlerHeadUsesEncodedRepresentationMetadata() {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(testEncoder());
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.HEAD, contentEncodingContext);
        DirectHandler.TransportRequest request = mock(DirectHandler.TransportRequest.class);
        when(request.method()).thenReturn(Method.HEAD_NAME);
        DirectHandlers directHandlers = DirectHandlers.builder()
                .addHandler(DirectHandler.EventType.BAD_REQUEST,
                            (_, _, _, _, _) -> DirectHandler.TransportResponse.builder()
                                    .status(Status.BAD_REQUEST_400)
                                    .entity("error")
                                    .build())
                .build();
        RequestException requestException = RequestException.builder()
                .request(request)
                .type(DirectHandler.EventType.BAD_REQUEST)
                .message("bad request")
                .build();

        directHandlers.handle(requestException, response, true);

        var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
        verify(stream).writeHeaders(responseHeaders.capture(), eq(true));
        verify(stream, never()).writeHeadersWithData(any(), anyInt(), any(), anyBoolean());
        verify(stream, never()).writeData(any(), anyBoolean());
        Headers sentHeaders = responseHeaders.getValue().httpHeaders();
        assertAll(
                () -> assertThat(responseHeaders.getValue().status(), is(Status.BAD_REQUEST_400)),
                () -> assertThat(sentHeaders.contentLength().orElseThrow(), is(6L)),
                () -> assertThat(sentHeaders.get(HeaderNames.CONTENT_ENCODING).get(), is("test")),
                () -> assertThat(sentHeaders.containsToken(HeaderValues.create(HeaderNames.VARY,
                                                                               HeaderNames.ACCEPT_ENCODING_NAME)),
                                 is(true))
        );
    }

    @Test
    void directHandlerFilteredHeadUsesOnlyConfiguredRepresentationLength() {
        for (long configuredLength : List.of(-1L, 10L)) {
            ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();
            Http2ServerStream stream = mock(Http2ServerStream.class);
            Http2ServerResponse response = createResponse(stream, Method.HEAD, contentEncodingContext);
            response.streamFilter(output -> new FilterOutputStream(output) {
                @Override
                public void write(byte[] bytes, int offset, int length) throws IOException {
                    out.write(bytes, offset, length);
                    out.write(bytes, offset, length);
                }
            });
            if (configuredLength >= 0) {
                response.beforeSend(() -> response.contentLength(configuredLength));
            }
            DirectHandler.TransportRequest request = mock(DirectHandler.TransportRequest.class);
            when(request.method()).thenReturn(Method.HEAD_NAME);
            DirectHandlers directHandlers = DirectHandlers.builder()
                    .addHandler(DirectHandler.EventType.BAD_REQUEST,
                                (_, _, _, _, _) -> DirectHandler.TransportResponse.builder()
                                        .status(Status.BAD_REQUEST_400)
                                        .entity("error")
                                        .build())
                    .build();
            RequestException requestException = RequestException.builder()
                    .request(request)
                    .type(DirectHandler.EventType.BAD_REQUEST)
                    .message("bad request")
                    .build();

            directHandlers.handle(requestException, response, true);

            var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
            verify(stream).writeHeaders(responseHeaders.capture(), eq(true));
            verify(stream, never()).writeHeadersWithData(any(), anyInt(), any(), anyBoolean());
            verify(stream, never()).writeData(any(), anyBoolean());
            Headers sentHeaders = responseHeaders.getValue().httpHeaders();
            if (configuredLength < 0) {
                assertThat(sentHeaders.contains(HeaderNames.CONTENT_LENGTH), is(false));
            } else {
                assertThat(sentHeaders.contentLength().orElseThrow(), is(configuredLength));
            }
        }
    }

    @Test
    void directHandlerHeadSkipsRepresentationMetadataForLateInformationalStatus() {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(testEncoder());
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.HEAD, contentEncodingContext);
        response.beforeSend(() -> response.status(Status.CONTINUE_100));
        DirectHandler.TransportRequest request = mock(DirectHandler.TransportRequest.class);
        when(request.method()).thenReturn(Method.HEAD_NAME);
        DirectHandlers directHandlers = DirectHandlers.builder()
                .addHandler(DirectHandler.EventType.BAD_REQUEST,
                            (_, _, _, _, _) -> DirectHandler.TransportResponse.builder()
                                    .status(Status.BAD_REQUEST_400)
                                    .entity("error")
                                    .build())
                .build();
        RequestException requestException = RequestException.builder()
                .request(request)
                .type(DirectHandler.EventType.BAD_REQUEST)
                .message("bad request")
                .build();

        directHandlers.handle(requestException, response, true);

        var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
        verify(stream).writeHeaders(responseHeaders.capture(), eq(true));
        verify(stream, never()).writeHeadersWithData(any(), anyInt(), any(), anyBoolean());
        verify(stream, never()).writeData(any(), anyBoolean());
        Headers sentHeaders = responseHeaders.getValue().httpHeaders();
        assertAll(
                () -> assertThat(responseHeaders.getValue().status(), is(Status.INTERNAL_SERVER_ERROR_500)),
                () -> assertThat(sentHeaders.contentLength().orElseThrow(), is(0L)),
                () -> assertThat(sentHeaders.contains(HeaderNames.CONTENT_ENCODING), is(false)),
                () -> assertThat(sentHeaders.contains(HeaderNames.VARY), is(false))
        );
    }

    @Test
    void directHandlerNoContentRemovesContentLength() {
        ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.GET, contentEncodingContext);
        response.contentLength(17);
        DirectHandlers directHandlers = DirectHandlers.builder()
                .addHandler(DirectHandler.EventType.BAD_REQUEST,
                            (_, _, _, _, _) -> DirectHandler.TransportResponse.builder()
                                    .status(Status.NO_CONTENT_204)
                                    .entity("ignored")
                                    .header(HeaderNames.CONTENT_LENGTH, "23")
                                    .header(HeaderValues.TRANSFER_ENCODING_CHUNKED)
                                    .header(HeaderNames.TRAILER, "test-trailer")
                                    .build())
                .build();
        RequestException requestException = RequestException.builder()
                .type(DirectHandler.EventType.BAD_REQUEST)
                .message("bad request")
                .build();

        directHandlers.handle(requestException, response, true);

        var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
        verify(stream).writeHeaders(responseHeaders.capture(), eq(true));
        verify(stream, never()).writeHeadersWithData(any(), anyInt(), any(), anyBoolean());
        verify(stream, never()).writeData(any(), anyBoolean());
        assertAll(
                () -> assertThat(responseHeaders.getValue().status(), is(Status.NO_CONTENT_204)),
                () -> assertThat(responseHeaders.getValue().httpHeaders().contains(HeaderNames.CONTENT_LENGTH), is(false)),
                () -> assertThat(responseHeaders.getValue().httpHeaders().contains(HeaderNames.TRANSFER_ENCODING), is(false)),
                () -> assertThat(responseHeaders.getValue().httpHeaders().contains(HeaderNames.TRAILER), is(false))
        );
    }

    @Test
    void presetNoEntityStatusesBypassNegotiatedGzip() {
        for (Status status : NO_ENTITY_STATUSES) {
            ContentEncodingContext contentEncodingContext = ContentEncodingContext.builder()
                    .addContentEncoding(GzipEncoding.create())
                    .build();
            WritableHeaders<?> requestHeaders = WritableHeaders.create();
            requestHeaders.set(HeaderNames.ACCEPT_ENCODING, "gzip");
            Http2ServerStream stream = mock(Http2ServerStream.class);
            Http2ServerResponse response = createResponse(stream,
                                                          Method.GET,
                                                          contentEncodingContext,
                                                          ServerRequestHeaders.create(requestHeaders));
            response.status(status);
            response.contentLength(23);
            response.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            response.header(HeaderValues.create(HeaderNames.TRAILER, "test-trailer"));

            response.send("entity".getBytes(StandardCharsets.UTF_8));

            var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
            verify(stream).writeHeaders(responseHeaders.capture(), eq(true));
            verify(stream, never()).writeHeadersWithData(any(), anyInt(), any(), anyBoolean());
            verify(stream, never()).writeData(any(), anyBoolean());
            verify(stream, never()).writeTrailers(any());
            Http2Headers sentHeaders = responseHeaders.getValue();
            Headers sentHttpHeaders = sentHeaders.httpHeaders();
            assertAll(
                    () -> assertThat(sentHeaders.status(), is(status)),
                    () -> assertNoEntityContentLength(status, sentHttpHeaders),
                    () -> assertThat(sentHttpHeaders.contains(HeaderNames.CONTENT_ENCODING), is(false)),
                    () -> assertThat(sentHttpHeaders.contains(HeaderNames.VARY),
                                     is(status.code() == Status.NOT_MODIFIED_304.code())),
                    () -> assertThat(sentHttpHeaders.contains(HeaderNames.TRANSFER_ENCODING), is(false)),
                    () -> assertThat(sentHttpHeaders.contains(HeaderNames.TRAILER), is(false))
            );
        }
    }

    @Test
    void noEntityStatusesDoNotApplyExplicitGzip() {
        for (Status status : NO_ENTITY_STATUSES) {
            Http2ServerStream stream = mock(Http2ServerStream.class);
            Http2ServerResponse response = createResponse(stream, Method.GET, ContentEncodingContext.create());
            response.status(status);
            response.contentEncoder(GzipEncoding.create().encoder());

            response.outputStream();
            response.commit();

            var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
            verify(stream).writeHeaders(responseHeaders.capture(), eq(true));
            verify(stream, never()).writeHeadersWithData(any(), anyInt(), any(), anyBoolean());
            verify(stream, never()).writeData(any(), anyBoolean());
            verify(stream, never()).writeTrailers(any());
            assertAll(
                    () -> assertThat(responseHeaders.getValue().status(), is(status)),
                    () -> assertThat(responseHeaders.getValue().httpHeaders()
                                             .get(HeaderNames.CONTENT_ENCODING).get(), is("gzip"))
            );
        }
    }

    @Test
    void eagerlyFlushedNoEntityStatusIsSentOnce() {
        for (Status status : NO_ENTITY_STATUSES) {
            ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
            when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);
            Http2ServerStream stream = mock(Http2ServerStream.class);
            Http2ServerResponse response = createResponse(stream, Method.GET, contentEncodingContext);
            response.status(status);
            response.contentLength(23);
            response.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            response.header(HeaderValues.create(HeaderNames.TRAILER, "test-trailer"));

            response.outputStream();
            response.flushHeaders();
            response.commit();

            var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
            verify(stream).writeHeaders(responseHeaders.capture(), eq(true));
            verify(stream, never()).writeHeadersWithData(any(), anyInt(), any(), anyBoolean());
            verify(stream, never()).writeData(any(), anyBoolean());
            verify(stream, never()).writeTrailers(any());
            assertAll(
                    () -> assertThat(responseHeaders.getValue().status(), is(status)),
                    () -> assertNoEntityContentLength(status, responseHeaders.getValue().httpHeaders())
            );
        }
    }

    @Test
    void beforeSendNoEntityStatusesBypassNegotiatedGzip() {
        for (Status status : NO_ENTITY_STATUSES) {
            ContentEncodingContext contentEncodingContext = ContentEncodingContext.builder()
                    .addContentEncoding(GzipEncoding.create())
                    .build();
            WritableHeaders<?> requestHeaders = WritableHeaders.create();
            requestHeaders.set(HeaderNames.ACCEPT_ENCODING, "gzip");
            Http2ServerStream stream = mock(Http2ServerStream.class);
            Http2ServerResponse response = createResponse(stream,
                                                          Method.GET,
                                                          contentEncodingContext,
                                                          ServerRequestHeaders.create(requestHeaders));
            response.contentLength(23);
            response.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            response.header(HeaderValues.create(HeaderNames.TRAILER, "test-trailer"));
            response.beforeSend(() -> response.status(status));

            response.send("entity".getBytes(StandardCharsets.UTF_8));

            var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
            verify(stream).writeHeaders(responseHeaders.capture(), eq(true));
            verify(stream, never()).writeHeadersWithData(any(), anyInt(), any(), anyBoolean());
            verify(stream, never()).writeData(any(), anyBoolean());
            verify(stream, never()).writeTrailers(any());
            Http2Headers sentHeaders = responseHeaders.getValue();
            Headers sentHttpHeaders = sentHeaders.httpHeaders();
            assertAll(
                    () -> assertThat(sentHeaders.status(), is(status)),
                    () -> assertNoEntityContentLength(status, sentHttpHeaders),
                    () -> assertThat(sentHttpHeaders.contains(HeaderNames.CONTENT_ENCODING), is(false)),
                    () -> assertThat(sentHttpHeaders.contains(HeaderNames.VARY),
                                     is(status.code() == Status.NOT_MODIFIED_304.code())),
                    () -> assertThat(sentHttpHeaders.contains(HeaderNames.TRANSFER_ENCODING), is(false)),
                    () -> assertThat(sentHttpHeaders.contains(HeaderNames.TRAILER), is(false))
            );
        }
    }

    @Test
    void presetInformationalStatusIsNotSentAsFinalResponse() {
        ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.GET, contentEncodingContext);
        response.status(Status.CONTINUE_100);
        response.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        response.header(HeaderValues.create(HeaderNames.TRAILER, "test-trailer"));

        response.send("entity".getBytes(StandardCharsets.UTF_8));

        assertRejectedInformationalResponse(stream);
    }

    @Test
    void filteredInformationalStatusIsNotSentAsFinalResponse() {
        ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.GET, contentEncodingContext);
        AtomicBoolean filterCalled = new AtomicBoolean();
        response.status(Status.CONTINUE_100);
        response.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        response.header(HeaderValues.create(HeaderNames.TRAILER, "test-trailer"));
        response.streamFilter(output -> {
            filterCalled.set(true);
            return output;
        });

        response.send("entity".getBytes(StandardCharsets.UTF_8));
        response.commit();

        assertAll(
                () -> assertThat(filterCalled.get(), is(false)),
                () -> assertRejectedInformationalResponse(stream)
        );
    }

    @Test
    void streamingInformationalStatusIsNotSentAsFinalResponse() throws IOException {
        ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.GET, contentEncodingContext);
        response.status(Status.CONTINUE_100);
        response.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        response.header(HeaderValues.create(HeaderNames.TRAILER, "test-trailer"));

        OutputStream output = response.outputStream();
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> output.write('x'));
        output.write(new byte[0]);
        response.commit();

        assertAll(
                () -> assertThat(exception.getMessage(), containsString(Status.INTERNAL_SERVER_ERROR_500.toString())),
                () -> assertRejectedInformationalResponse(stream)
        );
    }

    @Test
    void filteredNoEntityStatusesBypassNegotiatedGzip() {
        for (Status status : NO_ENTITY_STATUSES) {
            ContentEncodingContext contentEncodingContext = ContentEncodingContext.builder()
                    .addContentEncoding(GzipEncoding.create())
                    .build();
            WritableHeaders<?> requestHeaders = WritableHeaders.create();
            requestHeaders.set(HeaderNames.ACCEPT_ENCODING, "gzip");
            Http2ServerStream stream = mock(Http2ServerStream.class);
            Http2ServerResponse response = createResponse(stream,
                                                          Method.GET,
                                                          contentEncodingContext,
                                                          ServerRequestHeaders.create(requestHeaders));
            AtomicBoolean filterCalled = new AtomicBoolean();
            response.status(status);
            response.contentLength(23);
            response.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            response.header(HeaderValues.create(HeaderNames.TRAILER, "test-trailer"));
            response.streamFilter(output -> {
                filterCalled.set(true);
                return output;
            });

            response.send("entity".getBytes(StandardCharsets.UTF_8));
            response.commit();

            var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
            verify(stream).writeHeaders(responseHeaders.capture(), eq(true));
            verify(stream, never()).writeHeadersWithData(any(), anyInt(), any(), anyBoolean());
            verify(stream, never()).writeData(any(), anyBoolean());
            verify(stream, never()).writeTrailers(any());
            Http2Headers sentHeaders = responseHeaders.getValue();
            Headers sentHttpHeaders = sentHeaders.httpHeaders();
            assertAll(
                    () -> assertThat(filterCalled.get(), is(false)),
                    () -> assertThat(sentHeaders.status(), is(status)),
                    () -> assertNoEntityContentLength(status, sentHttpHeaders),
                    () -> assertThat(sentHttpHeaders.contains(HeaderNames.CONTENT_ENCODING), is(false)),
                    () -> assertThat(sentHttpHeaders.contains(HeaderNames.TRANSFER_ENCODING), is(false)),
                    () -> assertThat(sentHttpHeaders.contains(HeaderNames.TRAILER), is(false))
            );
        }
    }

    @Test
    void filteredNoEntityStatusesHonorExplicitContentEncoding() {
        for (Status status : NO_ENTITY_STATUSES) {
            Http2ServerStream stream = mock(Http2ServerStream.class);
            Http2ServerResponse response = createResponse(stream, Method.GET, ContentEncodingContext.create());
            AtomicBoolean filterCalled = new AtomicBoolean();
            response.status(status);
            response.contentLength(23);
            response.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            response.header(HeaderValues.create(HeaderNames.TRAILER, "test-trailer"));
            response.streamFilter(output -> {
                filterCalled.set(true);
                return output;
            });
            response.contentEncoder(testEncoder());

            response.send("entity".getBytes(StandardCharsets.UTF_8));
            response.commit();

            var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
            verify(stream).writeHeaders(responseHeaders.capture(), eq(true));
            verify(stream, never()).writeHeadersWithData(any(), anyInt(), any(), anyBoolean());
            verify(stream, never()).writeData(any(), anyBoolean());
            verify(stream, never()).writeTrailers(any());
            Http2Headers sentHeaders = responseHeaders.getValue();
            Headers sentHttpHeaders = sentHeaders.httpHeaders();
            assertAll(
                    () -> assertThat(filterCalled.get(), is(false)),
                    () -> assertThat(sentHeaders.status(), is(status)),
                    () -> assertThat(sentHttpHeaders.get(HeaderNames.CONTENT_ENCODING).get(), is("test")),
                    () -> assertThat(sentHttpHeaders.contains(HeaderNames.VARY), is(false)),
                    () -> assertThat(sentHttpHeaders.contains(HeaderNames.TRANSFER_ENCODING), is(false)),
                    () -> assertThat(sentHttpHeaders.contains(HeaderNames.TRAILER), is(false))
            );
        }
    }

    @Test
    void filteredEmptySendSkipsAutomaticContentEncoding() {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(testEncoder());
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.GET, contentEncodingContext);
        response.streamFilter(output -> output);

        response.send(BufferData.EMPTY_BYTES);
        response.commit();

        var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
        verify(stream).writeHeaders(responseHeaders.capture(), anyBoolean());
        verify(contentEncodingContext, never()).encoder(any(Headers.class));
        Headers sentHeaders = responseHeaders.getValue().httpHeaders();
        assertAll(
                () -> assertThat(sentHeaders.contains(HeaderNames.CONTENT_ENCODING), is(false)),
                () -> assertThat(sentHeaders.contains(HeaderNames.VARY), is(false))
        );
    }

    @Test
    void filteredEmptySendHonorsExplicitContentEncoding() {
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.GET, ContentEncodingContext.create());
        response.streamFilter(output -> output);
        response.contentEncoder(testEncoder());

        response.send(BufferData.EMPTY_BYTES);
        response.commit();

        var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
        verify(stream).writeHeaders(responseHeaders.capture(), anyBoolean());
        Headers sentHeaders = responseHeaders.getValue().httpHeaders();
        assertAll(
                () -> assertThat(sentHeaders.get(HeaderNames.CONTENT_ENCODING).get(), is("test")),
                () -> assertThat(sentHeaders.contains(HeaderNames.VARY), is(false))
        );
    }

    @Test
    void filteredSendHonorsBeforeSendNoContent() {
        ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.GET, contentEncodingContext);
        AtomicBoolean filterCalled = new AtomicBoolean();
        AtomicInteger whenSentCalls = new AtomicInteger();
        response.streamFilter(output -> {
            filterCalled.set(true);
            return output;
        });
        response.beforeSend(() -> response.status(Status.NO_CONTENT_204));
        response.whenSent(whenSentCalls::incrementAndGet);

        response.send("entity".getBytes(StandardCharsets.UTF_8));
        response.commit();

        var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
        verify(stream).writeHeaders(responseHeaders.capture(), eq(true));
        verify(stream, never()).writeHeadersWithData(any(), anyInt(), any(), anyBoolean());
        verify(stream, never()).writeData(any(), anyBoolean());
        verify(stream, never()).writeTrailers(any());
        Http2Headers sentHeaders = responseHeaders.getValue();
        assertAll(
                () -> assertThat(filterCalled.get(), is(false)),
                () -> assertThat(response.isSent(), is(true)),
                () -> assertThat(whenSentCalls.get(), is(1)),
                () -> assertThat(sentHeaders.status(), is(Status.NO_CONTENT_204)),
                () -> assertThat(sentHeaders.httpHeaders().contains(HeaderNames.CONTENT_LENGTH), is(false))
        );
    }

    @Test
    void noEntityApplicationWritesRemainRejected() throws IOException {
        for (Status status : NO_ENTITY_STATUSES) {
            ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();
            Http2ServerStream stream = mock(Http2ServerStream.class);
            Http2ServerResponse response = createResponse(stream, Method.GET, contentEncodingContext);
            AtomicInteger whenSentCalls = new AtomicInteger();
            response.status(status);
            response.contentLength(23);
            response.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            response.header(HeaderValues.create(HeaderNames.TRAILER, "test-trailer"));
            response.whenSent(whenSentCalls::incrementAndGet);

            OutputStream output = response.outputStream();
            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> output.write('x'));
            output.write(new byte[0]);
            output.flush();
            response.commit();

            var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
            verify(stream).writeHeaders(responseHeaders.capture(), eq(true));
            verify(stream, never()).writeHeadersWithData(any(), anyInt(), any(), anyBoolean());
            verify(stream, never()).writeData(any(), anyBoolean());
            verify(stream, never()).writeTrailers(any());
            Headers sentHttpHeaders = responseHeaders.getValue().httpHeaders();
            assertAll(
                    () -> assertThat(exception.getMessage(), containsString(status.toString())),
                    () -> assertThat(responseHeaders.getValue().status(), is(status)),
                    () -> assertNoEntityContentLength(status, sentHttpHeaders),
                    () -> assertThat(sentHttpHeaders.contains(HeaderNames.TRANSFER_ENCODING), is(false)),
                    () -> assertThat(sentHttpHeaders.contains(HeaderNames.TRAILER), is(false)),
                    () -> assertThat(whenSentCalls.get(), is(1))
            );
        }
    }

    @Test
    void filteredHeadRejectsEntityBeforeApplyingFilter() {
        byte[] entity = "entity".getBytes(StandardCharsets.UTF_8);
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(testEncoder());
        AtomicBoolean filterApplied = new AtomicBoolean();
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.HEAD, contentEncodingContext);
        response.streamFilter(network -> {
            filterApplied.set(true);
            return network;
        });
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> response.send(entity));

        assertAll(
                () -> assertThat(exception.getMessage(), containsString("HEAD request")),
                () -> assertThat(filterApplied.get(), is(false)),
                () -> assertThat(response.isSent(), is(false))
        );
        verifyNoWrites(stream);
    }

    @Test
    void streamingHeadRejectsEntityBeforeWritingToFilter() throws IOException {
        ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();
        AtomicBoolean filterWritten = new AtomicBoolean();
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.HEAD, contentEncodingContext);
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

        OutputStream output = response.outputStream();
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                        () -> output.write("entity".getBytes(StandardCharsets.UTF_8)));

        assertAll(
                () -> assertThat(exception.getMessage(), containsString("HEAD request")),
                () -> assertThat(filterWritten.get(), is(false)),
                () -> assertThat(response.isSent(), is(false))
        );
        verifyNoWrites(stream);
    }

    @Test
    void streamingHeadRejectsEntityBeforeWritingToEncoder() throws IOException {
        AtomicBoolean encoderWritten = new AtomicBoolean();
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class)))
                .thenReturn(testEncoder(() -> encoderWritten.set(true)));
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.HEAD, contentEncodingContext);

        OutputStream output = response.outputStream();
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                        () -> output.write("entity".getBytes(StandardCharsets.UTF_8)));

        assertAll(
                () -> assertThat(exception.getMessage(), containsString("HEAD request")),
                () -> assertThat(encoderWritten.get(), is(false)),
                () -> assertThat(response.isSent(), is(false))
        );
        verifyNoWrites(stream);
    }

    @Test
    void streamingHeadRejectsEntityBeforeSendingResponse() throws IOException {
        byte[] entity = "entity".getBytes(StandardCharsets.UTF_8);
        ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();

        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.HEAD, contentEncodingContext);
        OutputStream output = response.outputStream();
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> output.write(entity));

        assertAll(
                () -> assertThat(exception.getMessage(), containsString("HEAD request")),
                () -> assertThat(response.isSent(), is(false))
        );
        verifyNoWrites(stream);
    }

    @Test
    void flushedStreamingHeadRejectsEntityBeforeSendingResponse() throws IOException {
        ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.HEAD, contentEncodingContext);

        OutputStream output = response.outputStream();
        output.flush();
        verifyNoWrites(stream);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                        () -> output.write("entity".getBytes(StandardCharsets.UTF_8)));
        assertAll(
                () -> assertThat(exception.getMessage(), containsString("HEAD request")),
                () -> assertThat(response.isSent(), is(false))
        );
        verifyNoWrites(stream);
    }

    @Test
    void flushedStreamingHeadPreservesExplicitContentLength() throws IOException {
        ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.HEAD, contentEncodingContext);
        response.contentLength(42);

        OutputStream output = response.outputStream();
        output.flush();
        verifyNoWrites(stream);
        response.commit();

        var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
        verify(stream).writeHeaders(responseHeaders.capture(), eq(true));
        verify(stream, never()).writeHeadersWithData(any(), anyInt(), any(), anyBoolean());
        verify(stream, never()).writeData(any(), anyBoolean());
        assertThat(responseHeaders.getValue().httpHeaders().contentLength().orElseThrow(), is(42L));
    }

    @Test
    void emptyStreamingHeadSendsTrailersWithoutData() throws IOException {
        ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();
        Http2ServerStream stream = mock(Http2ServerStream.class);
        Http2ServerResponse response = createResponse(stream, Method.HEAD, contentEncodingContext);
        AtomicBoolean beforeTrailersCalled = new AtomicBoolean();
        response.header(HeaderValues.create(HeaderNames.TRAILER, "test-trailer"));
        response.beforeTrailers(_ -> beforeTrailersCalled.set(true));

        response.outputStream().close();
        response.commit();

        verify(stream).writeHeaders(any(Http2Headers.class), eq(false));
        verify(stream, never()).writeHeadersWithData(any(), anyInt(), any(), anyBoolean());
        verify(stream, never()).writeData(any(), anyBoolean());
        verify(stream).writeTrailers(any());
        assertThat(beforeTrailersCalled.get(), is(true));
    }

    private static void assertNoEntityContentLength(Status status, Headers headers) {
        if (status.code() == Status.NO_CONTENT_204.code()) {
            assertThat(headers.contains(HeaderNames.CONTENT_LENGTH), is(false));
        } else {
            assertThat(headers.contentLength().orElseThrow(),
                       is(status.code() == Status.RESET_CONTENT_205.code() ? 0L : 23L));
        }
    }

    private static void verifyNoWrites(Http2ServerStream stream) {
        verify(stream, never()).writeHeaders(any(), anyBoolean());
        verify(stream, never()).writeHeadersWithData(any(), anyInt(), any(), anyBoolean());
        verify(stream, never()).writeData(any(), anyBoolean());
        verify(stream, never()).writeTrailers(any());
    }

    private static ContentEncoder testEncoder() {
        return testEncoder(() -> { });
    }

    private static ContentEncoder testEncoder(Runnable onWrite) {
        return testEncoder(onWrite, false);
    }

    private static ContentEncoder testEncoder(Runnable onWrite, boolean writeOnClose) {
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
                        if (writeOnClose) {
                            network.write('z');
                        }
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

    private static void assertRejectedInformationalResponse(Http2ServerStream stream) {
        var responseHeaders = ArgumentCaptor.forClass(Http2Headers.class);
        verify(stream).writeHeaders(responseHeaders.capture(), eq(true));
        verify(stream, never()).writeHeadersWithData(any(), anyInt(), any(), anyBoolean());
        verify(stream, never()).writeData(any(), anyBoolean());
        verify(stream, never()).writeTrailers(any());
        Http2Headers sentHeaders = responseHeaders.getValue();
        Headers sentHttpHeaders = sentHeaders.httpHeaders();
        assertAll(
                () -> assertThat(sentHeaders.status(), is(Status.INTERNAL_SERVER_ERROR_500)),
                () -> assertThat(sentHttpHeaders.contentLength().orElseThrow(), is(0L)),
                () -> assertThat(sentHttpHeaders.contains(HeaderNames.TRANSFER_ENCODING), is(false)),
                () -> assertThat(sentHttpHeaders.contains(HeaderNames.TRAILER), is(false))
        );
    }

    private static Http2ServerResponse createResponse(Http2ServerStream stream,
                                                      Method method,
                                                      ContentEncodingContext contentEncodingContext) {
        return createResponse(stream,
                              method,
                              contentEncodingContext,
                              ServerRequestHeaders.create(WritableHeaders.create()));
    }

    private static Http2ServerResponse createResponse(Http2ServerStream stream,
                                                      Method method,
                                                      ContentEncodingContext contentEncodingContext,
                                                      ServerRequestHeaders requestHeaders) {
        return createResponse(stream,
                              method,
                              contentEncodingContext,
                              requestHeaders,
                              WebServer.builder().buildPrototype());
    }

    private static Http2ServerResponse createResponse(Http2ServerStream stream,
                                                      Method method,
                                                      ContentEncodingContext contentEncodingContext,
                                                      ListenerConfig listenerConfig) {
        return createResponse(stream,
                              method,
                              contentEncodingContext,
                              ServerRequestHeaders.create(WritableHeaders.create()),
                              listenerConfig);
    }

    private static Http2ServerResponse createResponse(Http2ServerStream stream,
                                                      Method method,
                                                      ContentEncodingContext contentEncodingContext,
                                                      ServerRequestHeaders requestHeaders,
                                                      ListenerConfig listenerConfig) {
        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.contentEncodingContext()).thenReturn(contentEncodingContext);
        when(listenerContext.mediaContext()).thenReturn(MediaContext.create());
        when(listenerContext.config()).thenReturn(listenerConfig);

        ConnectionContext connectionContext = mock(ConnectionContext.class);
        when(connectionContext.listenerContext()).thenReturn(listenerContext);
        when(stream.connectionContext()).thenReturn(connectionContext);

        Http2ServerRequest request = mock(Http2ServerRequest.class);
        HttpPrologue prologue = mock(HttpPrologue.class);
        when(prologue.method()).thenReturn(method);
        when(request.prologue()).thenReturn(prologue);
        when(request.headers()).thenReturn(requestHeaders);

        return new Http2ServerResponse(stream, request, true);
    }
}
