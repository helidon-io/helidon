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
import java.util.Arrays;
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
    private static final int MAX_HINT = 1024 * 1024;
    private static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

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
    private byte[] stagingBuffer;

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
    public <T> T as(Class<T> type) {
        if (type != byte[].class) {
            return entity().as(type);
        }
        boolean directEligible = delegate == null
                && getClass() == Http3ClientResponseImpl.class && !entityRequested;
        var responseEntity = entity();
        if (!responseEntity.hasEntity()) {
            return responseEntity.as(type);
        }
        long hint;
        try {
            hint = headers().contentLength().orElse(-1);
        } catch (IllegalArgumentException _) {
            return responseEntity.as(type);
        }
        if (hint < 1 || hint > MAX_HINT) {
            return responseEntity.as(type);
        }
        if (directEligible) {
            return type.cast(readDestination(new DestinationInputStream(), (int) hint));
        }
        return type.cast(readDestination(responseEntity.inputStream(), (int) hint));
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
        stagingBuffer = null;
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

    private static byte[] readDestination(InputStream input, int hint) {
        byte[] result = new byte[hint];
        byte[] probe = null;
        int used = 0;
        try {
            for (;;) {
                if (used == result.length) {
                    if (probe == null) {
                        probe = new byte[1];
                    }
                    int read = input.read(probe, 0, 1);
                    if (read < 0) {
                        return result;
                    }
                    if (read == 0) {
                        continue;
                    }
                    result = Arrays.copyOf(result, grownCapacity(result.length));
                    result[used++] = probe[0];
                } else {
                    int read = input.read(result, used, Math.min(16 * 1024, result.length - used));
                    if (read < 0) {
                        return Arrays.copyOf(result, used);
                    }
                    used += read;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int grownCapacity(int current) {
        if (current >= MAX_ARRAY_LENGTH) {
            throw new OutOfMemoryError("Required response byte array exceeds maximum length");
        }
        return (int) Math.min((long) MAX_ARRAY_LENGTH, (long) current * 2);
    }

    private BufferData readBytes(int estimate) {
        try {
            int requested = estimate > 0 ? estimate : 16;
            byte[] buffer = stagingBuffer;
            if (buffer == null || buffer.length < requested) {
                buffer = new byte[requested];
                stagingBuffer = buffer;
            }
            int read = inputStream.read(buffer, 0, requested);
            if (read < 1) {
                stagingBuffer = null;
                return BufferData.empty();
            }
            if (read == buffer.length) {
                stagingBuffer = null;
                return BufferData.create(buffer, 0, read);
            }
            return BufferData.create(Arrays.copyOf(buffer, read));
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
        stagingBuffer = null;
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

    private final class DestinationInputStream extends InputStream {
        @Override
        public int read() {
            byte[] single = new byte[1];
            return read(single, 0, 1) < 0 ? -1 : single[0] & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            int read;
            try {
                read = inputStream.read(bytes, offset, length);
            } catch (IOException e) {
                UncheckedIOException failure = new UncheckedIOException(e);
                failRead(failure);
                throw failure;
            } catch (RuntimeException | Error failure) {
                failRead(failure);
                throw failure;
            }
            // Match RequestingInputStream: a zero supplier result ends the entity too.
            // EOF close errors belong to entity completion, outside readBytes' failure handling.
            if (read < 1) {
                Http3ClientResponseImpl.this.close();
                return -1;
            }
            return read;
        }
    }
}
