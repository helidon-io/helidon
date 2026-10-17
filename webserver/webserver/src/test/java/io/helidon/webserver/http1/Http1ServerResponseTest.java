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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLException;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.ServerResponseTrailers;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.gzip.GzipEncoding;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class Http1ServerResponseTest {
    private static final List<Status> NO_ENTITY_STATUSES = List.of(Status.NO_CONTENT_204,
                                                                  Status.RESET_CONTENT_205,
                                                                  Status.NOT_MODIFIED_304);

    @ParameterizedTest
    @ValueSource(ints = {204, 205, 304})
    void finalNoEntityStatusRejectsPositiveLengthBeforeWritingHeaders(int code) {
        BufferData buffer = BufferData.growing(128);
        ServerResponseHeaders headers = ServerResponseHeaders.create();
        headers.contentLength(6);

        assertThrows(IllegalStateException.class,
                     () -> Http1ServerResponse.nonEntityBytes(headers, Status.create(code), buffer, true, true, true));

        assertThat(buffer.available(), is(0));
    }

    @ParameterizedTest
    @MethodSource("finalPositiveLengthEmissions")
    void finalNoEntityStatusWithPositiveLengthCanResetAfterRejectedEmission(int code, String emission) {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, ContentEncodingContext.create());
        response.headers().contentLength(5);
        response.status(Status.create(code));
        if (!emission.equals("direct")) {
            response.outputStream();
        }

        assertThrows(IllegalStateException.class, () -> {
            switch (emission) {
                case "direct" -> response.send();
                case "commit" -> response.commit();
                case "flushHeaders" -> response.flushHeaders();
                default -> throw new IllegalArgumentException(emission);
            }
        });

        verify(writer, never()).write(any(BufferData.class));
        assertThat(response.isSent(), is(false));
        assertThat(response.reset(), is(true));
        response.status(Status.INTERNAL_SERVER_ERROR_500);
        response.send("Internal Server Error".getBytes(StandardCharsets.UTF_8));

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 500 Internal Server Error\r\n")),
                () -> assertThat(responseText, containsString("Content-Length: 21\r\n")),
                () -> assertThat(responseText, endsWith("\r\n\r\nInternal Server Error"))
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 205, 304})
    void finalNoEntityStatusWithPositiveLengthRejectsClosedEmptyBuffer(int code) throws IOException {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerRequest request = mock(Http1ServerRequest.class);
        HttpPrologue prologue = mock(HttpPrologue.class);
        when(prologue.method()).thenReturn(Method.GET);
        when(request.prologue()).thenReturn(prologue);
        when(request.headers()).thenReturn(ServerRequestHeaders.create(WritableHeaders.create()));
        ContentEncodingContext encodingContext = mock(ContentEncodingContext.class);
        when(encodingContext.contentEncodingEnabled()).thenReturn(false);
        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.contentEncodingContext()).thenReturn(encodingContext);
        when(listenerContext.mediaContext()).thenReturn(MediaContext.create());
        when(listenerContext.config()).thenReturn(WebServer.builder().writeBufferSize(0).buildPrototype());
        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.listenerContext()).thenReturn(listenerContext);
        Http1ServerResponse response = new Http1ServerResponse(ctx,
                                                               mock(Http1ConnectionListener.class),
                                                               writer,
                                                               request,
                                                               true,
                                                               true,
                                                               true);
        OutputStream output = response.outputStream();
        output.write(new byte[0]);
        response.headers().contentLength(5);
        response.status(Status.create(code));
        output.close();

        verify(writer, never()).write(any(BufferData.class));
        assertThrows(IllegalStateException.class, response::commit);

        verify(writer, never()).write(any(BufferData.class));
        assertThat(response.isSent(), is(false));
        assertThat(response.reset(), is(true));
        response.status(Status.INTERNAL_SERVER_ERROR_500);
        response.send("Internal Server Error".getBytes(StandardCharsets.UTF_8));

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 500 Internal Server Error\r\n")),
                () -> assertThat(responseText, containsString("Content-Length: 21\r\n")),
                () -> assertThat(responseText, endsWith("\r\n\r\nInternal Server Error"))
        );
    }

    @ParameterizedTest(name = "status={0}, bufferSize={1}, bufferedEntity={2}")
    @MethodSource("finalNoEntityStreamingWrites")
    void finalNoEntityStatusRejectsStreamingEntity(int code, int bufferSize, boolean bufferedEntity) throws IOException {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer,
                                                      ContentEncodingContext.create(),
                                                      WebServer.builder().writeBufferSize(bufferSize).buildPrototype());
        OutputStream output = response.outputStream();
        if (bufferedEntity) {
            output.write('A');
        }
        response.status(Status.create(code));

        if (bufferSize == 0) {
            assertThrows(IllegalStateException.class, () -> output.write('B'));
            assertThrows(IllegalStateException.class, () -> output.write(new byte[] {'B'}));
            assertThrows(IllegalStateException.class, () -> output.write(new byte[] {'B'}, 0, 1));
        } else {
            assertThrows(IllegalStateException.class, () -> {
                output.write('B');
                output.write(new byte[] {'C'});
                output.write(new byte[] {'D'}, 0, 1);
                output.flush();
            });
        }

        verify(writer, never()).write(any(BufferData.class));
        assertThat(response.isSent(), is(false));
        assertThat(response.reset(), is(true));
        response.status(Status.INTERNAL_SERVER_ERROR_500);
        response.send("Internal Server Error".getBytes(StandardCharsets.UTF_8));

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 500 Internal Server Error\r\n")),
                () -> assertThat(responseText, containsString("Content-Length: 21\r\n")),
                () -> assertThat(responseText, endsWith("\r\n\r\nInternal Server Error"))
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 205, 304})
    void finalNoEntityStatusDefaultsLengthWhenStreamingHeadersAreFlushed(int code) {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, ContentEncodingContext.create());
        response.outputStream();
        Status status = Status.create(code);
        response.status(status);

        response.flushHeaders();
        response.commit();

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer, atLeastOnce()).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 " + status + "\r\n")),
                () -> assertThat(responseText, containsString("Content-Length: 0\r\n")),
                () -> assertThat(responseText, not(containsString("Transfer-Encoding:"))),
                () -> assertThat(responseText, endsWith("\r\n\r\n"))
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 205, 304})
    void finalNoEntityStatusDoesNotWriteTrailers(int code) {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, ContentEncodingContext.create());
        AtomicBoolean trailersCalled = new AtomicBoolean();
        response.header(HeaderNames.TRAILER, "test-trailer");
        response.beforeTrailers(trailers -> {
            trailersCalled.set(true);
            trailers.set(HeaderNames.create("test-trailer"), "complete");
        });
        response.status(Status.create(code));
        response.outputStream();

        response.flushHeaders();
        response.commit();

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer, atLeastOnce()).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 " + code + " ")),
                () -> assertThat(trailersCalled.get(), is(false)),
                () -> assertThat(responseText, not(containsString("Transfer-Encoding:"))),
                () -> assertThat(responseText, not(containsString("test-trailer: complete\r\n"))),
                () -> assertThat(responseText.substring(responseText.indexOf("\r\n\r\n") + 4), is("")),
                () -> assertThat(responseText, endsWith("\r\n\r\n"))
        );
        if (code == 205) {
            assertThat(responseText, containsString("Content-Length: 0\r\n"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void resetStreamReencodesReplacementEntity(boolean streamingReplacement) throws IOException {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(GzipEncoding.create().encoder());
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.GET, contentEncodingContext);
        response.header(HeaderNames.CONTENT_TYPE, "application/custom");
        response.status(Status.ACCEPTED_202);
        OutputStream original = response.outputStream();
        original.write("original".getBytes(StandardCharsets.UTF_8));
        verifyZeroInteractions(writer);

        assertThat(response.resetStream(), is(true));
        if (streamingReplacement) {
            OutputStream replacement = response.outputStream();
            replacement.write("replacement".getBytes(StandardCharsets.UTF_8));
            replacement.close();
            response.commit();
        } else {
            response.send("replacement".getBytes(StandardCharsets.UTF_8));
        }

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer, atLeastOnce()).write(responseBuffer.capture());
        assertGzipReplacement(responseText(responseBuffer));
    }

    @Test
    void resetStreamReencodesAfterUnsentByteResponseFailsValidation() throws IOException {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(GzipEncoding.create().encoder());
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.GET, contentEncodingContext);
        response.header(HeaderNames.CONTENT_TYPE, "application/custom");
        response.status(Status.ACCEPTED_202);
        response.header("x-invalid", "bad\r\nvalue");

        assertThrows(IllegalArgumentException.class,
                     () -> response.send("original".getBytes(StandardCharsets.UTF_8)));
        verifyZeroInteractions(writer);
        response.headers().set(HeaderValues.create("x-invalid", "valid"));
        response.headers().remove(HeaderNames.CONTENT_LENGTH);
        assertThat(response.resetStream(), is(true));
        response.send("replacement".getBytes(StandardCharsets.UTF_8));

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        assertGzipReplacement(responseText(responseBuffer));
    }

    @Test
    void resetStreamReencodesAfterEncoderCreationFails() throws IOException {
        ContentEncoder encoder = spy(GzipEncoding.create().encoder());
        doThrow(new IllegalStateException("encoder creation failed")).when(encoder).apply(any(OutputStream.class));
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(encoder, GzipEncoding.create().encoder());
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.GET, contentEncodingContext);
        response.header(HeaderNames.CONTENT_TYPE, "application/custom");
        response.status(Status.ACCEPTED_202);

        assertThrows(IllegalStateException.class, response::outputStream);
        verifyZeroInteractions(writer);
        assertThat(response.resetStream(), is(true));
        response.send("replacement".getBytes(StandardCharsets.UTF_8));

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        assertGzipReplacement(responseText(responseBuffer));
    }

    @ParameterizedTest
    @EnumSource(ExplicitContentEncoding.class)
    void resetStreamPreservesExplicitContentEncoding(ExplicitContentEncoding configuration) throws IOException {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(GzipEncoding.create().encoder());
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.GET, contentEncodingContext);
        response.header(HeaderNames.CONTENT_TYPE, "application/custom");
        response.status(Status.ACCEPTED_202);
        if (configuration == ExplicitContentEncoding.BEFORE_STREAM) {
            response.header(HeaderNames.CONTENT_ENCODING, "gzip");
        }
        OutputStream original = response.outputStream();
        original.write("original".getBytes(StandardCharsets.UTF_8));
        switch (configuration) {
        case BEFORE_STREAM -> {
        }
        case SAME_VALUE -> response.headers().set(HeaderValues.create(HeaderNames.CONTENT_ENCODING, "gzip"));
        case CAPTURED_HEADER -> response.headers().set(response.headers().get(HeaderNames.CONTENT_ENCODING));
        case SHARED_HEADER -> {
            WritableHeaders<?> encodingHeaders = WritableHeaders.create();
            GzipEncoding.create().encoder().headers(encodingHeaders);
            response.headers().set(encodingHeaders.get(HeaderNames.CONTENT_ENCODING));
        }
        }
        verifyZeroInteractions(writer);

        assertThat(response.resetStream(), is(true));
        byte[] encodedReplacement = gzipEntity("replacement".getBytes(StandardCharsets.UTF_8));
        response.send(encodedReplacement);

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertGzipReplacement(responseText);
        assertThat(responseText.substring(responseText.indexOf("\r\n\r\n") + 4)
                           .getBytes(StandardCharsets.ISO_8859_1), is(encodedReplacement));
    }

    @Test
    void resetStreamPreservesAppendedContentEncoding() throws IOException {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(GzipEncoding.create().encoder());
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.GET, contentEncodingContext);
        response.outputStream().write("original".getBytes(StandardCharsets.UTF_8));
        response.headers().add(HeaderValues.create(HeaderNames.CONTENT_ENCODING, "gzip"));
        verifyZeroInteractions(writer);

        assertThat(response.resetStream(), is(true));
        assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).allValues(), is(List.of("gzip", "gzip")));
        byte[] encodedReplacement = gzipEntity(gzipEntity("replacement".getBytes(StandardCharsets.UTF_8)));
        response.send(encodedReplacement);

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, containsString("Content-Encoding: gzip\r\nContent-Encoding: gzip\r\n")),
                () -> assertThat(responseText.substring(responseText.indexOf("\r\n\r\n") + 4)
                                        .getBytes(StandardCharsets.ISO_8859_1), is(encodedReplacement))
        );
    }

    @Test
    void resetStreamRefusalPreservesGeneratedContentEncoding() throws IOException {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(GzipEncoding.create().encoder());
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.GET, contentEncodingContext);
        OutputStream output = response.outputStream();
        response.flushHeaders();

        assertThat(response.resetStream(), is(false));
        assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).get(), is("gzip"));
        output.write("original".getBytes(StandardCharsets.UTF_8));
        output.close();
        response.commit();

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer, atLeastOnce()).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 200 OK\r\n")),
                () -> assertThat(responseText, containsString("Content-Encoding: gzip\r\n")),
                () -> assertThat(responseText, endsWith("\r\n0\r\n\r\n"))
        );
    }

    @Test
    void resetStreamPreservesConfiguredHeadersForReplacementEntity() throws IOException {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.GET, ContentEncodingContext.create());
        response.status(Status.ACCEPTED_202);
        response.header(HeaderNames.CONTENT_TYPE, "application/custom");
        response.header(HeaderNames.CONTENT_LANGUAGE, "en");
        response.header(HeaderNames.ETAG, "\"configured\"");
        response.header("x-configured", "value");
        OutputStream output = response.outputStream();
        output.write("original".getBytes(StandardCharsets.UTF_8));
        verifyZeroInteractions(writer);

        assertThat(response.resetStream(), is(true));
        response.send("replacement".getBytes(StandardCharsets.UTF_8));

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 202 Accepted\r\n")),
                () -> assertThat(responseText, containsString("Content-Type: application/custom\r\n")),
                () -> assertThat(responseText, containsString("Content-Language: en\r\n")),
                () -> assertThat(responseText, containsString("ETag: \"configured\"\r\n")),
                () -> assertThat(responseText, containsString("x-configured: value\r\n")),
                () -> assertThat(responseText, not(containsString("original"))),
                () -> assertThat(responseText, endsWith("\r\n\r\nreplacement"))
        );
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
        response.header(HeaderNames.SET_COOKIE, "session=preserved");
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
                () -> assertThat(response.headers().get(HeaderNames.SET_COOKIE).get(), is("session=preserved")),
                () -> assertThat(staleTrailer.defaultCase(), trailers.contains(staleTrailer), is(false))
        );
    }

    @Test
    void resetEntityRemovesInvalidResponseHeaders() {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.GET, ContentEncodingContext.create());
        response.header("invalid\nname", "value");
        response.header("invalid-value", "value\r\ninjected: true");
        response.header("unencodable-value", "\u0100");
        response.header("x-preserved", "value");
        response.header(HeaderNames.VARY, "Origin");
        response.header(HeaderNames.SET_COOKIE, "session=preserved");

        assertThrows(IllegalArgumentException.class,
                     () -> response.send("original".getBytes(StandardCharsets.UTF_8)));
        verifyZeroInteractions(writer);
        assertThat(response.resetEntity(), is(true));
        response.status(Status.INTERNAL_SERVER_ERROR_500);
        response.send("recovered".getBytes(StandardCharsets.UTF_8));

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 500 Internal Server Error\r\n")),
                () -> assertThat(responseText, containsString("x-preserved: value\r\n")),
                () -> assertThat(responseText, containsString("Vary: Origin\r\n")),
                () -> assertThat(responseText, containsString("Set-Cookie: session=preserved\r\n")),
                () -> assertThat(responseText, not(containsString("invalid"))),
                () -> assertThat(responseText, not(containsString("unencodable"))),
                () -> assertThat(responseText, endsWith("\r\n\r\nrecovered"))
        );
    }

    @Test
    void resetEntityRemovesUnencodableResponseHeaderWithoutValidation() {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer,
                                                     Method.GET,
                                                     ContentEncodingContext.create(),
                                                     WebServer.builder().buildPrototype(),
                                                     false);
        response.header("unencodable-value", "\u0100");
        response.header("x-preserved", "value");

        assertThrows(IllegalArgumentException.class,
                     () -> response.send("original".getBytes(StandardCharsets.UTF_8)));
        verifyZeroInteractions(writer);
        assertThat(response.resetEntity(), is(true));
        response.status(Status.INTERNAL_SERVER_ERROR_500);
        response.send("recovered".getBytes(StandardCharsets.UTF_8));

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 500 Internal Server Error\r\n")),
                () -> assertThat(responseText, containsString("x-preserved: value\r\n")),
                () -> assertThat(responseText, not(containsString("unencodable"))),
                () -> assertThat(responseText, endsWith("\r\n\r\nrecovered"))
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidBufferedTrailerCanRecoverBeforeCommit(boolean closeOutput) throws IOException {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.GET, ContentEncodingContext.create());
        response.header(HeaderNames.TRAILER, "x-trailer");
        response.beforeTrailers(trailers -> trailers.set(HeaderNames.create("x-trailer"), "bad\r\ninjected: true"));
        OutputStream output = response.outputStream();
        output.write("original".getBytes(StandardCharsets.UTF_8));
        if (closeOutput) {
            output.close();
        }

        assertThrows(IllegalArgumentException.class, response::commit);
        output.close();
        verifyZeroInteractions(writer);
        assertThat(response.resetEntity(), is(true));
        response.status(Status.INTERNAL_SERVER_ERROR_500);
        response.send("recovered".getBytes(StandardCharsets.UTF_8));

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 500 Internal Server Error\r\n")),
                () -> assertThat(responseText, not(containsString("Trailer:"))),
                () -> assertThat(responseText, not(containsString("original"))),
                () -> assertThat(responseText, endsWith("\r\n\r\nrecovered"))
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidBufferedTrailerRecoveryReencodesErrorEntity(boolean closeOutput) throws IOException {
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.encoder(any(Headers.class))).thenReturn(GzipEncoding.create().encoder());
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.GET, contentEncodingContext);
        response.header(HeaderNames.TRAILER, "x-trailer");
        response.beforeTrailers(trailers -> trailers.set(HeaderNames.create("x-trailer"), "bad\r\ninjected: true"));
        OutputStream output = response.outputStream();
        output.write("original".getBytes(StandardCharsets.UTF_8));
        if (closeOutput) {
            output.close();
        }
        assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).get(), is("gzip"));

        assertThrows(IllegalArgumentException.class, response::commit);
        output.close();
        verifyZeroInteractions(writer);
        assertThat(response.resetEntity(), is(true));
        response.status(Status.INTERNAL_SERVER_ERROR_500);
        response.send("recovered".getBytes(StandardCharsets.UTF_8));

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        int entityStart = responseText.indexOf("\r\n\r\n") + 4;
        String decodedEntity;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(
                responseText.substring(entityStart).getBytes(StandardCharsets.ISO_8859_1)))) {
            decodedEntity = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 500 Internal Server Error\r\n")),
                () -> assertThat(responseText, containsString("Content-Encoding: gzip\r\n")),
                () -> assertThat(responseText, not(containsString("Trailer:"))),
                () -> assertThat(decodedEntity, is("recovered"))
        );
    }

    @Test
    void invalidCommittedTrailerDoesNotTerminateResponse() throws IOException {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.GET, ContentEncodingContext.create());
        response.header(HeaderNames.TRAILER, "x-trailer");
        response.trailers().set(HeaderNames.create("x-trailer"), "bad\r\ninjected: true");
        OutputStream output = response.outputStream();
        response.flushHeaders();
        output.write("hello".getBytes(StandardCharsets.UTF_8));
        output.flush();

        assertThrows(IllegalArgumentException.class, response::commit);
        assertThat(response.resetEntity(), is(false));

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer, atLeastOnce()).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 200 OK\r\n")),
                () -> assertThat(responseText, endsWith("\r\n5\r\nhello\r\n")),
                () -> assertThat(responseText, not(containsString("\r\n0\r\n"))),
                () -> assertThat(responseText, not(containsString("injected")))
        );
    }

    @Test
    void emptyForcedChunkedResponseRemovesContentLength() throws IOException {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.GET, ContentEncodingContext.create());
        response.contentLength(0);
        response.header(HeaderNames.TRANSFER_ENCODING, "chunked");
        response.outputStream().close();
        response.commit();

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer, atLeastOnce()).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, containsString("Transfer-Encoding: chunked\r\n")),
                () -> assertThat(responseText, not(containsString("Content-Length:"))),
                () -> assertThat(responseText, endsWith("\r\n\r\n0\r\n\r\n"))
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emptyForcedChunkedResponseWritesTrailersAfterTerminalChunk(boolean lateContentLength) throws IOException {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.GET, ContentEncodingContext.create());
        AtomicInteger beforeTrailersCalls = new AtomicInteger();
        response.header(HeaderNames.TRAILER, "x-trailer");
        response.beforeTrailers(trailers -> {
            beforeTrailersCalls.incrementAndGet();
            trailers.set(HeaderNames.create("x-trailer"), "value");
            if (lateContentLength) {
                response.headers().set(HeaderValues.CONTENT_LENGTH_ZERO);
            }
        });
        response.outputStream().close();
        response.commit();

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer, atLeastOnce()).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(beforeTrailersCalls.get(), is(1)),
                () -> assertThat(responseText, containsString("Transfer-Encoding: chunked\r\n")),
                () -> assertThat(responseText, not(containsString("Content-Length:"))),
                () -> assertThat(responseText, containsString("\r\n\r\n0\r\n")),
                () -> assertThat(responseText, containsString("x-trailer: value\r\n")),
                () -> assertThat(responseText, endsWith("\r\n\r\n"))
        );
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void emptyChunkedHeadSendsHeadersOnly(boolean flushHeaders, boolean withTrailers) throws IOException {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, Method.HEAD, ContentEncodingContext.create());
        AtomicInteger beforeTrailersCalls = new AtomicInteger();
        response.header(HeaderNames.TRANSFER_ENCODING, "chunked");
        if (withTrailers) {
            response.header(HeaderNames.TRAILER, "x-trailer");
            response.beforeTrailers(trailers -> {
                beforeTrailersCalls.incrementAndGet();
                trailers.set(HeaderNames.create("x-trailer"), "value");
            });
        }
        OutputStream output = response.outputStream();
        if (flushHeaders) {
            response.flushHeaders();
        }
        output.close();
        response.commit();

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(beforeTrailersCalls.get(), is(withTrailers ? 1 : 0)),
                () -> assertThat(responseText, startsWith("HTTP/1.1 200 OK\r\n")),
                () -> assertThat(responseText, containsString("Transfer-Encoding: chunked\r\n")),
                () -> assertThat(responseText, not(containsString("Content-Length:"))),
                () -> assertThat(responseText.substring(responseText.indexOf("\r\n\r\n") + 4), is(""))
        );
    }

    @Test
    void customReasonPhrasePreservesObsText() {
        BufferData buffer = BufferData.growing(128);

        Http1ServerResponse.nonEntityBytes(ServerResponseHeaders.create(),
                                           Status.create(400, "Caf\u00e9"),
                                           buffer,
                                           true,
                                           true,
                                           true);

        String response = new String(buffer.readBytes(), StandardCharsets.ISO_8859_1);
        assertThat(response, startsWith("HTTP/1.1 400 Caf\u00e9\r\n"));
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

    @ParameterizedTest(name = "transientStatus={0}, status={1}, intermediate304={2}, explicitLength={3}")
    @MethodSource("entityStatusTransitions")
    void noEntityToEntityStatusAllowsBufferedEntity(Status transientStatus,
                                                    Status status,
                                                    boolean intermediate304,
                                                    boolean explicitLength) {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, ContentEncodingContext.create());
        if (explicitLength) {
            response.contentLength(6);
        }
        response.status(transientStatus);
        if (intermediate304) {
            response.status(Status.NOT_MODIFIED_304);
        }
        response.status(status);
        assertThat(response.headers().contentLength().orElse(-1), is(explicitLength ? 6L : -1L));
        response.send("entity".getBytes(StandardCharsets.UTF_8));

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 " + status + "\r\n")),
                () -> assertThat(responseText, containsString("Content-Length: 6\r\n")),
                () -> assertThat(responseText, not(containsString("Transfer-Encoding:"))),
                () -> assertThat(responseText, endsWith("\r\n\r\nentity"))
        );
    }

    @ParameterizedTest(name = "transientStatus={0}, status={1}, intermediate304={2}, explicitLength={3}")
    @MethodSource("entityStatusTransitions")
    void noEntityToEntityStatusAllowsStreamingEntity(Status transientStatus,
                                                     Status status,
                                                     boolean intermediate304,
                                                     boolean explicitLength) throws IOException {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, ContentEncodingContext.create());
        if (explicitLength) {
            response.contentLength(6);
        }
        response.status(transientStatus);
        if (intermediate304) {
            response.status(Status.NOT_MODIFIED_304);
        }
        response.status(status);
        response.outputStream().write("entity".getBytes(StandardCharsets.UTF_8));
        response.commit();

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer, atLeastOnce()).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 " + status + "\r\n")),
                () -> assertThat(responseText, explicitLength
                        ? containsString("Content-Length: 6\r\n") : not(containsString("Content-Length:"))),
                () -> assertThat(responseText, explicitLength
                        ? not(containsString("Transfer-Encoding:")) : containsString("Transfer-Encoding: chunked\r\n")),
                () -> assertThat(responseText, endsWith(explicitLength
                                                              ? "\r\n\r\nentity" : "\r\n\r\n6\r\nentity\r\n0\r\n\r\n"))
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 205, 304})
    void temporaryNoEntityStatusPreservesForcedChunking(int code) {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, ContentEncodingContext.create());
        response.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        response.status(Status.create(code));
        response.status(Status.OK_200);
        response.send("entity".getBytes(StandardCharsets.UTF_8));

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 200 OK\r\n")),
                () -> assertThat(responseText, containsString("Transfer-Encoding: chunked\r\n")),
                () -> assertThat(responseText, not(containsString("Content-Length:"))),
                () -> assertThat(responseText, endsWith("\r\n\r\n6\r\nentity\r\n0\r\n\r\n"))
        );
    }

    @ParameterizedTest(name = "transientStatus={0}, streamRequested={1}")
    @CsvSource({"204, false", "204, true", "205, false", "205, true", "304, false", "304, true"})
    void temporaryNoEntityStatusPreservesTrailers(int code, boolean streamRequested) throws IOException {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, ContentEncodingContext.create());
        response.header(HeaderNames.TRAILER, "test-trailer");
        response.beforeTrailers(trailers -> trailers.set(HeaderNames.create("test-trailer"), "complete"));
        OutputStream output = streamRequested ? response.outputStream() : null;
        response.status(Status.create(code));
        response.status(Status.OK_200);
        byte[] entity = "entity".getBytes(StandardCharsets.UTF_8);
        if (streamRequested) {
            output.write(entity);
        } else {
            response.send(entity);
        }
        response.commit();

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer, atLeastOnce()).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 200 OK\r\n")),
                () -> assertThat(responseText, containsString("Transfer-Encoding: chunked\r\n")),
                () -> assertThat(responseText, containsString("Trailer: test-trailer\r\n")),
                () -> assertThat(responseText, not(containsString("Content-Length:"))),
                () -> assertThat(responseText, containsString("\r\n\r\n6\r\nentity\r\n0\r\n")),
                () -> assertThat(responseText, containsString("\r\ntest-trailer: complete\r\n")),
                () -> assertThat(responseText, endsWith("\r\n\r\n"))
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 205, 304})
    void temporaryNoEntityStatusPreservesStreamingLength(int code) throws IOException {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, ContentEncodingContext.create());
        response.contentLength(6);
        OutputStream output = response.outputStream();
        response.status(Status.create(code));
        response.status(Status.OK_200);
        output.write("entity".getBytes(StandardCharsets.UTF_8));
        response.commit();

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer, atLeastOnce()).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 200 OK\r\n")),
                () -> assertThat(responseText, containsString("Content-Length: 6\r\n")),
                () -> assertThat(responseText, not(containsString("Transfer-Encoding:"))),
                () -> assertThat(responseText, endsWith("\r\n\r\nentity"))
        );
    }

    @Test
    void entityStatusAfterResetContentAndNotModifiedAllowsStreamingEntity() throws IOException {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, ContentEncodingContext.create());
        response.status(Status.RESET_CONTENT_205);
        response.status(Status.NOT_MODIFIED_304);
        OutputStream output = response.outputStream();
        response.status(Status.OK_200);
        output.write("entity".getBytes(StandardCharsets.UTF_8));
        response.commit();

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer, atLeastOnce()).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 200 OK\r\n")),
                () -> assertThat(responseText, containsString("Transfer-Encoding: chunked\r\n")),
                () -> assertThat(responseText, containsString("\r\nentity\r\n")),
                () -> assertThat(responseText, endsWith("0\r\n\r\n"))
        );
    }

    @ParameterizedTest(name = "streamRequested={0}, intermediate304={1}, contentLength={2}")
    @CsvSource({"false, false, 0", "false, false, 6", "false, true, 0", "false, true, 6",
                "true, false, 0", "true, false, 6", "true, true, 0", "true, true, 6"})
    void resetContentToEntityStatusPreservesExplicitLength(boolean streamRequested, boolean intermediate304, long length)
            throws IOException {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, ContentEncodingContext.create());
        response.status(Status.RESET_CONTENT_205);
        OutputStream output = streamRequested ? response.outputStream() : null;
        response.headers().contentLength(length);
        if (intermediate304) {
            response.status(Status.NOT_MODIFIED_304);
        }
        response.status(Status.OK_200);
        assertThat(response.headers().contentLength().orElseThrow(), is(length));
        if (!streamRequested) {
            output = response.outputStream();
        }
        response.flushHeaders();
        String entity = length == 0 ? "" : "entity";
        output.write(entity.getBytes(StandardCharsets.UTF_8));
        response.commit();

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer, atLeastOnce()).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 200 OK\r\n")),
                () -> assertThat(responseText, containsString("Content-Length: " + length + "\r\n")),
                () -> assertThat(responseText, not(containsString("Transfer-Encoding:"))),
                () -> assertThat(responseText, endsWith("\r\n\r\n" + entity))
        );
    }

    @Test
    void resetContentToEntityStatusPreservesExplicitZeroHeader() {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, ContentEncodingContext.create());
        response.status(Status.RESET_CONTENT_205);
        response.header(HeaderValues.CONTENT_LENGTH_ZERO);
        response.status(Status.OK_200);
        assertThat(response.headers().contentLength().orElseThrow(), is(0L));
        response.send();

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 200 OK\r\n")),
                () -> assertThat(responseText, containsString("Content-Length: 0\r\n")),
                () -> assertThat(responseText, endsWith("\r\n\r\n"))
        );
    }

    @ParameterizedTest(name = "streamRequested={0}, intermediate304={1}, contentLength={2}")
    @CsvSource({"false, false, 0", "false, false, 6", "false, true, 0", "false, true, 6",
                "true, false, 0", "true, false, 6", "true, true, 0", "true, true, 6"})
    void resetContentToEntityStatusPreservesExplicitLengthWithoutFlushing(boolean streamRequested,
                                                                          boolean intermediate304,
                                                                          long length) throws IOException {
        DataWriter writer = mock(DataWriter.class);
        Http1ServerResponse response = createResponse(writer, ContentEncodingContext.create());
        response.status(Status.RESET_CONTENT_205);
        OutputStream output = streamRequested ? response.outputStream() : null;
        response.headers().contentLength(length);
        if (intermediate304) {
            response.status(Status.NOT_MODIFIED_304);
        }
        response.status(Status.OK_200);
        if (!streamRequested) {
            output = response.outputStream();
        }
        String entity = length == 0 ? "" : "entity";
        output.write(entity.getBytes(StandardCharsets.UTF_8));
        response.commit();

        var responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer, atLeastOnce()).write(responseBuffer.capture());
        String responseText = responseText(responseBuffer);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 200 OK\r\n")),
                () -> assertThat(responseText, containsString("Content-Length: " + length + "\r\n")),
                () -> assertThat(responseText, not(containsString("Transfer-Encoding:"))),
                () -> assertThat(responseText, endsWith("\r\n\r\n" + entity))
        );
    }

    private static byte[] gzipEntity(byte[] entity) throws IOException {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(encoded)) {
            gzip.write(entity);
        }
        return encoded.toByteArray();
    }

    private static void assertGzipReplacement(String responseText) throws IOException {
        String encodedEntity = responseText.substring(responseText.indexOf("\r\n\r\n") + 4);
        assertAll(
                () -> assertThat(responseText, startsWith("HTTP/1.1 202 Accepted\r\n")),
                () -> assertThat(responseText, containsString("Content-Type: application/custom\r\n")),
                () -> assertThat(responseText, containsString("Content-Encoding: gzip\r\n")),
                () -> assertThat(responseText, containsString("Content-Length: " + encodedEntity.length() + "\r\n")),
                () -> assertThat("gzip entity magic", encodedEntity, startsWith("\u001f\u008b"))
        );
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(
                encodedEntity.getBytes(StandardCharsets.ISO_8859_1)))) {
            assertThat(new String(gzip.readAllBytes(), StandardCharsets.UTF_8), is("replacement"));
        }
    }

    private static String responseText(ArgumentCaptor<BufferData> responseBuffer) {
        StringBuilder responseText = new StringBuilder();
        for (BufferData buffer : responseBuffer.getAllValues()) {
            responseText.append(new String(buffer.readBytes(), StandardCharsets.ISO_8859_1));
        }
        return responseText.toString();
    }

    private static Stream<Arguments> finalPositiveLengthEmissions() {
        return Stream.of(204, 205, 304)
                .flatMap(code -> Stream.of("direct", "commit", "flushHeaders")
                        .map(emission -> Arguments.of(code, emission)));
    }

    private static Stream<Arguments> finalNoEntityStreamingWrites() {
        return Stream.of(204, 205, 304)
                .flatMap(code -> Stream.of(0, 4096)
                        .flatMap(bufferSize -> Stream.of(false, true)
                                .map(bufferedEntity -> Arguments.of(code, bufferSize, bufferedEntity))));
    }

    private static Stream<Arguments> entityStatusTransitions() {
        return NO_ENTITY_STATUSES.stream()
                .flatMap(transientStatus -> Stream.of(Status.OK_200, Status.BAD_REQUEST_400,
                                                     Status.NOT_FOUND_404, Status.INTERNAL_SERVER_ERROR_500)
                        .flatMap(status -> (transientStatus == Status.RESET_CONTENT_205
                                ? Stream.of(false, true) : Stream.of(false))
                                .flatMap(intermediate304 -> Stream.of(false, true)
                                        .map(explicitLength -> Arguments.of(transientStatus, status,
                                                                            intermediate304, explicitLength)))));
    }

    private static Http1ServerResponse createResponse(DataWriter dataWriter,
                                                      ContentEncodingContext contentEncodingContext) {
        return createResponse(dataWriter, contentEncodingContext, WebServer.builder().buildPrototype());
    }

    private static Http1ServerResponse createResponse(DataWriter dataWriter,
                                                      ContentEncodingContext contentEncodingContext,
                                                      WebServerConfig config) {
        return createResponse(dataWriter, Method.GET, contentEncodingContext, config);
    }

    private static Http1ServerResponse createResponse(RuntimeException writerFailure) {
        DataWriter dataWriter = mock(DataWriter.class);
        doThrow(writerFailure).when(dataWriter).write(any(BufferData.class));

        ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();

        return createResponse(dataWriter, Method.GET, contentEncodingContext);
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
        return createResponse(dataWriter, method, contentEncodingContext, listenerConfig, true);
    }

    private static Http1ServerResponse createResponse(DataWriter dataWriter,
                                                      Method method,
                                                      ContentEncodingContext contentEncodingContext,
                                                      ListenerConfig listenerConfig,
                                                      boolean validateHeaders) {
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
                                       true,
                                       validateHeaders);
    }

    private enum ExplicitContentEncoding {
        BEFORE_STREAM,
        SAME_VALUE,
        CAPTURED_HEADER,
        SHARED_HEADER
    }
}
