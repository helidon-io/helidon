/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.sse.webserver;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.helidon.common.GenericType;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.nima.webserver.http1.Http1ServerResponse;

import static io.helidon.common.http.Http.HeaderValues.CONTENT_TYPE_EVENT_STREAM;

/**
 * An SSE response that can be used to stream events to a client. Must be
 * created from a regular HTTP/1 response whose content type or status was
 * not set.
 *
 * @see #create(ServerResponse)
 */
public class SseResponse implements AutoCloseable {

    private static final byte[] SSE_DATA = "data:".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SSE_SEPARATOR = "\n\n".getBytes(StandardCharsets.UTF_8);
    private static final WritableHeaders<?> EMPTY_HEADERS = WritableHeaders.create();

    private boolean isClosed = false;
    private OutputStream outputStream;
    private final Http1ServerResponse serverResponse;
    private final MediaContext mediaContext;

    private SseResponse(Http1ServerResponse serverResponse) {
        this.serverResponse = serverResponse;
        this.mediaContext = serverResponse.mediaContext();
    }

    /**
     * Creates an SSE response from an HTTP/1 response. Once a response is used to create
     * an SSE response, it should not be interacted with anymore. Moreover, a response
     * used to create an SSE response must not have set its status code or its media
     * type, these shall be set automatically set in the newly created SSE response.
     *
     * @param serverResponse source response
     * @return new SSE response
     * @throws IllegalStateException if source not HTTP/1 or status or content type set
     */
    public static SseResponse create(ServerResponse serverResponse) {
        // Verify response has no status or content type
        HttpMediaType mt = serverResponse.headers().contentType().orElse(null);
        if (serverResponse.status().code() != Http.Status.OK_200.code()
                || mt != null && !CONTENT_TYPE_EVENT_STREAM.values().equals(mt.mediaType().text())) {
            throw new IllegalStateException("ServerResponse instance cannot be used to create SseResponse");
        }

        // Create SSE response based on HTTP/1 response
        if (serverResponse instanceof Http1ServerResponse res) {
            res.headers().add(CONTENT_TYPE_EVENT_STREAM);
            return new SseResponse(res);
        }
        throw new IllegalStateException("SSE support is only available with HTTP/1 responses");
    }

    /**
     * Sends an event over the SSE stream.
     *
     * @param sseEvent event to send
     * @return this SSE response
     */
    public SseResponse send(SseEvent sseEvent) {
        if (outputStream == null) {
            outputStream = serverResponse.outputStream();
            Objects.requireNonNull(outputStream);
        }
        try {
            outputStream.write(SSE_DATA);

            Object data = sseEvent.data();
            if (data instanceof byte[] bytes) {
                outputStream.write(bytes);
            } else {
                MediaType mediaType = sseEvent.mediaType();

                if (data instanceof String str && mediaType.equals(MediaTypes.TEXT_PLAIN)) {
                    EntityWriter<String> writer = mediaContext.writer(GenericType.STRING, EMPTY_HEADERS, EMPTY_HEADERS);
                    writer.write(GenericType.STRING, str, outputStream, EMPTY_HEADERS, EMPTY_HEADERS);
                } else {
                    GenericType<Object> type = GenericType.create(data);
                    WritableHeaders<?> resHeaders = WritableHeaders.create();
                    resHeaders.set(Http.Header.CONTENT_TYPE, sseEvent.mediaType().text());
                    EntityWriter<Object> writer = mediaContext.writer(type, EMPTY_HEADERS, resHeaders);
                    writer.write(type, data, outputStream, EMPTY_HEADERS, resHeaders);
                }
            }

            outputStream.write(SSE_SEPARATOR);
            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    /**
     * Closes the SSE stream.
     */
    @Override
    public void close() {
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        isClosed = true;
    }

    /**
     * Checks if SSE stream has already been closed.
     *
     * @return outcome of test
     */
    boolean isClosed() {
        return isClosed;
    }
}
