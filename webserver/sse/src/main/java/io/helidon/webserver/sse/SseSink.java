/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.http.sse.SseEvent;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http.spi.Sink;
import io.helidon.webserver.http.spi.SinkProviderContext;

import static io.helidon.http.HeaderValues.CONTENT_TYPE_EVENT_STREAM;
import static io.helidon.http.HeaderValues.create;

/**
 * Implementation of an SSE sink. Emits {@link SseEvent}s.
 */
public class SseSink implements Sink<SseEvent> {

    /**
     * Type of SSE event sinks.
     */
    public static final GenericType<SseSink> TYPE = GenericType.create(SseSink.class);

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
    private final ConnectionContext ctx;
    private final MediaContext mediaContext;
    private final Runnable closeRunnable;

    SseSink(SinkProviderContext context) {
        this.response = context.serverResponse();
        this.ctx = context.connectionContext();
        this.mediaContext = ctx.listenerContext().mediaContext();
        this.closeRunnable = context.closeRunnable();
        writeStatusAndHeaders();
    }

    @Override
    public SseSink emit(SseEvent sseEvent) {
        BufferData bufferData = BufferData.growing(512);

        Optional<String> comment = sseEvent.comment();
        if (comment.isPresent()) {
            bufferData.write(SSE_COMMENT);
            bufferData.write(comment.get().getBytes(StandardCharsets.UTF_8));
            bufferData.write(SSE_NL);
        }
        Optional<String> id = sseEvent.id();
        if (id.isPresent()) {
            bufferData.write(SSE_ID);
            bufferData.write(id.get().getBytes(StandardCharsets.UTF_8));
            bufferData.write(SSE_NL);
        }
        Optional<String> name = sseEvent.name();
        if (name.isPresent()) {
            bufferData.write(SSE_EVENT);
            bufferData.write(name.get().getBytes(StandardCharsets.UTF_8));
            bufferData.write(SSE_NL);
        }
        Object data = sseEvent.data();
        if (data != null) {
            bufferData.write(SSE_DATA);
            byte[] bytes = serializeData(data, sseEvent.mediaType().orElse(MediaTypes.TEXT_PLAIN));
            bufferData.write(bytes);
            bufferData.write(SSE_NL);
        }
        bufferData.write(SSE_NL);

        // write event to the network
        ctx.dataWriter().writeNow(bufferData);
        return this;
    }

    @Override
    public void close() {
        closeRunnable.run();
        ctx.serverSocket().close();
    }

    void writeStatusAndHeaders() {
        ServerResponseHeaders headers = response.headers();

        // verify response has no status or content type
        HttpMediaType ct = headers.contentType().orElse(null);
        if (response.status().code() != Status.OK_200.code()
                || ct != null && !CONTENT_TYPE_EVENT_STREAM.values().equals(ct.mediaType().text())) {
            throw new IllegalStateException("ServerResponse instance cannot be used to create SseSink");
        }

        // start writing status line
        BufferData buffer = BufferData.growing(256);
        buffer.write(OK_200);

        // serialize a date header if not included
        if (!headers.contains(HeaderNames.DATE)) {
            buffer.write(DATE);
            byte[] dateBytes = DateTime.http1Bytes();
            buffer.write(dateBytes);
        }

        // set up and write headers
        if (ct == null) {
            headers.add(CONTENT_TYPE_EVENT_STREAM);
        }
        headers.add(CACHE_NO_CACHE_ONLY);
        for (Header header : headers) {
            header.writeHttp1Header(buffer);
        }

        // complete heading
        buffer.write('\r');        // "\r\n" - empty line after headers
        buffer.write('\n');

        // write response heading to the network
        ctx.dataWriter().writeNow(buffer);
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
                throw new UncheckedIOException(e);
            }
        }
        throw new IllegalStateException("Unable to serialize SSE event without a media context");
    }
}
