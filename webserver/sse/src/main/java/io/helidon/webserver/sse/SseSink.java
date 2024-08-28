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

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.BiConsumer;

import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Status;
import io.helidon.http.sse.SseEvent;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http.spi.Sink;

import static io.helidon.http.HeaderValues.CONTENT_TYPE_EVENT_STREAM;

/**
 * Implementation of an SSE sink. Emits {@link SseEvent}s.
 */
public class SseSink implements Sink<SseEvent> {

    /**
     * Type of SSE event sinks.
     */
    public static final GenericType<SseSink> TYPE = GenericType.create(SseSink.class);

    private static final byte[] SSE_NL = "\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SSE_ID = "id:".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SSE_DATA = "data:".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SSE_EVENT = "event:".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SSE_COMMENT = ":".getBytes(StandardCharsets.UTF_8);

    private final BiConsumer<Object, MediaType> eventConsumer;
    private final Runnable closeRunnable;
    private final OutputStream outputStream;

    SseSink(ServerResponse serverResponse, BiConsumer<Object, MediaType> eventConsumer, Runnable closeRunnable) {
        // Verify response has no status or content type
        HttpMediaType ct = serverResponse.headers().contentType().orElse(null);
        if (serverResponse.status().code() != Status.OK_200.code()
                || ct != null && !CONTENT_TYPE_EVENT_STREAM.values().equals(ct.mediaType().text())) {
            throw new IllegalStateException("ServerResponse instance cannot be used to create SseResponse");
        }

        // Ensure content type set for SSE
        if (ct == null) {
            serverResponse.headers().add(CONTENT_TYPE_EVENT_STREAM);
        }

        this.outputStream = serverResponse.outputStream();
        this.eventConsumer = eventConsumer;
        this.closeRunnable = closeRunnable;
    }

    @Override
    public SseSink emit(SseEvent sseEvent) {
        try {
            Optional<String> comment = sseEvent.comment();
            if (comment.isPresent()) {
                outputStream.write(SSE_COMMENT);
                outputStream.write(comment.get().getBytes(StandardCharsets.UTF_8));
                outputStream.write(SSE_NL);
            }
            Optional<String> id = sseEvent.id();
            if (id.isPresent()) {
                outputStream.write(SSE_ID);
                outputStream.write(id.get().getBytes(StandardCharsets.UTF_8));
                outputStream.write(SSE_NL);
            }
            Optional<String> name = sseEvent.name();
            if (name.isPresent()) {
                outputStream.write(SSE_EVENT);
                outputStream.write(name.get().getBytes(StandardCharsets.UTF_8));
                outputStream.write(SSE_NL);
            }
            Object data = sseEvent.data();
            if (data != SseEvent.NO_DATA) {
                outputStream.write(SSE_DATA);
                eventConsumer.accept(data, sseEvent.mediaType().orElse(MediaTypes.TEXT_PLAIN));
                outputStream.write(SSE_NL);
            }
            outputStream.write(SSE_NL);
            outputStream.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    @Override
    public void close() {
        closeRunnable.run();
    }
}
