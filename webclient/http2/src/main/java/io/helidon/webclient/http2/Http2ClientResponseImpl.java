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

package io.helidon.webclient.http2;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.Status;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.ReadableEntity;
import io.helidon.webclient.api.ClientResponseEntity;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.api.ReleasableResource;

class Http2ClientResponseImpl implements Http2ClientResponse {
    private final HttpClientConfig httpClientConfig;
    private final Status responseStatus;
    private final ClientRequestHeaders requestHeaders;
    private final ClientResponseHeaders responseHeaders;
    private final ReleasableResource stream;
    private final CompletableFuture<Void> complete;
    private final Runnable closeResponseRunnable;
    private final CompletableFuture<ClientResponseTrailers> responseTrailers;
    private final InputStream inputStream;
    private final MediaContext mediaContext;
    private final ClientUri lastEndpointUri;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private boolean entityRequested;

    Http2ClientResponseImpl(HttpClientConfig httpClientConfig,
                            Status status,
                            ClientRequestHeaders requestHeaders,
                            ClientResponseHeaders responseHeaders,
                            CompletableFuture<ClientResponseTrailers> responseTrailers,
                            InputStream inputStream, // input stream is nullable - no response entity
                            MediaContext mediaContext,
                            ClientUri lastEndpointUri,
                            ReleasableResource stream,
                            CompletableFuture<Void> complete,
                            Runnable closeResponseRunnable) {
        this.httpClientConfig = httpClientConfig;
        this.responseStatus = status;
        this.requestHeaders = requestHeaders;
        this.responseHeaders = responseHeaders;
        this.responseTrailers = responseTrailers;
        this.inputStream = inputStream;
        this.mediaContext = mediaContext;
        this.lastEndpointUri = lastEndpointUri;
        this.stream = stream;
        this.complete = complete;
        this.closeResponseRunnable = closeResponseRunnable;
    }

    @Override
    public Status status() {
        return responseStatus;
    }

    @Override
    public ClientResponseHeaders headers() {
        return responseHeaders;
    }

    @Override
    public ClientResponseTrailers trailers() {
        // Block until trailers arrive
        Duration timeout = httpClientConfig.readTimeout()
                .orElseGet(() -> httpClientConfig.socketOptions().readTimeout());

        if (!this.entityRequested) {
            throw new IllegalStateException("Trailers requested before reading entity.");
        }

        try {
            return ClientResponseTrailers.create(this.responseTrailers.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timeout " + timeout + " reached while waiting for trailers.", e);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted while waiting for trailers.", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalStateException ise) {
                throw ise;
            } else {
                throw new IllegalStateException(e.getCause());
            }
        }
    }

    @Override
    public ReadableEntity entity() {
        this.entityRequested = true;

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

    Http2ClientStream stream() {
        return (Http2ClientStream) stream;
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
        if (!closed.getAndSet(true)) {
            complete.complete(null);
            closeResponseRunnable.run();
        }
    }
}
