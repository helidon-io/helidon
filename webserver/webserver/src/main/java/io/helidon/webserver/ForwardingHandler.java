/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import javax.net.ssl.SSLEngine;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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

import static io.helidon.webserver.HttpInitializer.CERTIFICATE_NAME;
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
    private final Queue<ReferenceHoldingQueue<DataChunk>> queues;
    private final HttpRequestDecoder httpRequestDecoder;
    private final long maxPayloadSize;

    // this field is always accessed by the very same thread; as such, it doesn't need to be
    // concurrency aware
    private RequestContext requestContext;

    private boolean isWebSocketUpgrade;
    private long actualPayloadSize;
    private boolean ignorePayload;

    private CompletableFuture prev;
    private boolean lastContent;

    ForwardingHandler(Routing routing,
                      NettyWebServer webServer,
                      SSLEngine sslEngine,
                      Queue<ReferenceHoldingQueue<DataChunk>> queues,
                      HttpRequestDecoder httpRequestDecoder,
                      long maxPayloadSize) {
        this.routing = routing;
        this.webServer = webServer;
        this.sslEngine = sslEngine;
        this.queues = queues;
        this.httpRequestDecoder = httpRequestDecoder;
        this.maxPayloadSize = maxPayloadSize;
    }

    private void reset() {
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
                ctx.channel().config().setAutoRead(true);
            }
            return;
        }

        if (requestContext.publisher().hasRequests()) {
            ctx.channel().read();
        }
    }

    @Override
    @SuppressWarnings("checkstyle:methodlength")
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        LOGGER.fine(() -> String.format("[Handler: %s, Channel: %s] Received object: %s",
                System.identityHashCode(this), System.identityHashCode(ctx.channel()), msg.getClass()));

        if (msg instanceof HttpRequest) {
            lastContent = false;
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
            Optional.ofNullable(ctx.channel().attr(CERTIFICATE_NAME).get())
                    .ifPresent(name -> request.headers().set(Http.Header.X_HELIDON_CN, name));

            // Queue, context and publisher creation
            ReferenceHoldingQueue<DataChunk> queue = new ReferenceHoldingQueue<>();
            queues.add(queue);
            final RequestContext requestContext = new RequestContext(new HttpRequestScopedPublisher(ctx, queue), request);
            this.requestContext = requestContext;

            // the only reason we have the 'ref' here is that the field might get assigned with null
            final HttpRequestScopedPublisher publisherRef = requestContext.publisher();
            long requestId = REQUEST_ID_GENERATOR.incrementAndGet();

            // If a problem with the request URI, return 400 response
            BareRequestImpl bareRequest;
            try {
                bareRequest = new BareRequestImpl((HttpRequest) msg, requestContext.publisher(),
                        webServer, ctx, sslEngine, requestId);
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
                            LOGGER.fine(() -> String.format("[Handler: %s, Channel: %s] Payload length over max %d > %d",
                                    System.identityHashCode(this), System.identityHashCode(ctx.channel()),
                                    value, maxPayloadSize));
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

            if (prev != null && prev.isDone()) {
                prev = null;
            }

            // Create response and handler for its completion
            BareResponseImpl bareResponse =
                    new BareResponseImpl(ctx, request, publisherRef::isCompleted, prev, Thread.currentThread(), requestId);
            prev = new CompletableFuture();

            final CompletableFuture thisResp = prev;

            bareResponse.whenCompleted()
                        .thenRun(() -> {
                            if (requestContext != null) {
                                requestContext.responseCompleted(true);
                            }

                            // Cleanup for these queues is done in HttpInitializer, but
                            // we try to do it here if possible to reduce memory usage,
                            // especially for keep-alive connections
                            if (queue.release()) {
                                queues.remove(queue);
                            }
                            publisherRef.clearBuffer(DataChunk::release);

                            thisResp.complete(null);
                        });
            if (HttpUtil.is100ContinueExpected(request)) {
                send100Continue(ctx);
            }

            // If a problem during routing, return 400 response
            try {
                routing.route(bareRequest, bareResponse);
            } catch (IllegalArgumentException e) {
                send400BadRequest(ctx, e.getMessage());
                return;
            }

            // If WebSockets upgrade, re-arrange pipeline and drop HTTP decoder
            if (bareResponse.isWebSocketUpgrade()) {
                LOGGER.fine("Replacing HttpRequestDecoder by WebSocketServerProtocolHandler");
                ctx.pipeline().replace(httpRequestDecoder, "webSocketsHandler",
                        new WebSocketServerProtocolHandler(bareRequest.uri().getPath(), null, true));
                removeHandshakeHandler(ctx);        // already done by Tyrus
                isWebSocketUpgrade = true;
                return;
            }
        }

        if (msg instanceof HttpContent) {
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
                    LOGGER.finer(() -> "Closing connection because of an illegal payload; method: " + method);
                    throw new BadRequestException("It is illegal to send a payload with http method: " + method);
                }

                // compliance with RFC 7231
                if (requestContext.responseCompleted() && !(msg instanceof LastHttpContent)) {
                    // payload is not consumed and the response is already sent; we must close the connection
                    LOGGER.finer(() -> "Closing connection because request payload was not consumed; method: " + method);
                    ctx.close();
                } else if (!ignorePayload) {
                    // Check payload size if a maximum has been set
                    if (maxPayloadSize >= 0) {
                        actualPayloadSize += content.readableBytes();
                        if (actualPayloadSize > maxPayloadSize) {
                            LOGGER.fine(() -> String.format("[Handler: %s, Channel: %s] Chunked Payload over max %d > %d",
                                    System.identityHashCode(this), System.identityHashCode(ctx.channel()),
                                    actualPayloadSize, maxPayloadSize));
                            ignorePayload = true;
                            send413PayloadTooLarge(ctx);
                        } else {
                            requestContext.publisher().emit(content);
                        }
                    } else {
                        requestContext.publisher().emit(content);
                    }
                }
            }

            if (msg instanceof LastHttpContent) {
                if (!isWebSocketUpgrade) {
                    lastContent = true;
                    requestContext.publisher().complete();
                    requestContext = null; // just to be sure that current http req/res session doesn't interfere with other ones
                }
            } else if (!content.isReadable()) {
                // this is here to handle the case when the content is not readable but we didn't
                // exceptionally complete the publisher and close the connection
                throw new IllegalStateException("It is not expected to not have readable content.");
            }
        }

        // We receive a raw bytebuf if connection was upgraded to WebSockets
        if (msg instanceof ByteBuf) {
            if (!isWebSocketUpgrade) {
                throw new IllegalStateException("Received ByteBuf without upgrading to WebSockets");
            }
            // Simply forward raw bytebuf to Tyrus for processing
            LOGGER.finest(() -> "Received ByteBuf of WebSockets connection" + msg);
            requestContext.publisher().emit((ByteBuf) msg);
        }
    }

    /**
     * Check that an HTTP message has been successfully decoded.
     *
     * @param request The HTTP request.
     */
    private static void checkDecoderResult(HttpRequest request) {
        DecoderResult decoderResult = request.decoderResult();
        if (decoderResult.isFailure()) {
            LOGGER.info(String.format("Request %s to %s rejected: %s", request.method()
                            .asciiName(), request.uri(), decoderResult.cause().getMessage()));
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
    private static void removeHandshakeHandler(ChannelHandlerContext ctx) {
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
            LOGGER.warning("Unable to remove WebSockets handshake handler from pipeline");
        }
    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE);
        ctx.write(response);
    }

    /**
     * Returns a 400 (Bad Request) response with a message as content.
     *
     * @param ctx Channel context.
     * @param message The message.
     */
    private static void send400BadRequest(ChannelHandlerContext ctx, String message) {
        byte[] entity = message.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST, Unpooled.wrappedBuffer(entity));
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, entity.length);
        response.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

        ctx.write(response)
                .addListener(future -> {
                    ctx.flush();
                    ctx.close();
                });

    }

    /**
     * Returns a 413 (Payload Too Large) response.
     *
     * @param ctx Channel context.
     */
    private void send413PayloadTooLarge(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, REQUEST_ENTITY_TOO_LARGE);
        ctx.write(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (requestContext != null) {
            requestContext.publisher().fail(cause);
        }
        ctx.close();
    }
}
