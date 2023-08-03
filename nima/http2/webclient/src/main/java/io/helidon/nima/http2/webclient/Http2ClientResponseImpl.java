/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.http2.webclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.Http;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.http.media.ReadableEntity;
import io.helidon.nima.webclient.api.ClientResponseEntity;
import io.helidon.nima.webclient.api.ClientUri;

class Http2ClientResponseImpl implements Http2ClientResponse {
    private final Http.Status responseStatus;
    private final ClientRequestHeaders requestHeaders;
    private final ClientResponseHeaders responseHeaders;
    private final CompletableFuture<Void> complete;
    private final Runnable closeResponseRunnable;
    private final InputStream inputStream;
    private final MediaContext mediaContext;
    private final ClientUri lastEndpointUri;

    Http2ClientResponseImpl(Http.Status status,
                            ClientRequestHeaders requestHeaders,
                            ClientResponseHeaders responseHeaders,
                            InputStream inputStream, // input stream is nullable - no response entity
                            MediaContext mediaContext,
                            ClientUri lastEndpointUri,
                            CompletableFuture<Void> complete,
                            Runnable closeResponseRunnable) {
        this.responseStatus = status;
        this.requestHeaders = requestHeaders;
        this.responseHeaders = responseHeaders;
        this.inputStream = inputStream;
        this.mediaContext = mediaContext;
        this.lastEndpointUri = lastEndpointUri;
        this.complete = complete;
        this.closeResponseRunnable = closeResponseRunnable;
    }

    @Override
    public Http.Status status() {
        return responseStatus;
    }

    @Override
    public ClientResponseHeaders headers() {
        return responseHeaders;
    }

    @Override
    public ReadableEntity entity() {
        if (inputStream == null) {
            return ClientResponseEntity.empty();
        }

        return ClientResponseEntity.create(
                this::readBytes,
                this::close,
                requestHeaders,
                responseHeaders,
                mediaContext
        );
    }

    private BufferData readBytes(int estimate) {
        try {
            // Empty buffer is considered as a fully consumed entity
            // so estimate can't be less than 1
            byte[] buffer = new byte[estimate > 0 ? estimate : 16];
            int read = inputStream.read(buffer);
            if (read < 1) {
                return BufferData.empty();
            }
            return BufferData.create(buffer, 0, read);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ClientUri lastEndpointUri() {
        return lastEndpointUri;
    }

    @Override
    public void close() {
        complete.complete(null);
        closeResponseRunnable.run();
    }
}
