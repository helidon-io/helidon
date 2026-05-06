/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.sse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.DateTime;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpMediaType;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.http.sse.SseEvent;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http.spi.SinkProviderContext;

import static io.helidon.http.HeaderValues.CONTENT_TYPE_EVENT_STREAM;
import static io.helidon.http.HeaderValues.create;

/**
 * Implementation of an SSE sink. Emits {@link SseEvent}s.
 */
class DataWriterSseSink implements SseSink {

    /**
     * Type of SSE event sinks.
     */
    public static final GenericType<DataWriterSseSink> TYPE = GenericType.create(DataWriterSseSink.class);

    private static final Header CACHE_NO_CACHE_ONLY = create(HeaderNames.CACHE_CONTROL, "no-cache");
    private static final byte[] SSE_NL = "\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SSE_ID = "id:".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SSE_DATA = "data:".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SSE_EVENT = "event:".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SSE_COMMENT = ":".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OK_200 = "HTTP/1.1 200 OK\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DATE = "Date: ".getBytes(StandardCharsets.UTF_8);
    private static final WritableHeaders<?> EMPTY_HEADERS = WritableHeaders.create();

    private final ServerResponse response;
    private final MediaContext mediaContext;
    private final Runnable closeRunnable;
    private final ConnectionContext ctx;
    private final OutputStream outputStream;

    DataWriterSseSink(SinkProviderContext context) {
        this.response = context.serverResponse();
        this.ctx = context.connectionContext();
        this.mediaContext = ctx.listenerContext().mediaContext();
        this.closeRunnable = context.closeRunnable();

        // output stream to write headers
        OutputStream headersOutputStream = new DataWriterOutputStream(ctx.dataWriter());

        // check for content encoding
        ContentEncoder encoder = null;
        ServerRequestHeaders requestHeaders = context.serverRequest().headers();
        ContentEncodingContext encodingContext = ctx.listenerContext().contentEncodingContext();
        if (encodingContext.contentEncodingEnabled() && requestHeaders.contains(HeaderNames.ACCEPT_ENCODING)) {
            encoder = encodingContext.encoder(requestHeaders);
            encoder.headers(response.headers());        // adds Content-Encoding
        }

        // write status and headers before encoding stream
        writeStatusAndHeaders(headersOutputStream);

        // set the final output stream
        this.outputStream = (encoder != null) ? encoder.apply(headersOutputStream) : headersOutputStream;
    }

    @Override
    public DataWriterSseSink emit(SseEvent sseEvent) {
        try {
            Optional<String> comment = sseEvent.comment();
            if (comment.isPresent()) {
                writeSseMultiLineField(SSE_COMMENT, comment.get().getBytes(StandardCharsets.UTF_8));
            }
            Optional<String> id = sseEvent.id();
            if (id.isPresent()) {
                writeSseSingleLineField(SSE_ID, id.get());
            }
            Optional<String> name = sseEvent.name();
            if (name.isPresent()) {
                writeSseSingleLineField(SSE_EVENT, name.get());
            }
            Object data = sseEvent.data();
            if (data != SseEvent.NO_DATA) {
                MediaType mediaType = sseEvent.mediaType().orElse(MediaTypes.TEXT_PLAIN);
                writeSseMultiLineField(SSE_DATA, serializeData(data, mediaType));
            }
            outputStream.write(SSE_NL);

            // write event to the output
            outputStream.flush();

            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        closeRunnable.run();
        try {
            outputStream.close();
            ctx.serverSocket().close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void writeStatusAndHeaders(OutputStream headersOutputStream) {
        try {
            ServerResponseHeaders headers = response.headers();

            // verify response has no status or content type
            HttpMediaType ct = headers.contentType().orElse(null);
            if (response.status().code() != Status.OK_200.code()
                    || ct != null && !CONTENT_TYPE_EVENT_STREAM.values().equals(ct.mediaType().text())) {
                throw new IllegalStateException("ServerResponse instance cannot be used to create SseSink");
            }

            // start writing status line
            headersOutputStream.write(OK_200);

            // serialize a date header if not included
            if (!headers.contains(HeaderNames.DATE)) {
                headersOutputStream.write(DATE);
                byte[] dateBytes = DateTime.http1Bytes();
                headersOutputStream.write(dateBytes);
            }

            // set up and write headers
            if (ct == null) {
                headers.add(CONTENT_TYPE_EVENT_STREAM);
            }
            headers.set(CACHE_NO_CACHE_ONLY);
            BufferData buffer = BufferData.growing(512);
            for (Header header : headers) {
                header.writeHttp1Header(buffer);
            }
            headersOutputStream.write(buffer.readBytes());

            // complete heading
            headersOutputStream.write('\r');        // "\r\n" - empty line after headers
            headersOutputStream.write('\n');

            // write response heading to the output
            headersOutputStream.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] serializeData(Object object, MediaType mediaType) {
        if (object instanceof byte[] bytes) {
            return bytes;
        } else if (mediaContext != null) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                if (object instanceof String str && mediaType.equals(MediaTypes.TEXT_PLAIN)) {
                    EntityWriter<String> writer = mediaContext.writer(GenericType.STRING, EMPTY_HEADERS, EMPTY_HEADERS);
                    writer.write(GenericType.STRING, str, baos, EMPTY_HEADERS, EMPTY_HEADERS);
                } else {
                    GenericType<Object> type = GenericType.create(object);
                    WritableHeaders<?> resHeaders = WritableHeaders.create();
                    resHeaders.set(HeaderNames.CONTENT_TYPE, mediaType.text());
                    EntityWriter<Object> writer = mediaContext.writer(type, EMPTY_HEADERS, resHeaders);
                    writer.write(type, object, baos, EMPTY_HEADERS, resHeaders);
                }
                return baos.toByteArray();
            } catch (IOException e) {
                throw new ServerConnectionException("Failed to write SSE event", e);

            }
        }
        throw new IllegalStateException("Unable to serialize SSE event without a media context");
    }

    private void writeSseSingleLineField(byte[] field, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        outputStream.write(field);
        int start = 0;
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            if (b == '\r' || b == '\n') {
                outputStream.write(bytes, start, i - start);
                outputStream.write(' ');
                if (b == '\r' && i + 1 < bytes.length && bytes[i + 1] == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }
        outputStream.write(bytes, start, bytes.length - start);
        outputStream.write(SSE_NL);
    }

    private void writeSseMultiLineField(byte[] field, byte[] value) throws IOException {
        int start = 0;
        boolean lineWritten = false;
        for (int i = 0; i < value.length; i++) {
            byte b = value[i];
            if (b == '\r' || b == '\n') {
                outputStream.write(field);
                outputStream.write(value, start, i - start);
                outputStream.write(SSE_NL);
                lineWritten = true;
                if (b == '\r' && i + 1 < value.length && value[i + 1] == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }
        if (!lineWritten || start < value.length) {
            outputStream.write(field);
            outputStream.write(value, start, value.length - start);
            outputStream.write(SSE_NL);
        }
    }
}
