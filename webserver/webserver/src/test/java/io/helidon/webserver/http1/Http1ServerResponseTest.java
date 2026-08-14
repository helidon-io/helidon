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

import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLException;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.http.HeaderNames;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseTrailers;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http1ServerResponseTest {

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
                () -> assertThat(response.status(), is(Status.OK_200)),
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

    private static Http1ServerResponse createResponse(RuntimeException writerFailure) {
        DataWriter dataWriter = mock(DataWriter.class);
        doThrow(writerFailure).when(dataWriter).write(any(BufferData.class));

        Http1ServerRequest request = mock(Http1ServerRequest.class);
        when(request.headers()).thenReturn(ServerRequestHeaders.create(WritableHeaders.create()));

        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);

        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.contentEncodingContext()).thenReturn(contentEncodingContext);
        when(listenerContext.mediaContext()).thenReturn(MediaContext.create());
        when(listenerContext.config()).thenReturn(WebServer.builder().buildPrototype());

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
