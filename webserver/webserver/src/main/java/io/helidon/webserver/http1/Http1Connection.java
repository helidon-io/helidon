/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.http1;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import io.helidon.common.ParserHelper;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.mapper.MapperException;
import io.helidon.common.task.InterruptableTask;
import io.helidon.common.tls.TlsUtils;
import io.helidon.common.uri.UriValidator;
import io.helidon.http.BadRequestException;
import io.helidon.http.DateTime;
import io.helidon.http.DirectHandler;
import io.helidon.http.DirectHandler.EventType;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HtmlEncoder;
import io.helidon.http.HttpPrologue;
import io.helidon.http.InternalServerException;
import io.helidon.http.RequestException;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.service.registry.Services;
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ErrorHandling;
import io.helidon.webserver.ProxyProtocolData;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.http.DirectTransportRequest;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.spi.HttpLimitListenerProvider;
import io.helidon.webserver.http1.spi.Http1Upgrader;
import io.helidon.webserver.spi.ServerConnection;

import static io.helidon.http.HeaderNames.X_FORWARDED_FOR;
import static io.helidon.http.HeaderNames.X_FORWARDED_PORT;
import static io.helidon.http.HeaderNames.X_HELIDON_CN;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;

/**
 * HTTP/1.1 server connection.
 */
public class Http1Connection implements ServerConnection, InterruptableTask<Void> {
    static final byte[] CONTINUE_100 = "HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    private static final System.Logger LOGGER = System.getLogger(Http1Connection.class.getName());
    private static final Supplier<RequestException> INVALID_SIZE_EXCEPTION_SUPPLIER =
            () -> RequestException.builder()
                    .type(EventType.BAD_REQUEST)
                    .message("Chunk size is invalid")
                    .build();
    private final ConnectionContext ctx;
    private final Http1Config http1Config;
    private final DataWriter writer;
    private final DataReader reader;
    private final Map<String, Http1Upgrader> upgradeProviderMap;
    private final boolean canUpgrade;
    private final Http1Headers http1headers;
    private final Http1Prologue http1prologue;
    private final ContentEncodingContext contentEncodingContext;
    private final HttpRouting routing;
    private final long maxPayloadSize;
    private final Http1ConnectionListener recvListener;
    private final Http1ConnectionListener sendListener;

    private final List<HttpLimitListenerProvider> httpLimitListenerProviders;

    // overall connection
    private int requestId;
    private long currentEntitySize;
    private long currentEntitySizeRead;

    private volatile Thread myThread;
    private volatile boolean canRun = true;
    private volatile boolean currentlyReadingPrologue;
    private volatile ZonedDateTime lastRequestTimestamp;
    private volatile ServerConnection upgradeConnection;

    /**
     * Create a new connection.
     *
     * @param ctx                connection context
     * @param http1Config        connection provider configuration
     * @param upgradeProviderMap map of upgrade providers (protocol id to provider)
     */
    Http1Connection(ConnectionContext ctx,
                    Http1Config http1Config,
                    Map<String, Http1Upgrader> upgradeProviderMap) {
        this.ctx = ctx;
        this.writer = ctx.dataWriter();
        this.reader = ctx.dataReader();
        this.http1Config = http1Config;
        this.upgradeProviderMap = upgradeProviderMap;
        this.canUpgrade = !upgradeProviderMap.isEmpty();
        this.recvListener = http1Config.compositeReceiveListener();
        this.sendListener = http1Config.compositeSendListener();
        this.reader.listener(recvListener, ctx);
        this.http1headers = new Http1Headers(reader, http1Config.maxHeadersSize(), http1Config.validateRequestHeaders());
        this.http1prologue = new Http1Prologue(reader, http1Config.maxPrologueLength(), http1Config.validatePath());
        this.contentEncodingContext = ctx.listenerContext().contentEncodingContext();
        this.routing = ctx.router().routing(HttpRouting.class, HttpRouting.empty());
        this.maxPayloadSize = ctx.listenerContext().config().maxPayloadSize();
        this.lastRequestTimestamp = DateTime.timestamp();
        this.httpLimitListenerProviders = Services.all(HttpLimitListenerProvider.class);
    }

    @Override
    public boolean canInterrupt() {
        if (upgradeConnection == null) {
            return currentlyReadingPrologue;
        }
        return true;
    }

    @SuppressWarnings("removal")
    @Override
    public void handle(Limit limit) throws InterruptedException {
        this.myThread = Thread.currentThread();
        try {
            // look for protocol data
            ProxyProtocolData proxyProtocolData = ctx.proxyProtocolData().orElse(null);

            // handle connection until an exception (or explicit connection close)
            while (canRun) {
                // prologue (first line of request)
                currentlyReadingPrologue = true;
                HttpPrologue prologue = http1prologue.readPrologue();
                currentlyReadingPrologue = false;
                lastRequestTimestamp = DateTime.timestamp();
                recvListener.prologue(ctx, prologue);
                currentEntitySize = 0;
                currentEntitySizeRead = 0;

                if (http1Config.validatePrologue()) {
                    validatePrologue(prologue);
                }

                WritableHeaders<?> headers = http1headers.readHeaders(prologue);
                if (http1Config.validateRequestHeaders()) {
                    validateHostHeader(prologue, headers, http1Config.validateRequestHostHeader());
                }
                ctx.remotePeer().tlsCertificates()
                        .flatMap(TlsUtils::parseCn)
                        .ifPresent(name -> headers.set(X_HELIDON_CN, name));
                recvListener.headers(ctx, headers);

                // proxy protocol related headers X-Forwarded-For and X-Forwarded-Port
                if (proxyProtocolData != null) {
                    String sourceAddress = proxyProtocolData.sourceAddress();
                    if (!sourceAddress.isEmpty()) {
                        headers.add(X_FORWARDED_FOR, sourceAddress);
                    }
                    int sourcePort = proxyProtocolData.sourcePort();
                    if (sourcePort != -1) {
                        headers.add(X_FORWARDED_PORT, sourcePort);
                    }
                }

                if (canUpgrade && headers.contains(HeaderNames.UPGRADE)) {
                    if (!upgradeHasEntity(headers)) {
                        Http1Upgrader upgrader = upgradeProviderMap.get(headers.get(HeaderNames.UPGRADE).get());
                        if (upgrader != null) {
                            ServerConnection upgradeConnection = upgrader.upgrade(ctx, prologue, headers);
                            // upgrader may decide not to upgrade this connection
                            if (upgradeConnection != null) {
                                if (LOGGER.isLoggable(TRACE)) {
                                    LOGGER.log(TRACE, "Connection upgrade using " + upgradeConnection);
                                }
                                this.upgradeConnection = upgradeConnection;
                                // this will block until the connection terminates
                                upgradeConnection.handle(limit);
                                return;
                            }
                        }
                    } else {
                        ctx.log(LOGGER, DEBUG, "Protocol upgrade for a request with a payload ignored");
                    }
                }

                Optional<LimitAlgorithm.Token> token = limit.tryAcquire(httpLimitListenerProviders
                        .stream()
                        .map(f -> f.create(prologue, headers))
                        .toList());

                if (token.isEmpty()) {
                    ctx.log(LOGGER, TRACE, "Too many concurrent requests, rejecting request and closing connection.");
                    throw RequestException.builder()
                            .setKeepAlive(false)
                            .status(Status.SERVICE_UNAVAILABLE_503)
                            .type(EventType.OTHER)
                            .message("Too Many Concurrent Requests")
                            .build();
                } else {
                    LimitAlgorithm.Token permit = token.get();

                    try {
                        this.lastRequestTimestamp = DateTime.timestamp();
                        route(prologue, headers);
                        permit.success();
                        this.lastRequestTimestamp = DateTime.timestamp();
                    } catch (Throwable e) {
                        permit.dropped();
                        throw e;
                    }
                }
            }
        } catch (CloseConnectionException e) {
            throw e;
        } catch (BadRequestException e) {
            handleRequestException(RequestException.builder()
                                           .message(e.getMessage())
                                           .cause(e)
                                           .type(EventType.BAD_REQUEST)
                                           .status(e.status())
                                           .build());
        } catch (RequestException e) {
            handleRequestException(e);
        } catch (Throwable e) {
            handleRequestException(RequestException.builder()
                                           .message("Internal error")
                                           .type(EventType.INTERNAL_ERROR)
                                           .cause(e)
                                           .build());
        }
    }

    @SuppressWarnings("removal")
    @Override
    public void handle(Semaphore requestSemaphore) throws InterruptedException {
        handle(FixedLimit.create(requestSemaphore));
    }

    @Override
    public Duration idleTime() {
        if (upgradeConnection == null) {
            return Duration.between(lastRequestTimestamp, DateTime.timestamp());
        }
        return upgradeConnection.idleTime();
    }

    @Override
    public void close(boolean interrupt) {
        ctx.log(LOGGER, TRACE, "Requested connection close, interrupt: %s", interrupt);
        // either way, finish
        this.canRun = false;

        if (upgradeConnection == null) {
            if (interrupt) {
                // interrupt regardless of current state
                if (myThread != null) {
                    myThread.interrupt();
                }
            } else if (canInterrupt()) {
                // only interrupt when not processing a request (there is a chance of a race condition, this edge case
                // is ignored
                myThread.interrupt();
            }
        } else {
            upgradeConnection.close(interrupt);
        }
    }

    void reset() {
        currentEntitySize = 0;
        currentEntitySizeRead = 0;
    }

    /**
     * Only accept protocol upgrades if no entity is present. Otherwise, a successful
     * upgrade may result in the request entity interpreted as part of the new protocol
     * data, resulting in a failure.
     *
     * @param headers the HTTP headers in the prologue
     * @return whether to accept or reject the upgrade
     */
    static boolean upgradeHasEntity(WritableHeaders<?> headers) {
        return headers.contains(HeaderNames.CONTENT_LENGTH) && !headers.contains(HeaderValues.CONTENT_LENGTH_ZERO)
                || headers.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED);
    }

    static void validateHostHeader(HttpPrologue prologue, WritableHeaders<?> headers, boolean fullValidation) {
        if (fullValidation) {
            try {
                doValidateHostHeader(prologue, headers);
            } catch (IllegalArgumentException e) {
                throw RequestException.builder()
                        .type(EventType.BAD_REQUEST)
                        .status(Status.BAD_REQUEST_400)
                        .request(DirectTransportRequest.create(prologue, headers))
                        .setKeepAlive(false)
                        .message("Invalid Host header: " + e.getMessage())
                        .cause(e)
                        .build();
            }
        } else {
            simpleHostHeaderValidation(prologue, headers);
        }
    }

    private void validatePrologue(HttpPrologue prologue) {
        try {
            // scheme is not validated, as it is fixed and validated by the prologue reader
            UriValidator.validateQuery(prologue.query().rawValue());
            if (prologue.fragment().hasValue()) {
                UriValidator.validateFragment(prologue.fragment().rawValue());
            }
        } catch (IllegalArgumentException e) {
            throw RequestException.builder()
                    .type(EventType.BAD_REQUEST)
                    .status(Status.BAD_REQUEST_400)
                    .request(DirectTransportRequest.create(prologue, ServerRequestHeaders.create()))
                    .setKeepAlive(false)
                    .message(e.getMessage())
                    .safeMessage(true)
                    .cause(e)
                    .build();
        }
    }

    private static void simpleHostHeaderValidation(HttpPrologue prologue, WritableHeaders<?> headers) {
        if (headers.contains(HeaderNames.HOST)) {
            String host = headers.get(HeaderNames.HOST).get();
            // this is what is used to set up URI information, and this MUST work
            int index = host.lastIndexOf(':');
            if (index < 1) {
                return;
            }
            // this may still be an IPv6 address
            if (host.charAt(host.length() - 1) == ']') {
                // IP literal without port
                return;
            }

            try {
                // port must be parseable to int
                Integer.parseInt(host.substring(index + 1));
            } catch (NumberFormatException e) {
                throw RequestException.builder()
                        .type(EventType.BAD_REQUEST)
                        .status(Status.BAD_REQUEST_400)
                        .request(DirectTransportRequest.create(prologue, headers))
                        .setKeepAlive(false)
                        .message("Invalid port of the host header: " + HtmlEncoder.encode(host.substring(index + 1)))
                        .build();
            }

        }

    }

    private static void doValidateHostHeader(HttpPrologue prologue, WritableHeaders<?> headers) {
        List<String> hostHeaders = headers.all(HeaderNames.HOST, List::of);
        if (hostHeaders.isEmpty()) {
            throw RequestException.builder()
                    .type(EventType.BAD_REQUEST)
                    .status(Status.BAD_REQUEST_400)
                    .request(DirectTransportRequest.create(prologue, headers))
                    .setKeepAlive(false)
                    .message("Host header must be present in the request")
                    .build();
        }
        if (hostHeaders.size() > 1) {
            throw RequestException.builder()
                    .type(EventType.BAD_REQUEST)
                    .status(Status.BAD_REQUEST_400)
                    .request(DirectTransportRequest.create(prologue, headers))
                    .setKeepAlive(false)
                    .message("Only a single Host header is allowed in request")
                    .build();
        }
        String host = hostHeaders.getFirst();
        if (host.isEmpty()) {
            throw RequestException.builder()
                    .type(EventType.BAD_REQUEST)
                    .status(Status.BAD_REQUEST_400)
                    .request(DirectTransportRequest.create(prologue, headers))
                    .setKeepAlive(false)
                    .message("Host header must not be empty")
                    .build();
        }
        // now host and port must be valid
        int startLiteral = host.indexOf('[');
        int endLiteral = host.lastIndexOf(']');
        if (startLiteral == 0 && endLiteral == host.length() - 1) {
            // this is most likely an IPv6 address without a port
            UriValidator.validateIpLiteral(host);
            return;
        }
        if (startLiteral == 0 && endLiteral == -1) {
            UriValidator.validateIpLiteral(host);
            return;
        }
        int colon = host.lastIndexOf(':');
        if (colon == -1) {
            // only host
            UriValidator.validateNonIpLiteral(host);
            return;
        }

        String portString = host.substring(colon + 1);
        try {
            Integer.parseInt(portString);
        } catch (NumberFormatException e) {
            throw RequestException.builder()
                    .type(EventType.BAD_REQUEST)
                    .status(Status.BAD_REQUEST_400)
                    .request(DirectTransportRequest.create(prologue, headers))
                    .setKeepAlive(false)
                    .message("Invalid port of the host header: " + HtmlEncoder.encode(portString))
                    .build();
        }
        String hostString = host.substring(0, colon);
        // can be
        // IP-literal [..::]
        if (startLiteral == 0 && endLiteral == hostString.length() - 1) {
            UriValidator.validateIpLiteral(hostString);
            return;
        }

        UriValidator.validateNonIpLiteral(hostString);
    }

    private BufferData readEntityFromPipeline(HttpPrologue prologue, WritableHeaders<?> headers) {
        if (currentEntitySize == -1) {
            // chunked
            return readNextChunk(prologue, headers);
        } else {
            // length
            return readLengthEntity();
        }
    }

    private BufferData readNextChunk(HttpPrologue prologue, WritableHeaders<?> headers) {
        // chunk length processing
        String hex = reader.readLine();
        int chunkLength = ParserHelper.parseNonNegative(hex, 16, INVALID_SIZE_EXCEPTION_SUPPLIER);

        currentEntitySizeRead += chunkLength;
        if (maxPayloadSize != -1 && currentEntitySizeRead > maxPayloadSize) {
            throw RequestException.builder()
                    .type(EventType.BAD_REQUEST)
                    .status(Status.REQUEST_ENTITY_TOO_LARGE_413)
                    .request(DirectTransportRequest.create(prologue, headers))
                    .setKeepAlive(false)
                    .build();
        }
        // read chunk
        if (chunkLength == 0) {
            String end = reader.readLine();
            if (!end.isEmpty()) {
                throw RequestException.builder()
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

    private void route(HttpPrologue prologue, WritableHeaders<?> headers) {
        EntityStyle entity = EntityStyle.NONE;

        if (headers.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
            entity = EntityStyle.CHUNKED;
            this.currentEntitySize = -1;
        } else if (headers.contains(HeaderNames.CONTENT_LENGTH)) {
            try {
                this.currentEntitySize = headers.get(HeaderNames.CONTENT_LENGTH).get(long.class);
                if (maxPayloadSize != -1 && currentEntitySize > maxPayloadSize) {
                    throw RequestException.builder()
                            .type(EventType.BAD_REQUEST)
                            .status(Status.REQUEST_ENTITY_TOO_LARGE_413)
                            .request(DirectTransportRequest.create(prologue, headers))
                            .setKeepAlive(false)
                            .build();
                }
                entity = currentEntitySize == 0 ? EntityStyle.NONE : EntityStyle.LENGTH;
            } catch (MapperException e) {
                throw RequestException.builder()
                        .type(EventType.BAD_REQUEST)
                        .request(DirectTransportRequest.create(prologue, headers))
                        .message("Content length is not a number")
                        .cause(e)
                        .build();
            }
        }
        requestId++;

        if (entity == EntityStyle.NONE) {
            Http1ServerRequest request = Http1ServerRequest.create(ctx,
                                                                   routing.security(),
                                                                   prologue,
                                                                   headers,
                                                                   requestId);
            Http1ServerResponse response = new Http1ServerResponse(ctx,
                                                                   sendListener,
                                                                   writer,
                                                                   request,
                                                                   !headers.contains(HeaderValues.CONNECTION_CLOSE),
                                                                   http1Config.validateResponseHeaders());

            routing.route(ctx, request, response);
            // we have handled a request without request entity
            return;
        }

        boolean expectContinue = false;

        // Expect: 100-continue
        if (headers.contains(HeaderValues.EXPECT_100)) {
            if (this.http1Config.continueImmediately()) {
                try {
                    writer.writeNow(BufferData.create(CONTINUE_100));
                } catch (UncheckedIOException e) {
                    throw new ServerConnectionException("Failed to write continue", e);
                }
            }
            expectContinue = true;
        }

        ContentDecoder decoder;
        if (contentEncodingContext.contentDecodingEnabled()) {
            // there may be some decoder used
            if (headers.contains(HeaderNames.CONTENT_ENCODING)) {
                String contentEncoding = headers.get(HeaderNames.CONTENT_ENCODING).get();
                if (contentEncodingContext.contentDecodingSupported(contentEncoding)) {
                    decoder = contentEncodingContext.decoder(contentEncoding);
                } else {
                    throw RequestException.builder()
                            .type(EventType.BAD_REQUEST)
                            .request(DirectTransportRequest.create(prologue, headers))
                            .message("Unsupported content encoding")
                            .build();
                }
            } else {
                decoder = ContentDecoder.NO_OP;
            }
        } else {
            // Check whether Content-Encoding header is present when headers validation is enabled
            if (http1Config.validateRequestHeaders() && headers.contains(HeaderNames.CONTENT_ENCODING)) {
                throw RequestException.builder()
                        .type(EventType.BAD_REQUEST)
                        .request(DirectTransportRequest.create(prologue, headers))
                        .message("Content-Encoding header present when content encoding is disabled")
                        .build();
            }
            decoder = ContentDecoder.NO_OP;
        }

        CountDownLatch entityReadLatch = new CountDownLatch(1);
        Http1ServerRequest request = Http1ServerRequest.create(ctx,
                                                               this,
                                                               http1Config,
                                                               routing.security(),
                                                               prologue,
                                                               ServerRequestHeaders.create(headers),
                                                               decoder,
                                                               requestId,
                                                               expectContinue,
                                                               entityReadLatch,
                                                               () -> this.readEntityFromPipeline(prologue, headers));
        Http1ServerResponse response = new Http1ServerResponse(ctx,
                                                               sendListener,
                                                               writer,
                                                               request,
                                                               !request.headers()
                                                                       .contains(HeaderValues.CONNECTION_CLOSE),
                                                               http1Config.validateResponseHeaders());

        routing.route(ctx, request, response);

        consumeEntity(request, response, entityReadLatch);
        try {
            entityReadLatch.await();
        } catch (InterruptedException e) {
            throw RequestException.builder()
                    .type(EventType.INTERNAL_ERROR)
                    .request(DirectTransportRequest.create(prologue, headers))
                    .message("Failed to wait for pipeline")
                    .cause(e)
                    .build();
        }
    }

    private void consumeEntity(Http1ServerRequest request, Http1ServerResponse response, CountDownLatch entityReadLatch) {
        if (response.headers().contains(HeaderValues.CONNECTION_CLOSE) || request.content().consumed()) {
            // we do not care about request entity if connection is getting closed
            entityReadLatch.countDown();
            return;
        }
        // consume the entity if not consumed by routing
        try {
            request.content().consume();
        } catch (Exception e) {
            boolean keepAlive = request.content().consumed() && response.headers().contains(HeaderValues.CONNECTION_KEEP_ALIVE);
            // we must close connection, as we could not consume request
            if (!response.isSent()) {
                throw new InternalServerException(e.getMessage(), e, keepAlive);
            }
            throw new CloseConnectionException("Failed to consume request entity, must close", e);
        }
    }

    private void handleRequestException(RequestException e) {
        // gather error handling properties
        ErrorHandling errorHandling = ctx.listenerContext()
                .config()
                .errorHandling();

        // log message in DEBUG mode
        if (LOGGER.isLoggable(DEBUG) && (e.safeMessage() || errorHandling.logAllMessages())) {
            LOGGER.log(DEBUG, e);
        }

        // create message to return based on settings
        String message = null;
        if (errorHandling.includeEntity()) {
            message = e.safeMessage() ? e.getMessage() : "Bad request, see server log for more information";
        }

        // handle exception condition using direct handlers
        DirectHandler handler = ctx.listenerContext()
                .directHandlers()
                .handler(e.eventType());
        DirectHandler.TransportResponse response = handler.handle(e.request(),
                                                                  e.eventType(),
                                                                  e.status(),
                                                                  e.responseHeaders(),
                                                                  message);

        // write response
        BufferData buffer = BufferData.growing(128);
        ServerResponseHeaders headers = response.headers();

        // we are escaping the connection loop, the connection will be closed
        headers.set(HeaderValues.CONNECTION_CLOSE);

        byte[] entity = response.entity().orElse(BufferData.EMPTY_BYTES);
        headers.set(HeaderValues.create(HeaderNames.CONTENT_LENGTH, String.valueOf(entity.length)));

        Http1ServerResponse.nonEntityBytes(headers, response.status(), buffer, response.keepAlive(),
                                           http1Config.validateResponseHeaders());
        if (entity.length != 0) {
            buffer.write(entity);
        }

        sendListener.status(ctx, response.status());
        sendListener.headers(ctx, headers);
        sendListener.data(ctx, buffer);
        try {
            writer.write(buffer);
        } catch (UncheckedIOException uioe) {
            throw new ServerConnectionException("Failed to write request exception", uioe);
        }

        if (response.status() == Status.INTERNAL_SERVER_ERROR_500) {
            LOGGER.log(WARNING, "Internal server error", e);
        }
    }
}
