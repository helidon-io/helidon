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

package io.helidon.webclient.http3;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.Status;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.ReadableEntity;
import io.helidon.webclient.api.ClientResponseEntity;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientResponse;

class Http3ClientResponseImpl implements Http3ClientResponse {
    private final HttpClientResponse delegate;
    private final Duration readTimeout;
    private final String protocolId;
    private final Status responseStatus;
    private final ClientRequestHeaders requestHeaders;
    private final ClientResponseHeaders responseHeaders;
    private final CompletableFuture<Void> complete;
    private final Runnable closeResponseRunnable;
    private final Consumer<Throwable> readFailureAction;
    private final CompletableFuture<ClientResponseTrailers> responseTrailers;
    private final InputStream inputStream;
    private final MediaContext mediaContext;
    private final ClientUri lastEndpointUri;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final long maxBufferedEntitySize;
    private boolean entityRequested;

    Http3ClientResponseImpl(HttpClientResponse delegate) {
        this.delegate = delegate;
        this.readTimeout = null;
        this.protocolId = null;
        this.responseStatus = null;
        this.requestHeaders = null;
        this.responseHeaders = null;
        this.complete = null;
        this.closeResponseRunnable = null;
        this.readFailureAction = null;
        this.responseTrailers = null;
        this.inputStream = null;
        this.mediaContext = null;
        this.lastEndpointUri = null;
        this.maxBufferedEntitySize = -1;
    }

    Http3ClientResponseImpl(Duration readTimeout,
                            String protocolId,
                            Status responseStatus,
                            ClientRequestHeaders requestHeaders,
                            ClientResponseHeaders responseHeaders,
                            CompletableFuture<ClientResponseTrailers> responseTrailers,
                            InputStream inputStream,
                            MediaContext mediaContext,
                            ClientUri lastEndpointUri,
                            CompletableFuture<Void> complete,
                            Runnable closeResponseRunnable,
                            Consumer<Throwable> readFailureAction,
                            long maxBufferedEntitySize) {
        this.delegate = null;
        this.readTimeout = readTimeout;
        this.protocolId = protocolId;
        this.responseStatus = responseStatus;
        this.requestHeaders = requestHeaders;
        this.responseHeaders = responseHeaders;
        this.responseTrailers = responseTrailers;
        this.inputStream = inputStream;
        this.mediaContext = mediaContext;
        this.lastEndpointUri = lastEndpointUri;
        this.complete = complete;
        this.closeResponseRunnable = closeResponseRunnable;
        this.readFailureAction = readFailureAction;
        this.maxBufferedEntitySize = maxBufferedEntitySize;
    }

    @Override
    public String protocolId() {
        return delegate == null ? protocolId : delegate.protocolId();
    }

    @Override
    public Status status() {
        return delegate == null ? responseStatus : delegate.status();
    }

    @Override
    public ClientResponseHeaders headers() {
        return delegate == null ? responseHeaders : delegate.headers();
    }

    @Override
    public ClientResponseTrailers trailers() {
        if (delegate != null) {
            return delegate.trailers();
        }

        if (!entityRequested) {
            throw new IllegalStateException("Trailers requested before reading entity.");
        }
        try {
            return ClientResponseTrailers.create(this.responseTrailers.get(readTimeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            IllegalStateException failure = new IllegalStateException(
                    "Timeout " + readTimeout + " reached while waiting for trailers.", e);
            failRead(failure);
            throw failure;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            IllegalStateException failure = new IllegalStateException("Interrupted while waiting for trailers.", e);
            failRead(failure);
            throw failure;
        } catch (ExecutionException e) {
            IllegalStateException failure;
            if (e.getCause() instanceof IllegalStateException ise) {
                failure = ise;
            } else {
                failure = new IllegalStateException(e.getCause());
            }
            failRead(failure);
            throw failure;
        }
    }

    @Override
    public ReadableEntity entity() {
        if (delegate != null) {
            return delegate.entity();
        }

        this.entityRequested = true;
        if (inputStream == null) {
            return ClientResponseEntity.empty();
        }

        return ClientResponseEntity.create(
                this::readBytes,
                this::close,
                requestHeaders,
                responseHeaders,
                mediaContext,
                maxBufferedEntitySize);
    }

    @Override
    public long maxBufferedEntitySize() {
        return delegate == null ? maxBufferedEntitySize : delegate.maxBufferedEntitySize();
    }

    @Override
    public void serviceEntityConsumed() {
        if (delegate == null) {
            entityRequested = true;
        } else {
            delegate.serviceEntityConsumed();
        }
    }

    @Override
    public ClientUri lastEndpointUri() {
        return delegate == null ? lastEndpointUri : delegate.lastEndpointUri();
    }

    ClientRequestHeaders effectiveRequestHeaders(ClientRequestHeaders fallback) {
        return delegate == null ? requestHeaders : fallback;
    }

    @Override
    public void close() {
        if (delegate != null) {
            delegate.close();
            return;
        }
        if (closed.compareAndSet(false, true)) {
            try {
                closeResponseRunnable.run();
                complete.complete(null);
            } catch (RuntimeException | Error cleanupFailure) {
                responseTrailers.completeExceptionally(cleanupFailure);
                complete.completeExceptionally(cleanupFailure);
                throw cleanupFailure;
            }
        }
    }

    private BufferData readBytes(int estimate) {
        try {
            byte[] buffer = new byte[estimate > 0 ? estimate : 16];
            int read = inputStream.read(buffer);
            if (read < 1) {
                return BufferData.empty();
            }
            return BufferData.create(buffer, 0, read);
        } catch (IOException e) {
            UncheckedIOException failure = new UncheckedIOException(e);
            failRead(failure);
            throw failure;
        } catch (RuntimeException | Error failure) {
            failRead(failure);
            throw failure;
        }
    }

    private void failRead(Throwable failure) {
        if (closed.compareAndSet(false, true)) {
            try {
                readFailureAction.accept(failure);
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure != cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            responseTrailers.completeExceptionally(failure);
            complete.completeExceptionally(failure);
        }
    }
}
