/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.lang.ref.ReferenceQueue;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import javax.net.ssl.SSLEngine;

import io.helidon.common.context.Context;
import io.helidon.common.http.Http;
import io.helidon.webserver.ByteBufRequestChunk.DataChunkHoldingQueue;
import io.helidon.webserver.ReferenceHoldingQueue.IndirectReference;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;

import static io.helidon.webserver.HttpInitializer.CLIENT_CERTIFICATE_NAME;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * ForwardingHandler bridges Netty response and request related APIs to
 * {@link BareRequest} and {@link BareResponse}.
 * <p>
 * For each tcp connection, a single {@link ForwardingHandler} is created.
 */
public class ForwardingHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger LOGGER = Logger.getLogger(ForwardingHandler.class.getName());
    private static final AtomicLong REQUEST_ID_GENERATOR = new AtomicLong(0);

    private final Routing routing;
    private final NettyWebServer webServer;
    private final SSLEngine sslEngine;
    private final ReferenceQueue<Object> queues;
    private final HttpRequestDecoder httpRequestDecoder;
    private final long maxPayloadSize;

    // this field is always accessed by the very same thread; as such, it doesn't need to be
    // concurrency aware
    private RequestContext requestContext;

    private boolean isWebSocketUpgrade;
    private long actualPayloadSize;
    private boolean ignorePayload;

    private CompletableFuture<ChannelFutureListener> requestDrained;
    private CompletableFuture<?> prevRequestFuture;
    private boolean lastContent;
    private boolean hadContentAlready;
    private final Runnable clearQueues;

    ForwardingHandler(Routing routing,
                      NettyWebServer webServer,
                      SSLEngine sslEngine,
                      ReferenceQueue<Object> queues,
                      Runnable clearQueues,
                      HttpRequestDecoder httpRequestDecoder,
                      long maxPayloadSize) {
        this.routing = routing;
        this.webServer = webServer;
        this.sslEngine = sslEngine;
        this.queues = queues;
        this.httpRequestDecoder = httpRequestDecoder;
        this.maxPayloadSize = maxPayloadSize;
        this.clearQueues = clearQueues;
    }

    private void reset() {
        lastContent = false;
        hadContentAlready = false;
        isWebSocketUpgrade = false;
        actualPayloadSize = 0L;
        ignorePayload = false;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();

        if (requestContext == null) {
            // there was no publisher associated with this connection
            // this happens in case there was no http request made on this connection

            // this also happens after LastHttpContent has been consumed by channelRead0
            if (lastContent) {
                // if the last thing that went through channelRead0 was LastHttpContent, then
                // there is no request handler that should be enforcing backpressure
                LOGGER.fine(() -> log("Read complete lastContent", ctx));
                ctx.channel().config().setAutoRead(true);
            } else {
                LOGGER.fine(() -> log("Read complete not lastContent", ctx));
            }
            return;
        }

        if (requestContext.hasRequests()) {
            LOGGER.fine(() -> log("Read complete has requests: %s", ctx, requestContext));
            ctx.channel().read();
        }
    }

    @Override
    @SuppressWarnings("checkstyle:methodlength")
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            hadContentAlready = false;
            LOGGER.fine(() -> log("Received HttpRequest: %s", ctx, System.identityHashCode(msg)));

            // On new request, use chance to cleanup queues in HttpInitializer
            clearQueues.run();

            // Turns off auto read
            ctx.channel().config().setAutoRead(false);

            // Reset internal state on new request
            reset();

            // Check that HTTP decoding was successful or return 400
            HttpRequest request = (HttpRequest) msg;
            try {
                checkDecoderResult(request);
            } catch (Throwable e) {
                send400BadRequest(ctx, e.getMessage());
                return;
            }

            // Certificate management
            request.headers().remove(Http.Header.X_HELIDON_CN);
            Optional.ofNullable(ctx.channel().attr(CLIENT_CERTIFICATE_NAME).get())
                    .ifPresent(name -> request.headers().set(Http.Header.X_HELIDON_CN, name));

            // Context, publisher and DataChunk queue for this request/response
            DataChunkHoldingQueue queue = new DataChunkHoldingQueue();
            HttpRequestScopedPublisher publisher = new HttpRequestScopedPublisher(queue);
            requestContext = new RequestContext(publisher, request, Context.create(webServer.context()));

            // Closure local variables that cache mutable instance variables
            RequestContext requestContextRef = requestContext;

            // Creates an indirect reference between publisher and queue so that when
            // publisher is ready for collection, we have access to queue by calling its
            // acquire method. We shall also attempt to release queue on completion of
            // bareResponse below.
            IndirectReference<HttpRequestScopedPublisher, DataChunkHoldingQueue> publisherRef =
                    new IndirectReference<>(publisher, queues, queue);

            // Set up read strategy for channel based on consumer demand
            publisher.onRequest((n, demand) -> {
                if (publisher.isUnbounded()) {
                    LOGGER.finest(() -> log("Netty autoread: true", ctx));
                    ctx.channel().config().setAutoRead(true);
                } else {
                    LOGGER.finest(() -> log("Netty autoread: false", ctx));
                    ctx.channel().config().setAutoRead(false);
                }

                if (publisher.hasRequests()) {
                    LOGGER.finest(() -> log("Requesting next chunks from Netty", ctx));
                    ctx.channel().read();
                } else {
                    LOGGER.finest(() -> log("No hook action required", ctx));
                }
            });

            // New request ID
            long requestId = REQUEST_ID_GENERATOR.incrementAndGet();

            // If a problem with the request URI, return 400 response
            BareRequestImpl bareRequest;
            try {
                bareRequest = new BareRequestImpl((HttpRequest) msg, publisher, webServer, ctx, sslEngine, requestId);
            } catch (IllegalArgumentException e) {
                send400BadRequest(ctx, e.getMessage());
                return;
            }

            // If context length is greater than maximum allowed, return 413 response
            if (maxPayloadSize >= 0) {
                String contentLength = request.headers().get(Http.Header.CONTENT_LENGTH);
                if (contentLength != null) {
                    try {
                        long value = Long.parseLong(contentLength);
                        if (value > maxPayloadSize) {
                            LOGGER.fine(() -> log("Payload length over max %d > %d", ctx, value, maxPayloadSize));
                            ignorePayload = true;
                            send413PayloadTooLarge(ctx);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        send400BadRequest(ctx, Http.Header.CONTENT_LENGTH + " header is invalid");
                        return;
                    }
                }
            }

            // If prev response is done, the next can start writing right away (HTTP pipelining)
            if (prevRequestFuture != null && prevRequestFuture.isDone()) {
                prevRequestFuture = null;
            }

            requestDrained = new CompletableFuture<>();
            // Create response and handler for its completion
            BareResponseImpl bareResponse =
                    new BareResponseImpl(ctx, request, publisher::isCompleted, publisher::hasRequests,
                                         prevRequestFuture, requestDrained, requestId);
            prevRequestFuture = new CompletableFuture<>();
            CompletableFuture<?> thisResp = prevRequestFuture;
            bareResponse.whenCompleted()
                        .thenRun(() -> {
                            // Mark response completed in context
                            requestContextRef.responseCompleted(true);

                            // Consume and release any buffers in publisher
                            publisher.clearAndRelease();

                            // Cleanup for these queues is done in HttpInitializer, but
                            // we try to do it here if possible to reduce memory usage,
                            // especially for keep-alive connections
                            if (queue.release()) {
                                publisherRef.acquire();      // clears reference to other
                            }

                            // Enables next response to proceed (HTTP pipelining)
                            thisResp.complete(null);

                            LOGGER.fine(() -> log("Response complete: %s", ctx, System.identityHashCode(msg)));
                        });
            if (HttpUtil.is100ContinueExpected(request)) {
                send100Continue(ctx);
            }

            // If a problem during routing, return 400 response
            try {
                requestContext.runInScope(() -> routing.route(bareRequest, bareResponse));
            } catch (IllegalArgumentException e) {
                send400BadRequest(ctx, e.getMessage());
                return;
            }

            // If WebSockets upgrade, re-arrange pipeline and drop HTTP decoder
            if (bareResponse.isWebSocketUpgrade()) {
                LOGGER.fine(() -> log("Replacing HttpRequestDecoder by WebSocketServerProtocolHandler", ctx));
                ctx.pipeline().replace(httpRequestDecoder, "webSocketsHandler",
                        new WebSocketServerProtocolHandler(bareRequest.uri().getPath(), null, true));
                removeHandshakeHandler(ctx);        // already done by Tyrus
                isWebSocketUpgrade = true;
                return;
            }
        }

        if (msg instanceof HttpContent) {
            LOGGER.fine(() -> log("Received HttpContent: %s", ctx, System.identityHashCode(msg)));

            if (requestContext == null) {
                throw new IllegalStateException("There is no request context associated with this http content. "
                                                + "This is never expected to happen!");
            }
            lastContent = false;

            HttpContent httpContent = (HttpContent) msg;

            ByteBuf content = httpContent.content();
            if (content.isReadable()) {
                HttpMethod method = requestContext.request().method();

                // compliance with RFC 7231
                if (HttpMethod.TRACE.equals(method)) {
                    // regarding the TRACE method, we're failing when payload is present only when the payload is actually
                    // consumed; if not, the request might proceed when payload is small enough
                    requestDrained.complete(ChannelFutureListener.CLOSE_ON_FAILURE);
                    LOGGER.finer(() -> log("Closing connection illegal payload; method: ", ctx, method));
                    throw new BadRequestException("It is illegal to send a payload with http method: " + method);
                }

                // compliance with RFC 7231
                if (requestContext.responseCompleted() && !(msg instanceof LastHttpContent)) {
                    // payload is not consumed and the response is already sent; we must close the connection
                    LOGGER.finer(() -> log("Closing connection unconsumed payload; method: ", ctx, method));
                    ctx.close();
                } else if (!ignorePayload) {
                    // Check payload size if a maximum has been set
                    if (maxPayloadSize >= 0) {
                        actualPayloadSize += content.readableBytes();
                        if (actualPayloadSize > maxPayloadSize) {
                            LOGGER.finer(() -> log("Chunked Payload over max %d > %d", ctx,
                                    actualPayloadSize, maxPayloadSize));
                            ignorePayload = true;
                            send413PayloadTooLarge(ctx);
                        } else {
                            requestContext.emit(content);
                        }
                    } else {
                        requestContext.emit(content);
                    }
                }
            }

            if (msg instanceof LastHttpContent) {
                LOGGER.fine(() -> log("Received LastHttpContent: %s", ctx, System.identityHashCode(msg)));

                if (!isWebSocketUpgrade) {
                    lastContent = true;
                    requestContext.complete();
                    requestContext = null; // just to be sure that current http req/res session doesn't interfere with other ones
                }
                requestDrained.complete(ChannelFutureListener.CLOSE_ON_FAILURE);
            } else if (!content.isReadable()) {
                // this is here to handle the case when the content is not readable but we didn't
                // exceptionally complete the publisher and close the connection
                throw new IllegalStateException("It is not expected to not have readable content.");
            } else if (!requestContext.hasRequests() && HttpUtil.isKeepAlive(requestContext.request())) {
                if (hadContentAlready) {
                    requestDrained.complete(ChannelFutureListener.CLOSE);
                } else {
                    //We are draining the entity, but we cannot be sure if connection should be closed or not.
                    //Next content has to be checked if it is last chunk. If not close connection.
                    hadContentAlready = true;
                    ctx.channel().read();
                }
            }
        }

        // We receive a raw bytebuf if connection was upgraded to WebSockets
        if (msg instanceof ByteBuf) {
            if (!isWebSocketUpgrade) {
                throw new IllegalStateException("Received ByteBuf without upgrading to WebSockets");
            }
            // Simply forward raw bytebuf to Tyrus for processing
            LOGGER.finest(() -> log("Received ByteBuf of WebSockets connection: %s", ctx, msg));
            requestContext.emit((ByteBuf) msg);
        }
    }

    /**
     * Overrides behavior when exception is thrown in pipeline.
     *
     * @param ctx channel context.
     * @param cause the throwable.
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.fine(() -> log("Exception caught: %s", ctx, cause.toString()));

        // We ignore stream resets (RST_STREAM) from HTTP/2
        if (cause instanceof Http2Exception.StreamException
                && ((Http2Exception.StreamException) cause).error() == Http2Error.CANCEL) {
            return;     // no action
        }

        // Otherwise, we fail publisher and close
        failPublisher(cause);
        ctx.close();
    }

    /**
     * Check that an HTTP message has been successfully decoded.
     *
     * @param request The HTTP request.
     */
    private void checkDecoderResult(HttpRequest request) {
        DecoderResult decoderResult = request.decoderResult();
        if (decoderResult.isFailure()) {
            LOGGER.info(() -> log("Request %s to %s rejected: %s", null,
                    request.method().asciiName(), request.uri(), decoderResult.cause().getMessage()));
            throw new BadRequestException(String.format("Request was rejected: %s", decoderResult.cause().getMessage()),
                    decoderResult.cause());
        }
    }

    /**
     * Find and remove the WebSockets handshake handler. Note that the handler's implementation
     * class is package private, so we look for it by name. Handshake is done in Helidon using
     * Tyrus' code instead of here.
     *
     * @param ctx Channel handler context.
     */
    private void removeHandshakeHandler(ChannelHandlerContext ctx) {
        ChannelHandler handshakeHandler = null;
        for (Iterator<Map.Entry<String, ChannelHandler>> it = ctx.pipeline().iterator(); it.hasNext();) {
            ChannelHandler handler = it.next().getValue();
            if (handler.getClass().getName().endsWith("WebSocketServerProtocolHandshakeHandler")) {
                handshakeHandler = handler;
                break;
            }
        }
        if (handshakeHandler != null) {
            ctx.pipeline().remove(handshakeHandler);
        } else {
            LOGGER.warning(() -> log("Unable to remove WebSockets handshake handler from pipeline", ctx));
        }
    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE);
        ctx.write(response);
    }

    /**
     * Returns a 400 (Bad Request) response with a message as content. Message is encoded using
     * HTML entities to prevent potential XSS attacks even if content type is text/plain.
     *
     * @param ctx Channel context.
     * @param message The message.
     */
    private void send400BadRequest(ChannelHandlerContext ctx, String message) {
        String encoded = HtmlEncoder.encode(message);
        byte[] entity = encoded.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST, Unpooled.wrappedBuffer(entity));
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, entity.length);
        response.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response)
                .addListener(future -> {
                    ctx.close();
                });
        failPublisher(new Error("400: Bad request"));
    }

    /**
     * Returns a 413 (Payload Too Large) response.
     *
     * @param ctx Channel context.
     */
    private void send413PayloadTooLarge(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, REQUEST_ENTITY_TOO_LARGE);
        ctx.writeAndFlush(response)
                .addListener(future -> {
                    ctx.close();
                });
        failPublisher(new Error("413: Payload is too large"));
    }

    /**
     * Informs publisher of failure.
     *
     * @param cause the cause.
     */
    private void failPublisher(Throwable cause) {
        if (requestContext != null) {
            requestContext.fail(cause);
        }
    }

    /**
     * Log message formatter for this class.
     *
     * @param template template suffix.
     * @param ctx channel context.
     * @param params template suffix params.
     * @return string to log.
     */
    private String log(String template, ChannelHandlerContext ctx, Object... params) {
        List<Object> list = new ArrayList<>(params.length + 2);
        list.add(System.identityHashCode(this));
        list.add(ctx != null ? System.identityHashCode(ctx.channel()) : "N/A");
        list.addAll(Arrays.asList(params));
        return String.format("[Handler: %s, Channel: %s] " + template, list.toArray());
    }
}
