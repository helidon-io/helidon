/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.OptionalLong;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.GenericType;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.media.type.ParserMode;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
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
    private final Http1ClientProtocolConfig protocolConfig;
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
    private final LazyValue<Headers> trailers;
    private boolean entityRequested;
    private long entityLength;
    private boolean entityFullyRead = false;

    Http1ClientResponseImpl(HttpClientConfig clientConfig,
                            Http1ClientProtocolConfig protocolConfig,
                            Status responseStatus,
                            ClientRequestHeaders requestHeaders,
                            ClientResponseHeaders responseHeaders,
                            ClientConnection connection,
                            InputStream inputStream, // can be null if no entity
                            MediaContext mediaContext,
                            ClientUri lastEndpointUri,
                            CompletableFuture<Void> whenComplete) {
        this.clientConfig = clientConfig;
        this.protocolConfig = protocolConfig;
        this.responseStatus = responseStatus;
        this.requestHeaders = requestHeaders;
        this.responseHeaders = responseHeaders;
        this.connection = connection;
        this.inputStream = inputStream;
        this.mediaContext = mediaContext;
        this.parserMode = clientConfig.mediaTypeParserMode();
        this.lastEndpointUri = lastEndpointUri;
        this.whenComplete = whenComplete;
        this.trailers = LazyValue.create(() -> Http1HeadersParser.readHeaders(
                connection.reader(),
                protocolConfig.maxHeaderSize(),
                protocolConfig.validateResponseHeaders()
        ));

        OptionalLong contentLength = responseHeaders.contentLength();
        if (contentLength.isPresent()) {
            this.entityLength = contentLength.getAsLong();
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
            if (!this.entityRequested) {
                throw new IllegalStateException("Trailers requested before reading entity.");
            }
            return ClientResponseTrailers.create(this.trailers.get());
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
                    if (entityFullyRead || entityLength == 0 || consumeUnreadEntity()) {
                        connection.releaseResource();
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

    /**
     * Attempts to consume an unread entity for the purpose of re-using a cached
     * connection. Only works for length-prefixed responses and when the entity
     * has been loaded and has not been partially read. This method shall never
     * block on a read operation.
     *
     * @return {@code true} if consumed, {@code false} otherwise
     */
    private boolean consumeUnreadEntity() {
        if (entityLength == ENTITY_LENGTH_CHUNKED) {
            return false;
        }
        DataReader reader = connection.reader();
        if (reader.available() != entityLength) {
            return false;
        }
        try {
            for (long i = 0; i < entityLength; i++) {
                reader.read();
            }
            entityFullyRead = true;
            return true;
        } catch (RuntimeException e) {
            LOGGER.log(Level.DEBUG, "Exception while consuming entity", e);
            return false;
        }
    }

    private ReadableEntity entity(ClientRequestHeaders requestHeaders,
                                  ClientResponseHeaders responseHeaders) {
        if (inputStream == null) {
            return ReadableEntityBase.empty();
        }
        return ClientResponseEntity.create(
                this::readBytes,
                this::entityFullyRead,
                requestHeaders,
                responseHeaders,
                mediaContext
        );
    }

    private void entityFullyRead() {
        this.entityFullyRead = true;
        this.close();
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
