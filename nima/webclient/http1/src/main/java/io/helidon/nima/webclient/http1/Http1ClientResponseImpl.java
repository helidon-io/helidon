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

package io.helidon.nima.webclient.http1;

import java.io.InputStream;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.GenericType;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderValues;
import io.helidon.common.http.Http1HeadersParser;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.media.type.ParserMode;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.http.media.ReadableEntity;
import io.helidon.nima.http.media.ReadableEntityBase;
import io.helidon.nima.webclient.api.ClientConnection;
import io.helidon.nima.webclient.api.ClientResponseEntity;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.HttpClientConfig;
import io.helidon.nima.webclient.spi.Source;
import io.helidon.nima.webclient.spi.SourceHandlerProvider;

class Http1ClientResponseImpl implements Http1ClientResponse {
    private static final System.Logger LOGGER = System.getLogger(Http1ClientResponseImpl.class.getName());

    @SuppressWarnings("rawtypes")
    private static final List<SourceHandlerProvider> SOURCE_HANDLERS
            = HelidonServiceLoader.builder(ServiceLoader.load(SourceHandlerProvider.class)).build().asList();

    private final AtomicBoolean closed = new AtomicBoolean();

    private final Http.Status responseStatus;
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
    private long entityLength;
    private boolean entityFullyRead;
    private WritableHeaders<?> trailers;

    Http1ClientResponseImpl(HttpClientConfig clientConfig,
                            Http.Status responseStatus,
                            ClientRequestHeaders requestHeaders,
                            ClientResponseHeaders responseHeaders,
                            ClientConnection connection,
                            InputStream inputStream, // can be null if no entity
                            MediaContext mediaContext,
                            ParserMode parserMode,
                            ClientUri lastEndpointUri,
                            CompletableFuture<Void> whenComplete) {
        this.responseStatus = responseStatus;
        this.requestHeaders = requestHeaders;
        this.responseHeaders = responseHeaders;
        this.connection = connection;
        this.inputStream = inputStream;
        this.mediaContext = mediaContext;
        this.parserMode = parserMode;
        this.lastEndpointUri = lastEndpointUri;
        this.whenComplete = whenComplete;

        if (responseHeaders.contains(Header.CONTENT_LENGTH)) {
            this.entityLength = Long.parseLong(responseHeaders.get(Header.CONTENT_LENGTH).value());
        } else if (responseHeaders.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
            this.entityLength = -1;
        }
        if (responseHeaders.contains(Header.TRAILER)) {
            this.hasTrailers = true;
            this.trailerNames = responseHeaders.get(Header.TRAILER).allValues(true);
        } else {
            this.hasTrailers = false;
            this.trailerNames = List.of();
        }
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
        return entity(requestHeaders, responseHeaders, whenComplete);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (headers().contains(HeaderValues.CONNECTION_CLOSE)) {
                connection.closeResource();
            } else {
                if (entityFullyRead || entityLength == 0) {
                    if (hasTrailers) {
                        readTrailers();
                    }
                    connection.releaseResource();
                } else {
                    connection.closeResource();
                }
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
                                  ClientResponseHeaders responseHeaders,
                                  CompletableFuture<Void> whenComplete) {
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
        this.trailers = Http1HeadersParser.readHeaders(connection.reader(), 1024, true);
    }

    private BufferData readBytes(int estimate) {
        BufferData bufferData = BufferData.create(estimate);
        bufferData.readFrom(inputStream);

        return bufferData;
    }
}
