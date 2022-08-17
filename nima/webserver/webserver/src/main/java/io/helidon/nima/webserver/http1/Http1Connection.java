/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.http1;

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.HeadersServerRequest;
import io.helidon.common.http.HeadersServerResponse;
import io.helidon.common.http.HeadersWritable;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.HeaderValues;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.mapper.MapperException;
import io.helidon.nima.http.encoding.ContentDecoder;
import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.webserver.CloseConnectionException;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.SimpleHandler;
import io.helidon.nima.webserver.SimpleHandler.EventType;
import io.helidon.nima.webserver.http.HttpException;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http.HttpSimpleRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.nima.webserver.http1.spi.Http1UpgradeProvider;
import io.helidon.nima.webserver.spi.ServerConnection;

import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;

/**
 * HTTP/1.1 server connection.
 */
public class Http1Connection implements ServerConnection {
    private static final System.Logger LOGGER = System.getLogger(Http1Connection.class.getName());

    private final ConnectionContext ctx;
    private final DataWriter writer;
    private final DataReader reader;
    private final Http1ConnectionListener recvListener;
    private final Map<String, Http1UpgradeProvider> upgradeProviderMap;
    private final boolean canUpgrade;
    private final Http1Headers http1headers;
    private final Http1Prologue http1prologue;
    // todo pass from server config
    private final ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();
    private final Http1ConnectionListener sendListener;
    private final HttpRouting routing;
    private final long maxPayloadSize;

    // overall connection
    private int requestId;
    private long currentEntitySize;
    private long currentEntitySizeRead;

    /**
     * Create a new connection.
     *
     * @param ctx                connection context
     * @param recvListener       receive listener to get events for incoming traffic
     * @param sendListener       send listener to get events for outgoing traffic
     * @param maxPrologueLength  maximal size of prologue (initial line)
     * @param maxHeadersSize     maximal size of headers in bytes
     * @param validateHeaders    whether to validate request headers
     * @param validatePath       whether to validate path
     * @param upgradeProviderMap map of upgrade providers (protocol id to provider)
     */
    public Http1Connection(ConnectionContext ctx,
                           Http1ConnectionListener recvListener,
                           Http1ConnectionListener sendListener,
                           int maxPrologueLength,
                           int maxHeadersSize,
                           boolean validateHeaders,
                           boolean validatePath,
                           Map<String, Http1UpgradeProvider> upgradeProviderMap) {
        this.ctx = ctx;
        this.writer = ctx.dataWriter();
        this.reader = ctx.dataReader();
        this.sendListener = sendListener;
        this.recvListener = recvListener;
        this.upgradeProviderMap = upgradeProviderMap;
        this.canUpgrade = !upgradeProviderMap.isEmpty();
        this.reader.listener(recvListener, ctx);
        this.http1headers = new Http1Headers(reader, maxHeadersSize, validateHeaders);
        this.http1prologue = new Http1Prologue(reader, maxPrologueLength, validatePath);
        this.routing = ctx.router().routing(HttpRouting.class, HttpRouting.empty());
        this.maxPayloadSize = ctx.maxPayloadSize();
    }

    @Override
    public void handle() throws InterruptedException {
        try {
            try {
                doHandle();
            } catch (CloseConnectionException | HttpException | UncheckedIOException e) {
                throw e;
            } catch (Throwable e) {
                throw HttpException.builder()
                        .message("Internal error")
                        .type(EventType.INTERNAL_ERROR)
                        .cause(e)
                        .build();
            }
        } catch (HttpException e) {
            SimpleHandler handler = ctx.simpleHandlers().handler(e.eventType());
            SimpleHandler.SimpleResponse response = handler.handle(e.request(),
                                                                   e.eventType(),
                                                                   e.status(),
                                                                   e.responseHeaders(),
                                                                   e);

            Http.Status usedStatus;

            Optional<ServerResponse> fullResponse = e.fullResponse();
            if (fullResponse.isPresent()) {
                ServerResponse res = fullResponse.get();
                res.status(response.status());
                response.headers()
                        .forEach(res::header);
                if (!e.keepAlive()) {
                    res.header(HeaderValues.CONNECTION_CLOSE);
                }

                if (res.isSent()) {
                    throw new CloseConnectionException(
                            "Cannot send response of a simple handler, status and headers already written");
                }

                try {
                    response.message().ifPresentOrElse(res::send, res::send);
                } catch (IllegalStateException ex) {
                    // failed to send - probably output stream was already obtained and used, so status is written
                    // we can only close the connection now
                    res.streamResult(response.message().map(String::new).orElseGet(() -> e.getCause().getMessage()));
                    throw new CloseConnectionException(
                            "Cannot send response of a simple handler, status and headers already written",
                            ex);
                }
                usedStatus = response.status();
            } else {
                BufferData buffer = BufferData.growing(128);
                HeadersServerResponse headers = response.headers();
                if (!e.keepAlive()) {
                    headers.set(HeaderValues.CONNECTION_CLOSE);
                }
                byte[] message = response.message().orElse(BufferData.EMPTY_BYTES);
                if (message.length != 0) {
                    headers.set(Http.HeaderValue.create(Http.Header.CONTENT_LENGTH, String.valueOf(message.length)));
                }
                Http1ServerResponse.nonEntityBytes(headers, response.status(), buffer, response.keepAlive());
                if (message.length != 0) {
                    buffer.write(message);
                }

                writer.write(buffer);
                usedStatus = response.status();
            }

            if (usedStatus == Http.Status.INTERNAL_SERVER_ERROR_500) {
                LOGGER.log(WARNING, "Internal server error", e);
            }
        }
    }

    private void doHandle() throws InterruptedException {
        // handle connection until an exception (or explicit connection close)
        while (true) {
            // prologue (first line of request)
            HttpPrologue prologue = http1prologue.readPrologue();
            recvListener.prologue(ctx, prologue);
            currentEntitySize = 0;
            currentEntitySizeRead = 0;

            HeadersWritable<?> headers = http1headers.readHeaders(prologue);
            recvListener.headers(ctx, headers);

            if (canUpgrade) {
                if (headers.contains(Http.Header.UPGRADE)) {
                    Http1UpgradeProvider upgrader = upgradeProviderMap.get(headers.get(Http.Header.UPGRADE).value());
                    if (upgrader != null) {
                        ServerConnection upgradeConnection = upgrader.upgrade(ctx, prologue, headers);
                        // upgrader may decide not to upgrade this connection
                        if (upgradeConnection != null) {
                            if (LOGGER.isLoggable(TRACE)) {
                                LOGGER.log(TRACE, "Connection upgrade using " + upgradeConnection);
                            }
                            // this will block until the connection terminates
                            upgradeConnection.handle();
                            return;
                        }
                    }
                }
            }
            route(prologue, headers);
        }
    }

    private BufferData readEntityFromPipeline(HttpPrologue prologue, HeadersWritable<?> headers) {
        if (currentEntitySize == -1) {
            // chunked
            return readNextChunk(prologue, headers);
        } else {
            // length
            return readLengthEntity();
        }
    }

    private BufferData readNextChunk(HttpPrologue prologue, HeadersWritable<?> headers) {
        // chunk length processing
        String hex = reader.readLine();
        int chunkLength = Integer.parseUnsignedInt(hex, 16);

        currentEntitySizeRead += chunkLength;
        if (maxPayloadSize != -1 && currentEntitySizeRead > maxPayloadSize) {
            throw HttpException.builder()
                    .type(EventType.BAD_REQUEST)
                    .status(Http.Status.REQUEST_ENTITY_TOO_LARGE_413)
                    .request(HttpSimpleRequest.create(prologue, headers))
                    .setKeepAlive(false)
                    .build();
        }
        // read chunk
        if (chunkLength == 0) {
            String end = reader.readLine();
            if (!end.isEmpty()) {
                throw HttpException.builder()
                        .type(EventType.BAD_REQUEST)
                        .message("Invalid terminating chunk")
                        .build();
            }
            return null;
        }
        BufferData nextChunkData = reader.readBuffer(chunkLength);
        reader.skip(2); // skip \r\n after the chunk
        return nextChunkData;
    }

    private BufferData readLengthEntity() {
        long stillNeed = currentEntitySize - currentEntitySizeRead;
        if (stillNeed == 0) {
            return null;
        }

        reader.ensureAvailable();
        int toRead = (int) Math.min(reader.available(), stillNeed);
        BufferData buffer = reader.readBuffer(toRead);
        this.currentEntitySizeRead += toRead;
        return buffer;
    }

    private void route(HttpPrologue prologue, HeadersWritable<?> headers) {
        EntityStyle entity = EntityStyle.NONE;

        if (headers.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
            entity = EntityStyle.CHUNKED;
            this.currentEntitySize = -1;
        } else if (headers.contains(Http.Header.CONTENT_LENGTH)) {
            try {
                this.currentEntitySize = headers.get(Http.Header.CONTENT_LENGTH).value(long.class);
                if (maxPayloadSize != -1 && currentEntitySize > maxPayloadSize) {
                    throw HttpException.builder()
                            .type(EventType.BAD_REQUEST)
                            .status(Http.Status.REQUEST_ENTITY_TOO_LARGE_413)
                            .request(HttpSimpleRequest.create(prologue, headers))
                            .setKeepAlive(false)
                            .build();
                }
                entity = currentEntitySize == 0 ? EntityStyle.NONE : EntityStyle.LENGTH;
            } catch (MapperException e) {
                throw HttpException.builder()
                        .type(EventType.BAD_REQUEST)
                        .request(HttpSimpleRequest.create(prologue, headers))
                        .message("Content length is not a number")
                        .cause(e)
                        .build();
            }
        }
        requestId++;

        if (entity == EntityStyle.NONE) {
            Http1ServerRequest request = Http1ServerRequest.create(ctx, prologue, headers, requestId);
            Http1ServerResponse response = new Http1ServerResponse(ctx,
                                                                   sendListener,
                                                                   writer,
                                                                   request,
                                                                   !request.headers()
                                                                           .contains(HeaderValues.CONNECTION_CLOSE));

            routing.route(ctx, request, response);
            // we have handled a request without request entity
            return;
        }

        // todo we may want to send continue only when we find a route - this is probably too early
        // if we do not find a route, we should just return (maybe even wait for user to actually request the entity)
        // Expect: 100-continue
        if (headers.contains(Http.Header.EXPECT)) {
            if (headers.contains(HeaderValues.EXPECT_100)) {
                writer.write(BufferData.create("HTTP/1.1 100 Continue\r\n"));
            } else {
                writer.write(BufferData.create("HTTP/1.1 417 Unsupported-Expect\r\n"));
                // TODO and terminate the connection?
            }
        }

        ContentDecoder decoder;
        if (contentEncodingContext.contentDecodingEnabled()) {
            // there may be some decoder used
            if (headers.contains(Http.Header.CONTENT_ENCODING)) {
                String contentEncoding = headers.get(Http.Header.CONTENT_ENCODING).value();
                if (contentEncodingContext.contentDecodingSupported(contentEncoding)) {
                    decoder = contentEncodingContext.decoder(contentEncoding);
                } else {
                    throw HttpException.builder()
                            .type(EventType.BAD_REQUEST)
                            .request(HttpSimpleRequest.create(prologue, headers))
                            .message("Unsupported content encoding")
                            .build();
                }
            } else {
                decoder = ContentDecoder.NO_OP;
            }
        } else {
            // todo if validation of request enabled, check the content encoding and fail if present
            decoder = ContentDecoder.NO_OP;
        }

        CountDownLatch entityReadLatch = new CountDownLatch(1);
        Http1ServerRequest request = Http1ServerRequest.create(ctx,
                                                               prologue,
                                                               HeadersServerRequest.create(headers),
                                                               decoder,
                                                               requestId,
                                                               entityReadLatch,
                                                               () -> this.readEntityFromPipeline(prologue, headers));
        Http1ServerResponse response = new Http1ServerResponse(ctx,
                                                               sendListener,
                                                               writer,
                                                               request,
                                                               !request.headers()
                                                                       .contains(HeaderValues.CONNECTION_CLOSE));

        routing.route(ctx, request, response);

        consumeEntity(request, response);
        try {
            entityReadLatch.await();
        } catch (InterruptedException e) {
            throw HttpException.builder()
                    .type(EventType.INTERNAL_ERROR)
                    .request(HttpSimpleRequest.create(prologue, headers))
                    .message("Failed to wait for pipeline")
                    .cause(e)
                    .build();
        }
    }

    private void consumeEntity(Http1ServerRequest request, Http1ServerResponse response) {
        if (response.headers().contains(HeaderValues.CONNECTION_CLOSE) || request.content().consumed()) {
            // we do not care about request entity if connection is getting closed
            return;
        }
        // consume the entity if not consumed by routing
        try {
            request.content().consume();
        } catch (Exception e) {
            boolean keepAlive = request.content().consumed() && response.headers().contains(HeaderValues.CONNECTION_KEEP_ALIVE);
            // we must close connection, as we could not consume request
            if (!response.isSent()) {
                throw HttpException.builder()
                        .type(EventType.INTERNAL_ERROR)
                        .cause(e)
                        .request(request)
                        .response(response)
                        .message(e.getMessage())
                        .setKeepAlive(keepAlive)
                        .build();
            }
            throw new CloseConnectionException("Failed to consume request entity, must close", e);
        }
    }
}
