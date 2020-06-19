/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
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
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

import static io.helidon.webserver.HttpInitializer.CERTIFICATE_NAME;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
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

    // this field is always accessed by the very same thread; as such, it doesn't need to be
    // concurrency aware
    private RequestContext requestContext;

    private boolean isWebSocketUpgrade = false;

    ForwardingHandler(Routing routing,
                      NettyWebServer webServer,
                      SSLEngine sslEngine,
                      Queue<ReferenceHoldingQueue<DataChunk>> queues,
                      HttpRequestDecoder httpRequestDecoder) {
        this.routing = routing;
        this.webServer = webServer;
        this.sslEngine = sslEngine;
        this.queues = queues;
        this.httpRequestDecoder = httpRequestDecoder;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();

        if (requestContext == null) {
            // there was no publisher associated with this connection
            // this happens in case there was no http request made on this connection
            return;
        }

        if (requestContext.publisher().hasRequests()) {
            ctx.channel().read();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        LOGGER.fine(() -> String.format("[Handler: %s] Received object: %s", System.identityHashCode(this), msg.getClass()));

        if (msg instanceof HttpRequest) {

            ctx.channel().config().setAutoRead(false);

            HttpRequest request = (HttpRequest) msg;
            try {
                checkDecoderResult(request);
            } catch (Throwable e) {
                send400BadRequest(ctx, e.getMessage());
                return;
            }
            request.headers().remove(Http.Header.X_HELIDON_CN);
            Optional.ofNullable(ctx.channel().attr(CERTIFICATE_NAME).get())
                    .ifPresent(name -> request.headers().set(Http.Header.X_HELIDON_CN, name));
            ReferenceHoldingQueue<DataChunk> queue = new ReferenceHoldingQueue<>();
            queues.add(queue);
            requestContext = new RequestContext(new HttpRequestScopedPublisher(ctx, queue), request);
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

            BareResponseImpl bareResponse =
                    new BareResponseImpl(ctx, request, publisherRef::isCompleted, Thread.currentThread(), requestId);
            bareResponse.whenCompleted()
                        .thenRun(() -> {
                            RequestContext requestContext = this.requestContext;
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

                            // Enable auto-read only after response has been completed
                            // to avoid a race condition with the next response
                            ctx.channel().config().setAutoRead(true);
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
                } else {
                    requestContext.publisher().emit(content);
                }
            }

            if (msg instanceof LastHttpContent) {
                if (!isWebSocketUpgrade) {
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
