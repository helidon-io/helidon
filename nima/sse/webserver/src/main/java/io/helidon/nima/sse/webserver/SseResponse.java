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

import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.nima.webserver.http1.Http1ServerResponse;

import static io.helidon.common.http.Http.HeaderValues.CONTENT_TYPE_EVENT_STREAM;

/**
 * An SSE response.
 */
public class SseResponse implements AutoCloseable {

    private static final byte[] SSE_DATA = "data:".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SSE_SEPARATOR = "\n\n".getBytes(StandardCharsets.UTF_8);

    private boolean isClosed = false;
    private OutputStream outputStream;
    private final Http1ServerResponse serverResponse;

    private SseResponse(Http1ServerResponse serverResponse) {
        this.serverResponse = serverResponse;
        this.serverResponse.status(Http.Status.OK_200).header(CONTENT_TYPE_EVENT_STREAM);
    }

    public static SseResponse create(ServerResponse serverResponse) {
        // Verify response has no status or content type
        HttpMediaType mt = serverResponse.headers().contentType().orElse(null);
        if (serverResponse.status().code() != Http.Status.OK_200.code() || mt != null) {
            throw new IllegalStateException("ServerResponse must not have set status or content-type");
        }

        // Create SSE response based on HTTP/1 response
        if (serverResponse instanceof Http1ServerResponse res) {
            return new SseResponse(res);
        }
        throw new IllegalStateException("SSE support is only available with HTTP/1 responses");
    }

    public SseResponse send(SseEvent sseEvent) {
        if (outputStream == null) {
            outputStream = serverResponse.outputStream();
            Objects.requireNonNull(outputStream);
        }
        try {
            outputStream.write(SSE_DATA);
            if (sseEvent.mediaType() == MediaTypes.TEXT_PLAIN) {
                String stringData = sseEvent.data().toString();
                outputStream.write(stringData.getBytes(StandardCharsets.UTF_8));
            } else {
                throw new UnsupportedOperationException("Not implemented");
            }
            outputStream.write(SSE_SEPARATOR);
            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

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
     * Checks if SSE response has already been closed.
     *
     * @return outcome of test
     */
    boolean isClosed() {
        return isClosed;
    }
}
