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

package io.helidon.webclient.http1;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.GenericType;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.media.type.ParserMode;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Http1HeadersParser;
import io.helidon.http.Status;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.ReadableEntity;
import io.helidon.http.media.ReadableEntityBase;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientResponseEntity;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.spi.Source;
import io.helidon.webclient.spi.SourceHandlerProvider;

class Http1ClientResponseImpl implements Http1ClientResponse {
    private static final System.Logger LOGGER = System.getLogger(Http1ClientResponseImpl.class.getName());

    @SuppressWarnings("rawtypes")
    private static final List<SourceHandlerProvider> SOURCE_HANDLERS
            = HelidonServiceLoader.builder(ServiceLoader.load(SourceHandlerProvider.class)).build().asList();
    private static final long ENTITY_LENGTH_CHUNKED = -1;
    private final AtomicBoolean closed = new AtomicBoolean();

    private final HttpClientConfig clientConfig;
    private final Status responseStatus;
    private final ClientRequestHeaders requestHeaders;
    private final ClientResponseHeaders responseHeaders;
    private final InputStream inputStream;
    private final MediaContext mediaContext;
    private final CompletableFuture<Void> whenComplete;
    private final boolean hasTrailers;
    private final List<String> trailerNames;
    // Media type parsing mode configured on client.
    private final ParserMode parserMode;
    private final ClientUri lastEndpointUri;

    private final ClientConnection connection;
    private final CompletableFuture<io.helidon.http.Headers> trailers = new CompletableFuture<>();
    private boolean entityRequested;
    private long entityLength;

    Http1ClientResponseImpl(HttpClientConfig clientConfig,
                            Status responseStatus,
                            ClientRequestHeaders requestHeaders,
                            ClientResponseHeaders responseHeaders,
                            ClientConnection connection,
                            InputStream inputStream, // can be null if no entity
                            MediaContext mediaContext,
                            ParserMode parserMode,
                            ClientUri lastEndpointUri,
                            CompletableFuture<Void> whenComplete) {
        this.clientConfig = clientConfig;
        this.responseStatus = responseStatus;
        this.requestHeaders = requestHeaders;
        this.responseHeaders = responseHeaders;
        this.connection = connection;
        this.inputStream = inputStream;
        this.mediaContext = mediaContext;
        this.parserMode = parserMode;
        this.lastEndpointUri = lastEndpointUri;
        this.whenComplete = whenComplete;

        if (responseHeaders.contains(HeaderNames.CONTENT_LENGTH)) {
            this.entityLength = Long.parseLong(responseHeaders.get(HeaderNames.CONTENT_LENGTH).value());
        } else if (responseHeaders.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
            this.entityLength = ENTITY_LENGTH_CHUNKED;
        }
        if (responseHeaders.contains(HeaderNames.TRAILER)) {
            this.hasTrailers = true;
            this.trailerNames = responseHeaders.get(HeaderNames.TRAILER).allValues(true);
        } else {
            this.hasTrailers = false;
            this.trailerNames = List.of();
        }
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
        if (hasTrailers) {
            // Block until trailers arrive
            Duration timeout = clientConfig.readTimeout()
                    .orElseGet(() -> clientConfig.socketOptions().readTimeout());

            if (!this.entityRequested) {
                throw new IllegalStateException("Trailers requested before reading entity.");
            }

            try {
                return ClientResponseTrailers.create(this.trailers.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
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
        } else {
            return ClientResponseTrailers.create();
        }
    }

    @Override
    public ReadableEntity entity() {
        this.entityRequested = true;
        return entity(requestHeaders, responseHeaders);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                if (headers().contains(HeaderValues.CONNECTION_CLOSE)) {
                    connection.closeResource();
                } else {
                    if (entityLength == 0) {
                        connection.releaseResource();
                    } else if (entityLength == ENTITY_LENGTH_CHUNKED) {
                        if (hasTrailers) {
                            readTrailers();
                            connection.releaseResource();
                        } else {
                            connection.closeResource();
                        }
                    } else {
                        connection.closeResource();
                    }
                }
            } finally {
                whenComplete.complete(null);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Source<?>> void source(GenericType<T> sourceType, T source) {
        for (SourceHandlerProvider p : SOURCE_HANDLERS) {
            if (p.supports(sourceType, this)) {
                p.handle(source, this, mediaContext);
                return;
            }
        }
        throw new UnsupportedOperationException("No source available for " + sourceType);
    }

    @Override
    public ClientUri lastEndpointUri() {
        return lastEndpointUri;
    }

    ClientConnection connection() {
        return connection;
    }

    private ReadableEntity entity(ClientRequestHeaders requestHeaders,
                                  ClientResponseHeaders responseHeaders) {
        if (inputStream == null) {
            return ReadableEntityBase.empty();
        }
        return ClientResponseEntity.create(
                this::readBytes,
                this::close,
                requestHeaders,
                responseHeaders,
                mediaContext
        );
    }

    private void readTrailers() {
        this.trailers.complete(Http1HeadersParser.readHeaders(connection.reader(), 1024, true));
    }

    private BufferData readBytes(int estimate) {
        BufferData bufferData = BufferData.create(estimate);
        int bytesRead = bufferData.readFrom(inputStream);
        if (bytesRead == -1) {
            return null;
        }
        return bufferData;
    }
}
