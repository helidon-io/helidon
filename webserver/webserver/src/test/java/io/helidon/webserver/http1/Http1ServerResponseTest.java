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

package io.helidon.webserver.http1;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLException;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.http.DirectHandler;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RequestException;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseTrailers;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.DirectHandlers;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class Http1ServerResponseTest {
    private static final List<Status> NO_ENTITY_STATUSES = List.of(Status.NO_CONTENT_204,
                                                                  Status.RESET_CONTENT_205,
                                                                  Status.NOT_MODIFIED_304);

    @Test
    void resetEntityPreservesPublicBeforeSendListeners() {
        Http1ServerResponse response = createResponse(new IllegalStateException("not used"));
        List<String> invokedListeners = new ArrayList<>();

        response.beforeSend(() -> invokedListeners.add("public-1"));
        response.beforeSend(() -> invokedListeners.add("public-2"));
        response.entityBeforeSend(() -> invokedListeners.add("entity"));

        response.outputStream();
        assertThat(invokedListeners, is(List.of("public-1", "public-2", "entity")));

        assertThat(response.resetEntity(), is(true));
        invokedListeners.clear();
        response.outputStream();

        assertThat(invokedListeners, is(List.of("public-1", "public-2")));
    }

    @Test
    void resetEntityPreservesPublicStreamFilters() {
        Http1ServerResponse response = createResponse(new IllegalStateException("not used"));
        List<String> appliedFilters = new ArrayList<>();

        response.streamFilter(outputStream -> {
            appliedFilters.add("public");
            return outputStream;
        });
        response.entityStreamFilter(outputStream -> {
            appliedFilters.add("entity");
            return outputStream;
        });

        response.outputStream();
        assertThat(appliedFilters, is(List.of("public", "entity")));

        assertThat(response.resetEntity(), is(true));
        appliedFilters.clear();
        response.outputStream();

        assertThat(appliedFilters, is(List.of("public")));
    }

    @Test
    void resetEntityClearsEntityMetadata() {
        Http1ServerResponse response = createResponse(new IllegalStateException("not used"));
        var staleTrailer = HeaderNames.create("x-stale-trailer");
        var contentDigest = HeaderNames.create("Content-Digest");
        var contentMd5 = HeaderNames.create("Content-MD5");
        var digest = HeaderNames.create("Digest");
        var reprDigest = HeaderNames.create("Repr-Digest");

        response.status(Status.PARTIAL_CONTENT_206);
        response.header(HeaderNames.CONTENT_LENGTH, "1");
        response.header(HeaderNames.TRANSFER_ENCODING, "chunked");
        response.header(HeaderNames.TRAILER, staleTrailer.defaultCase());
        response.header(HeaderNames.CONTENT_RANGE, "bytes 0-0/1");
        response.header(HeaderNames.CONTENT_TYPE, "text/plain");
        response.header(HeaderNames.CONTENT_ENCODING, "br");
        response.header(HeaderNames.CONTENT_LANGUAGE, "en");
        response.header(HeaderNames.CONTENT_LOCATION, "/stale");
        response.header(HeaderNames.CONTENT_DISPOSITION, "attachment");
        response.header(contentDigest, "sha-256=:YWJjZA==:");
        response.header(contentMd5, "YWJjZA==");
        response.header(digest, "SHA-256=YWJjZA==");
        response.header(reprDigest, "sha-256=:YWJjZA==:");
        response.header(HeaderNames.ETAG, "\"stale\"");
        response.header(HeaderNames.LAST_MODIFIED, "stale");
        response.header(HeaderNames.ACCEPT_RANGES, "bytes");
        response.header(HeaderNames.CACHE_CONTROL, "no-store");
        response.header(HeaderNames.VARY, "Origin");
        ServerResponseTrailers trailers = response.trailers();
        trailers.set(staleTrailer, "stale");

        assertThat(response.resetEntity(), is(true));

        assertAll(
                () -> assertThat(response.status(), is(Status.PARTIAL_CONTENT_206)),
                () -> assertThat(HeaderNames.CONTENT_LENGTH.defaultCase(),
                                 response.headers().contains(HeaderNames.CONTENT_LENGTH), is(false)),
                () -> assertThat(HeaderNames.TRANSFER_ENCODING.defaultCase(),
                                 response.headers().contains(HeaderNames.TRANSFER_ENCODING), is(false)),
                () -> assertThat(HeaderNames.TRAILER.defaultCase(),
                                 response.headers().contains(HeaderNames.TRAILER), is(false)),
                () -> assertThat(HeaderNames.CONTENT_RANGE.defaultCase(),
                                 response.headers().contains(HeaderNames.CONTENT_RANGE), is(false)),
                () -> assertThat(HeaderNames.CONTENT_TYPE.defaultCase(),
                                 response.headers().contains(HeaderNames.CONTENT_TYPE), is(false)),
                () -> assertThat(HeaderNames.CONTENT_ENCODING.defaultCase(),
                                 response.headers().contains(HeaderNames.CONTENT_ENCODING), is(false)),
                () -> assertThat(HeaderNames.CONTENT_LANGUAGE.defaultCase(),
                                 response.headers().contains(HeaderNames.CONTENT_LANGUAGE), is(false)),
                () -> assertThat(HeaderNames.CONTENT_LOCATION.defaultCase(),
                                 response.headers().contains(HeaderNames.CONTENT_LOCATION), is(false)),
                () -> assertThat(HeaderNames.CONTENT_DISPOSITION.defaultCase(),
                                 response.headers().contains(HeaderNames.CONTENT_DISPOSITION), is(false)),
                () -> assertThat(contentDigest.defaultCase(), response.headers().contains(contentDigest), is(false)),
                () -> assertThat(contentMd5.defaultCase(), response.headers().contains(contentMd5), is(false)),
                () -> assertThat(digest.defaultCase(), response.headers().contains(digest), is(false)),
                () -> assertThat(reprDigest.defaultCase(), response.headers().contains(reprDigest), is(false)),
                () -> assertThat(HeaderNames.ETAG.defaultCase(),
                                 response.headers().contains(HeaderNames.ETAG), is(false)),
                () -> assertThat(HeaderNames.LAST_MODIFIED.defaultCase(),
                                 response.headers().contains(HeaderNames.LAST_MODIFIED), is(false)),
                () -> assertThat(HeaderNames.ACCEPT_RANGES.defaultCase(),
                                 response.headers().contains(HeaderNames.ACCEPT_RANGES), is(false)),
                () -> assertThat(response.headers().get(HeaderNames.CACHE_CONTROL).get(), is("no-store")),
                () -> assertThat(response.headers().get(HeaderNames.VARY).get(), is("Origin")),
                () -> assertThat(staleTrailer.defaultCase(), trailers.contains(staleTrailer), is(false))
        );
    }

    @Test
    void directSendWrapsUncheckedIOException() {
        Http1ServerResponse response = createResponse(new UncheckedIOException(new SocketException("Broken pipe")));

        ServerConnectionException exception = assertThrows(ServerConnectionException.class,
                                                           () -> response.send("hello".getBytes(StandardCharsets.UTF_8)));

        assertAll(
                () -> assertThat(exception.getCause(), instanceOf(UncheckedIOException.class)),
                () -> assertThat(exception.getCause().getCause(), instanceOf(SocketException.class))
        );
    }

    @Test
    void directSendWrapsSocketWriterException() {
        Http1ServerResponse response =
                createResponse(new SocketWriterException(new UncheckedIOException(new SSLException("Engine is closed"))));

        ServerConnectionException exception = assertThrows(ServerConnectionException.class,
                                                           () -> response.send("hello".getBytes(StandardCharsets.UTF_8)));

        assertAll(
                () -> assertThat(exception.getCause(), instanceOf(SocketWriterException.class)),
                () -> assertThat(exception.getCause().getCause(), instanceOf(UncheckedIOException.class)),
                () -> assertThat(exception.getCause().getCause().getCause(), instanceOf(SSLException.class))
        );
    }

    @Test
    void streamingCommitWrapsSocketWriterException() throws Exception {
        Http1ServerResponse response = createResponse(
                new SocketWriterException(new UncheckedIOException(new SocketException("Connection reset by peer"))));

        OutputStream outputStream = response.outputStream();
        outputStream.write("hello".getBytes(StandardCharsets.UTF_8));

        ServerConnectionException exception = assertThrows(ServerConnectionException.class, response::commit);

        assertAll(
                () -> assertThat(exception.getCause(), instanceOf(SocketWriterException.class)),
                () -> assertThat(exception.getCause().getCause(), instanceOf(UncheckedIOException.class)),
                () -> assertThat(exception.getCause().getCause().getCause(), instanceOf(SocketException.class))
        );
    }

    @Test
    void lateNoEntityStatusSuppressesBufferedEntity() throws IOException {
        for (Status status : NO_ENTITY_STATUSES) {
            ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
            when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);
            DataWriter writer = mock(DataWriter.class);
            Http1ServerResponse response = createResponse(writer, Method.GET, contentEncodingContext);
            response.contentLength(23);

            OutputStream output = response.outputStream();
            output.write("entity".getBytes(StandardCharsets.UTF_8));
            response.status(status);
            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> output.write('x'));
            response.commit();

            var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
            verify(writer, atLeastOnce()).write(responseBuffer.capture());
            String responseText = responseText(responseBuffer);
            assertAll(
                    () -> assertThat(exception.getMessage(), containsString(status.toString())),
                    () -> assertThat(responseText, containsString("HTTP/1.1 " + status + "\r\n")),
                    () -> assertNoEntityHeaders(status, responseText),
                    () -> assertThat(responseText.contains("entity"), is(false)),
                    () -> assertThat(responseText, endsWith("\r\n\r\n"))
            );
        }
    }

    @Test
    void flushedFixedLengthResponseRejectsStatusChange() throws IOException {
        assertFlushedResponseRejectsStatusChange(true);
    }

    @Test
    void flushedChunkedResponseRejectsStatusChange() throws IOException {
        assertFlushedResponseRejectsStatusChange(false);
    }

    @Test
    void beforeSendNoEntityStatusSuppressesEntity() {
        for (Status status : NO_ENTITY_STATUSES) {
            ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
            when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);
            DataWriter writer = mock(DataWriter.class);
            Http1ServerResponse response = createResponse(writer, Method.GET, contentEncodingContext);
            AtomicBoolean filterApplied = new AtomicBoolean();
            response.contentLength(23);
            response.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            response.header(HeaderNames.TRAILER, "test-trailer");
            response.streamFilter(outputStream -> {
                filterApplied.set(true);
                return outputStream;
            });
            response.beforeSend(() -> response.status(status));

            response.send("entity".getBytes(StandardCharsets.UTF_8));

            var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
            verify(writer).write(responseBuffer.capture());
            String responseText = responseText(responseBuffer);
            assertAll(
                    () -> assertThat(responseText, containsString("HTTP/1.1 " + status + "\r\n")),
                    () -> assertNoEntityHeaders(status, responseText),
                    () -> assertThat(responseText.contains("entity"), is(false)),
                    () -> assertThat(responseText, endsWith("\r\n\r\n")),
                    () -> assertThat(filterApplied.get(), is(false))
            );
        }
    }

    @Test
    void noEntityStatusWithoutContentLengthUsesRequiredFraming() {
        for (Status status : NO_ENTITY_STATUSES) {
            ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
            when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);
            DataWriter writer = mock(DataWriter.class);
            Http1ServerResponse response = createResponse(writer, Method.GET, contentEncodingContext);

            response.status(status).send();

            var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
            verify(writer).write(responseBuffer.capture());
            String responseText = responseText(responseBuffer);
            assertAll(
                    () -> assertThat(responseText, containsString("HTTP/1.1 " + status + "\r\n")),
                    () -> {
                        if (status.code() == Status.RESET_CONTENT_205.code()) {
                            assertThat(responseText, containsString("Content-Length: 0\r\n"));
                        } else {
                            assertThat(responseText.contains("Content-Length:"), is(false));
                        }
                    },
                    () -> assertThat(responseText, endsWith("\r\n\r\n"))
            );
        }
    }

    @Test
    void noEntityStatusIgnoresEmptyWritesWithoutBuffering() throws IOException {
        for (Status status : NO_ENTITY_STATUSES) {
            ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
            when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);
            DataWriter writer = mock(DataWriter.class);
            ListenerConfig config = WebServer.builder().writeBufferSize(0).buildPrototype();
            Http1ServerResponse response = createResponse(writer, Method.GET, contentEncodingContext, config);
            response.contentLength(23);
            response.status(status);

            OutputStream output = response.outputStream();
            output.write(new byte[0]);
            output.write(new byte[0]);
            response.commit();

            var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
            verify(writer, atLeastOnce()).write(responseBuffer.capture());
            String responseText = responseText(responseBuffer);
            assertAll(
                    () -> assertThat(responseText, containsString("HTTP/1.1 " + status + "\r\n")),
                    () -> assertNoEntityHeaders(status, responseText),
                    () -> assertThat(responseText.substring(responseText.indexOf("\r\n\r\n") + 4), is(""))
            );
        }
    }

    @Test
    void eagerlyFlushedNoEntityStatusIsSentOnce() {
        for (Status status : NO_ENTITY_STATUSES) {
            ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
            when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);
            DataWriter writer = mock(DataWriter.class);
            Http1ServerResponse response = createResponse(writer, Method.GET, contentEncodingContext);
            response.status(status);
            response.contentLength(23);
            response.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            response.header(HeaderNames.TRAILER, "test-trailer");

            response.outputStream();
            response.flushHeaders();
            response.commit();

            var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
            verify(writer).write(responseBuffer.capture());
            String responseText = responseText(responseBuffer);
            assertAll(
                    () -> assertThat(responseText, containsString("HTTP/1.1 " + status + "\r\n")),
                    () -> assertNoEntityHeaders(status, responseText),
                    () -> assertThat(responseText, endsWith("\r\n\r\n"))
            );
        }
    }

    @Test
    void headRejectsEntityBeforeSendingResponse() {
        byte[] entity = "entity".getBytes(StandardCharsets.UTF_8);
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(testEncoder());

        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.HEAD, contentEncodingContext);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> response.send(entity));

        assertAll(
                () -> assertThat(exception.getMessage(), containsString("HEAD request")),
                () -> assertThat(response.isSent(), is(false))
        );
        verifyZeroInteractions(writer);
    }

    @Test
    void directHandlerReplacesContentLengthWithEncodedLength() {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(testEncoder());
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.GET, contentEncodingContext);
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

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = new String(responseBuffer.getValue().readBytes(), StandardCharsets.ISO_8859_1);
        assertAll(
                () -> assertThat(responseText, containsString("Content-Length: 6\r\n")),
                () -> assertThat(responseText, endsWith("\r\n\r\nxerror"))
        );
    }

    @Test
    void directHandlerHeadPreservesContentLengthWithoutEntity() {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.HEAD, contentEncodingContext);
        DirectHandler.TransportRequest request = mock(DirectHandler.TransportRequest.class);
        when(request.method()).thenReturn(Method.HEAD_NAME);
        when(request.protocolVersion()).thenReturn("HTTP/1.1");
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

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = new String(responseBuffer.getValue().readBytes(), StandardCharsets.ISO_8859_1);
        assertAll(
                () -> assertThat(responseText, containsString("HTTP/1.1 400 Bad Request\r\n")),
                () -> assertThat(responseText, containsString("Content-Length: 23\r\n")),
                () -> assertThat(responseText, endsWith("\r\n\r\n"))
        );
    }

    @Test
    void directHandlerHeadUsesEncodedRepresentationMetadata() {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(testEncoder());
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.HEAD, contentEncodingContext);
        DirectHandler.TransportRequest request = mock(DirectHandler.TransportRequest.class);
        when(request.method()).thenReturn(Method.HEAD_NAME);
        when(request.protocolVersion()).thenReturn("HTTP/1.1");
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

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = new String(responseBuffer.getValue().readBytes(), StandardCharsets.ISO_8859_1);
        assertAll(
                () -> assertThat(responseText, containsString("HTTP/1.1 400 Bad Request\r\n")),
                () -> assertThat(responseText, containsString("Content-Encoding: test\r\n")),
                () -> assertThat(responseText, containsString("Vary: Accept-Encoding\r\n")),
                () -> assertThat(responseText, containsString("Content-Length: 6\r\n")),
                () -> assertThat(responseText, endsWith("\r\n\r\n"))
        );
    }

    @Test
    void directHandlerFilteredHeadUsesOnlyConfiguredRepresentationLength() {
        for (long configuredLength : List.of(-1L, 10L)) {
            ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
            when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);
            DataWriter writer = mock(DataWriter.class);
            Http1ServerResponse response = createResponse(writer, Method.HEAD, contentEncodingContext);
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
            when(request.protocolVersion()).thenReturn("HTTP/1.1");
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

            var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
            verify(writer).write(responseBuffer.capture());
            String responseText = new String(responseBuffer.getValue().readBytes(), StandardCharsets.ISO_8859_1);
            assertThat(responseText, endsWith("\r\n\r\n"));
            if (configuredLength < 0) {
                assertThat(responseText.contains("Content-Length:"), is(false));
            } else {
                assertThat(responseText, containsString("Content-Length: " + configuredLength + "\r\n"));
            }
        }
    }

    @Test
    void directHandlerNoContentRemovesContentLength() {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.GET, contentEncodingContext);
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

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = new String(responseBuffer.getValue().readBytes(), StandardCharsets.ISO_8859_1);
        assertAll(
                () -> assertThat(responseText, containsString("HTTP/1.1 204 No Content\r\n")),
                () -> assertThat(responseText.contains("Content-Length:"), is(false)),
                () -> assertThat(responseText.contains("Transfer-Encoding:"), is(false)),
                () -> assertThat(responseText.contains("Trailer:"), is(false)),
                () -> assertThat(responseText, endsWith("\r\n\r\n"))
        );
    }

    @Test
    void filteredHeadRejectsEntityBeforeApplyingFilter() {
        byte[] entity = "entity".getBytes(StandardCharsets.UTF_8);
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(testEncoder());
        AtomicBoolean filterApplied = new AtomicBoolean();
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.HEAD, contentEncodingContext);
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
        verifyZeroInteractions(writer);
    }

    @Test
    void streamingHeadRejectsEntityBeforeWritingToFilter() throws IOException {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);
        AtomicBoolean filterWritten = new AtomicBoolean();
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.HEAD, contentEncodingContext);
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
        verifyZeroInteractions(writer);
    }

    @Test
    void streamingHeadRejectsEntityBeforeWritingToEncoder() throws IOException {
        AtomicBoolean encoderWritten = new AtomicBoolean();
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class)))
                .thenReturn(testEncoder(() -> encoderWritten.set(true)));
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.HEAD, contentEncodingContext);

        OutputStream output = response.outputStream();
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                        () -> output.write("entity".getBytes(StandardCharsets.UTF_8)));

        assertAll(
                () -> assertThat(exception.getMessage(), containsString("HEAD request")),
                () -> assertThat(encoderWritten.get(), is(false)),
                () -> assertThat(response.isSent(), is(false))
        );
        verifyZeroInteractions(writer);
    }

    @Test
    void streamingHeadRejectsEntityBeforeSendingResponse() throws IOException {
        byte[] entity = "entity".getBytes(StandardCharsets.UTF_8);
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);

        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.HEAD, contentEncodingContext);
        OutputStream output = response.outputStream();
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> output.write(entity));

        assertAll(
                () -> assertThat(exception.getMessage(), containsString("HEAD request")),
                () -> assertThat(response.isSent(), is(false))
        );
        verifyZeroInteractions(writer);
    }

    @Test
    void flushedStreamingHeadRejectsEntityBeforeSendingResponse() throws IOException {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.HEAD, contentEncodingContext);

        OutputStream output = response.outputStream();
        output.flush();
        verifyZeroInteractions(writer);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                        () -> output.write("entity".getBytes(StandardCharsets.UTF_8)));

        assertAll(
                () -> assertThat(exception.getMessage(), containsString("HEAD request")),
                () -> assertThat(response.isSent(), is(false))
        );
        verifyZeroInteractions(writer);
    }

    @Test
    void flushedStreamingHeadNoEntityStatusSendsHeadersOnce() throws IOException {
        for (Status status : NO_ENTITY_STATUSES) {
            ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
            when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);
            DataWriter writer = mock(DataWriter.class);
            Http1ServerResponse response = createResponse(writer, Method.HEAD, contentEncodingContext);
            response.contentLength(23);
            response.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            response.header(HeaderNames.TRAILER, "test-trailer");
            response.status(status);

            OutputStream output = response.outputStream();
            output.flush();
            response.commit();

            var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
            verify(writer).write(responseBuffer.capture());
            String responseText = responseText(responseBuffer);
            assertAll(
                    () -> assertThat(responseText, containsString("HTTP/1.1 " + status + "\r\n")),
                    () -> assertNoEntityHeaders(status, responseText),
                    () -> assertThat(responseText, endsWith("\r\n\r\n"))
            );
        }
    }

    @Test
    void flushedStreamingHeadPreservesExplicitContentLength() throws IOException {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.HEAD, contentEncodingContext);
        response.contentLength(11);

        OutputStream output = response.outputStream();
        output.flush();
        verifyZeroInteractions(writer);
        response.commit();

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = new String(responseBuffer.getValue().readBytes(), StandardCharsets.ISO_8859_1);
        assertAll(
                () -> assertThat(responseText, containsString("Content-Length: 11\r\n")),
                () -> assertThat(responseText, endsWith("\r\n\r\n"))
        );
    }

    @Test
    void emptyStreamingHeadEvaluatesButDoesNotSendTrailers() throws IOException {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.HEAD, contentEncodingContext);
        AtomicBoolean beforeTrailersCalled = new AtomicBoolean();
        response.header(HeaderValues.create(HeaderNames.TRAILER, "test-trailer"));
        response.beforeTrailers(_ -> beforeTrailersCalled.set(true));

        response.outputStream().close();
        response.commit();

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = new String(responseBuffer.getValue().readBytes(), StandardCharsets.ISO_8859_1);
        assertAll(
                () -> assertThat(beforeTrailersCalled.get(), is(true)),
                () -> assertThat(responseText, containsString("Trailer: test-trailer\r\n")),
                () -> assertThat(responseText, endsWith("\r\n\r\n"))
        );
    }

    @Test
    void forcedChunkedHeadSendsHeadersOnly() throws IOException {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.HEAD, contentEncodingContext);
        response.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);

        response.outputStream().close();
        response.commit();

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = new String(responseBuffer.getValue().readBytes(), StandardCharsets.ISO_8859_1);
        assertAll(
                () -> assertThat(responseText, containsString("Transfer-Encoding: chunked\r\n")),
                () -> assertThat(responseText, endsWith("\r\n\r\n"))
        );
    }

    private static String responseText(ArgumentCaptor<BufferData> responseBuffer) {
        StringBuilder responseText = new StringBuilder();
        for (BufferData buffer : responseBuffer.getAllValues()) {
            responseText.append(new String(buffer.readBytes(), StandardCharsets.ISO_8859_1));
        }
        return responseText.toString();
    }

    private static void assertFlushedResponseRejectsStatusChange(boolean fixedLength) throws IOException {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.GET, contentEncodingContext);
        byte[] entity = "entity".getBytes(StandardCharsets.UTF_8);
        if (fixedLength) {
            response.contentLength(entity.length);
        }

        OutputStream output = response.outputStream();
        output.write(entity);
        output.flush();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                        () -> response.status(Status.NO_CONTENT_204));
        response.commit();

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer, atLeastOnce()).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        int statusStart = responseText.indexOf("HTTP/1.1 ");
        String expectedEnding = fixedLength ? "\r\n\r\nentity" : "0\r\n\r\n";
        assertAll(
                () -> assertThat(exception.getMessage(), containsString("already sent")),
                () -> assertThat(responseText, containsString("HTTP/1.1 200 OK\r\n")),
                () -> assertThat(responseText.indexOf("HTTP/1.1 ", statusStart + 1), is(-1)),
                () -> assertThat(responseText, containsString("entity")),
                () -> assertThat(responseText, endsWith(expectedEnding))
        );
    }

    private static void assertNoEntityHeaders(Status status, String responseText) {
        int statusCode = status.code();
        if (statusCode == Status.NO_CONTENT_204.code()) {
            assertThat(responseText.contains("Content-Length:"), is(false));
        } else if (statusCode == Status.RESET_CONTENT_205.code()) {
            assertThat(responseText, containsString("Content-Length: 0\r\n"));
        } else {
            assertThat(responseText, containsString("Content-Length: 23\r\n"));
        }
        assertThat(responseText.contains("Transfer-Encoding:"), is(false));
        assertThat(responseText.contains("Trailer:"), is(false));
    }

    private static Http1ServerResponse createResponse(RuntimeException writerFailure) {
        DataWriter dataWriter = mock(DataWriter.class);
        doThrow(writerFailure).when(dataWriter).write(any(BufferData.class));

        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);

        return createResponse(dataWriter, Method.GET, contentEncodingContext);
    }

    private static ContentEncoder testEncoder() {
        return testEncoder(() -> { });
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

    private static Http1ServerResponse createResponse(DataWriter dataWriter,
                                                      Method method,
                                                      ContentEncodingContext contentEncodingContext) {
        return createResponse(dataWriter, method, contentEncodingContext, WebServer.builder().buildPrototype());
    }

    private static Http1ServerResponse createResponse(DataWriter dataWriter,
                                                      Method method,
                                                      ContentEncodingContext contentEncodingContext,
                                                      ListenerConfig listenerConfig) {
        Http1ServerRequest request = mock(Http1ServerRequest.class);
        HttpPrologue prologue = mock(HttpPrologue.class);
        when(prologue.method()).thenReturn(method);
        when(request.headers()).thenReturn(ServerRequestHeaders.create(WritableHeaders.create()));
        when(request.prologue()).thenReturn(prologue);

        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.contentEncodingContext()).thenReturn(contentEncodingContext);
        when(listenerContext.mediaContext()).thenReturn(MediaContext.create());
        when(listenerContext.config()).thenReturn(listenerConfig);

        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.listenerContext()).thenReturn(listenerContext);

        return new Http1ServerResponse(ctx,
                                       mock(Http1ConnectionListener.class),
                                       dataWriter,
                                       request,
                                       true,
                                       true);
    }
}
