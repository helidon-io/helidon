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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.GenericType;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderValues;
import io.helidon.common.http.Http1HeadersParser;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.http.encoding.ContentDecoder;
import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.http.media.ReadableEntity;
import io.helidon.nima.http.media.ReadableEntityBase;
import io.helidon.nima.webclient.ClientConnection;
import io.helidon.nima.webclient.ClientResponseEntity;
import io.helidon.nima.webclient.http.spi.Source;
import io.helidon.nima.webclient.http.spi.SourceHandler;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;
import static java.nio.charset.StandardCharsets.US_ASCII;

class ClientResponseImpl implements Http1ClientResponse {
    private static final System.Logger LOGGER = System.getLogger(ClientResponseImpl.class.getName());

    private static final HelidonServiceLoader<SourceHandler> SOURCE_HANDLER_LOADER
            = HelidonServiceLoader.builder(ServiceLoader.load(SourceHandler.class)).build();

    private final AtomicBoolean closed = new AtomicBoolean();

    private final Http.Status responseStatus;
    private final ClientRequestHeaders requestHeaders;
    private final ClientResponseHeaders responseHeaders;
    private final DataReader reader;
    // todo configurable
    private final ContentEncodingContext encodingSupport = ContentEncodingContext.create();
    private final MediaContext mediaContext = MediaContext.create();
    private final String channelId;
    private final boolean hasTrailers;
    private final List<String> trailerNames;

    private ClientConnection connection;
    private long entityLength;
    private boolean entityFullyRead;
    private WritableHeaders<?> trailers;

    ClientResponseImpl(Http.Status responseStatus,
                       ClientRequestHeaders requestHeaders,
                       ClientResponseHeaders responseHeaders,
                       ClientConnection connection,
                       DataReader reader) {
        this.responseStatus = responseStatus;
        this.requestHeaders = requestHeaders;
        this.responseHeaders = responseHeaders;
        this.connection = connection;
        this.reader = reader;
        this.channelId = connection.channelId();

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
    public Headers headers() {
        return responseHeaders;
    }

    @Override
    public ReadableEntity entity() {
        return entity(requestHeaders, responseHeaders);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (headers().contains(HeaderValues.CONNECTION_CLOSE)) {
                connection.close();
            } else {
                if (entityFullyRead || entityLength == 0) {
                    if (hasTrailers) {
                        readTrailers();
                    }
                    connection.release();
                } else {
                    connection.close();
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Source<?>> void source(GenericType<T> sourceType, T source) {
        List<SourceHandler> providers = SOURCE_HANDLER_LOADER.asList();
        for (SourceHandler p : providers) {
            if (p.supports(sourceType, this)) {
                p.handle(source, this);
                return;
            }
        }
        throw new UnsupportedOperationException("No source available for " + sourceType);
    }

    private ReadableEntity entity(ClientRequestHeaders requestHeaders,
                                  ClientResponseHeaders responseHeaders) {
        ContentDecoder decoder;

        if (encodingSupport.contentDecodingEnabled()) {
            // there may be some decoder used
            if (responseHeaders.contains(Header.CONTENT_ENCODING)) {
                String contentEncoding = responseHeaders.get(Header.CONTENT_ENCODING).value();
                if (encodingSupport.contentDecodingSupported(contentEncoding)) {
                    decoder = encodingSupport.decoder(contentEncoding);
                } else {
                    throw new IllegalStateException("Unsupported content encoding: \n"
                                                            + BufferData.create(contentEncoding.getBytes(StandardCharsets.UTF_8))
                            .debugDataHex());
                }
            } else {
                decoder = ContentDecoder.NO_OP;
            }
        } else {
            // todo if validation of response enabled, check the content encoding and fail if present
            decoder = ContentDecoder.NO_OP;
        }
        if (responseHeaders.contains(Header.CONTENT_LENGTH)) {
            entityLength = responseHeaders.contentLength().getAsLong();
            if (entityLength == 0) {
                return ReadableEntityBase.empty();
            } else {
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, String.format("[%s] client read entity length %d", channelId, entityLength));
                }
                return ClientResponseEntity.create(decoder,
                                                   this::readEntity,
                                                   this::close,
                                                   requestHeaders,
                                                   responseHeaders,
                                                   mediaContext);
            }
        } else if (responseHeaders.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, String.format("[%s] client read entity chunked", channelId));
            }
            entityLength = -1;
            return ClientResponseEntity.create(decoder,
                                               this::readEntityChunked,
                                               this::close,
                                               requestHeaders,
                                               responseHeaders,
                                               mediaContext);
        }
        return ReadableEntityBase.empty();
    }

    private BufferData readEntityChunked(int estimate) {
        int endOfChunkSize = reader.findNewLine(256);
        if (endOfChunkSize == 256) {
            throw new IllegalStateException("Cannot read chunked entity, end of line not found within 256 bytes:\n"
                                                    + reader.readBuffer(Math.min(reader.available(), 256)));
        }
        String hex = reader.readAsciiString(endOfChunkSize);
        reader.skip(2); // CRLF
        int length;
        try {
            length = Integer.parseUnsignedInt(hex, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Chunk size is not a number:\n"
                                                       + BufferData.create(hex.getBytes(US_ASCII)).debugDataHex());
        }
        if (length == 0) {
            reader.skip(2); // second CRLF finishing the entity
            // todo support trailers?
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, String.format("[%s] read last (empty) chunk %n", channelId));
            }
            entityFullyRead = true;
            return null;
        }
        BufferData chunk = reader.readBuffer(length);
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, String.format("[%s] client read chunk %n%s", channelId, chunk.debugDataHex(true)));
        }

        reader.skip(2); // trailing CRLF after each chunk
        return chunk;
    }

    private BufferData readEntity(int estimate) {
        if (entityLength == 0) {
            // finished reading
            entityFullyRead = true;
            return null;
        }

        reader.ensureAvailable();
        int toRead = Math.min(estimate, reader.available());
        toRead = (int) Math.min(entityLength, toRead);
        entityLength -= toRead;
        // read between 0 and available bytes (or estimate, which is the number of requested bytes)
        BufferData buffer = reader.readBuffer(toRead);
        LOGGER.log(TRACE, String.format("[%s] client read entity buffer %n%s", channelId, buffer.debugDataHex(true)));
        return buffer;
    }

    private void readTrailers() {
        this.trailers = Http1HeadersParser.readHeaders(reader, 1024, true);
    }
}
