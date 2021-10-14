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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLEngine;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.Http;
import io.helidon.logging.common.HelidonMdc;
import io.helidon.webserver.ByteBufRequestChunk.DataChunkHoldingQueue;
import io.helidon.webserver.DirectHandler.TransportResponse;
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
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;

import static io.helidon.webserver.HttpInitializer.CLIENT_CERTIFICATE_NAME;
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
    private static final String MDC_SCOPE_ID = "io.helidon.scope-id";

    private final Routing routing;
    private final NettyWebServer webServer;
    private final SSLEngine sslEngine;
    private final ReferenceQueue<Object> queues;
    private final HttpRequestDecoder httpRequestDecoder;
    private final long maxPayloadSize;
    private final Runnable clearQueues;
    private final DirectHandlers directHandlers;

    // this field is always accessed by the very same thread; as such, it doesn't need to be
    // concurrency aware
    private RequestContext requestContext;

    private boolean isWebSocketUpgrade;
    private long actualPayloadSize;
    private boolean ignorePayload;

    private CompletableFuture<ChannelFutureListener> requestEntityAnalyzed;
    private CompletableFuture<?> prevRequestFuture;
    private boolean lastContent;
    private boolean hadContentAlready;

    ForwardingHandler(Routing routing,
                      NettyWebServer webServer,
                      SSLEngine sslEngine,
                      ReferenceQueue<Object> queues,
                      Runnable clearQueues,
                      HttpRequestDecoder httpRequestDecoder,
                      long maxPayloadSize,
                      DirectHandlers directHandlers) {
        this.routing = routing;
        this.webServer = webServer;
        this.sslEngine = sslEngine;
        this.queues = queues;
        this.httpRequestDecoder = httpRequestDecoder;
        this.maxPayloadSize = maxPayloadSize;
        this.clearQueues = clearQueues;
        this.directHandlers = directHandlers;
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
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            Context requestScope = Context.create(webServer.context());
            requestScope.register(WebServer.class.getName() + ".connection",
                                  "0x" + ctx.channel().id());

            HelidonMdc.set(MDC_SCOPE_ID, requestScope.id());

            boolean shouldReturn = Contexts.runInContext(requestScope,
                                                         () -> channelReadHttpRequest(ctx, requestScope, msg));

            if (shouldReturn) {
                HelidonMdc.remove(MDC_SCOPE_ID);
                return;
            }
        }

        if (requestContext != null) {
            HelidonMdc.set(MDC_SCOPE_ID, requestContext.scope().id());
        }

        if (msg instanceof HttpContent) {
            if (requestContext == null) {
                LOGGER.fine(() -> log("Received HttpContent: %s", ctx, System.identityHashCode(msg)));
                HelidonMdc.remove(MDC_SCOPE_ID);
                throw new IllegalStateException("There is no request context associated with this http content. "
                                                + "This is never expected to happen!");
            }

            requestContext.runInScope(() -> channelReadHttpContent(ctx, msg));
        }

        // We receive a raw bytebuf if connection was upgraded to WebSockets
        if (msg instanceof ByteBuf) {
            if (!isWebSocketUpgrade) {
                HelidonMdc.remove(MDC_SCOPE_ID);
                throw new IllegalStateException("Received ByteBuf without upgrading to WebSockets");
            }
            requestContext.runInScope(() -> {
                // Simply forward raw bytebuf to Tyrus for processing
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest(log("Received ByteBuf of WebSockets connection: %s", ctx, msg));
                }
                requestContext.emit((ByteBuf) msg);
            });
        }
        HelidonMdc.remove(MDC_SCOPE_ID);
    }

    private void channelReadHttpContent(ChannelHandlerContext ctx, Object msg) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(log("Received HttpContent: %s", ctx, System.identityHashCode(msg)));
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
                requestEntityAnalyzed.complete(ChannelFutureListener.CLOSE);
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer(log("Closing connection illegal payload; method: ", ctx, method));
                }
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
                        send413PayloadTooLarge(ctx, requestContext.request());
                    } else {
                        requestContext.emit(content);
                    }
                } else {
                    requestContext.emit(content);
                }
            }
        }

        if (msg instanceof LastHttpContent) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(log("Received LastHttpContent: %s", ctx, System.identityHashCode(msg)));
            }

            if (!isWebSocketUpgrade) {
                lastContent = true;
                requestContext.complete();
                requestContext = null; // just to be sure that current http req/res session doesn't interfere with other ones
            }
            requestEntityAnalyzed.complete(ChannelFutureListener.CLOSE_ON_FAILURE);
        } else if (!content.isReadable()) {
            // this is here to handle the case when the content is not readable but we didn't
            // exceptionally complete the publisher and close the connection
            throw new IllegalStateException("It is not expected to not have readable content.");
        } else if (!requestContext.hasRequests()
                && HttpUtil.isKeepAlive(requestContext.request())
                && !requestEntityAnalyzed.isDone()) {
            if (hadContentAlready) {
                LOGGER.finest(() -> "More than one unhandled content present. Closing the connection.");
                requestEntityAnalyzed.complete(ChannelFutureListener.CLOSE);
            } else {
                //We are checking the unhandled entity, but we cannot be sure if connection should be closed or not.
                //Next content has to be checked if it is last chunk. If not close connection.
                hadContentAlready = true;
                LOGGER.finest(() -> "Requesting the next chunk to determine if the connection should be closed.");
                ctx.channel().read();
            }
        }
    }

    @SuppressWarnings("checkstyle:methodlength")
    private boolean channelReadHttpRequest(ChannelHandlerContext ctx, Context requestScope, Object msg) {
        hadContentAlready = false;
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(log("Received HttpRequest: %s. Remote address: %s. Scope id: %s",
                                  ctx,
                                  System.identityHashCode(msg),
                                  ctx.channel().remoteAddress(),
                                  requestScope.id()));
        }

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
            LOGGER.finest(() -> log("Invalid HTTP request. %s", ctx, e.getMessage()));
            send400BadRequest(ctx, request, e);
            return true;
        }

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest(log("Requested URI: %s %s", ctx, request.method(), request.uri()));
        }

        // Certificate management
        request.headers().remove(Http.Header.X_HELIDON_CN);
        Optional.ofNullable(ctx.channel().attr(CLIENT_CERTIFICATE_NAME).get())
                .ifPresent(name -> request.headers().set(Http.Header.X_HELIDON_CN, name));

        // Context, publisher and DataChunk queue for this request/response
        DataChunkHoldingQueue queue = new DataChunkHoldingQueue();
        HttpRequestScopedPublisher publisher = new HttpRequestScopedPublisher(queue);
        requestContext = new RequestContext(publisher, request, requestScope);

        // Watch for prematurely closed channel
        ctx.channel().closeFuture()
                .addListener(f -> {
                    if (requestContext != null && !publisher.isCompleted()) {
                        IllegalStateException e =
                                new IllegalStateException("Channel closed prematurely by other side!", f.cause());
                        failPublisher(e);
                    }
                });

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
            send400BadRequest(ctx, request, e);
            return true;
        }

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest(log("Request id: %s", ctx, bareRequest.requestId()));
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
                        send413PayloadTooLarge(ctx, request);
                        return true;
                    }
                } catch (NumberFormatException e) {
                    // this cannot happen, content length is validated in decoder
                    send400BadRequest(ctx, request, e);
                    return true;
                }
            }
        }

        // If prev response is done, the next can start writing right away (HTTP pipelining)
        if (prevRequestFuture != null && prevRequestFuture.isDone()) {
            prevRequestFuture = null;
        }

        requestEntityAnalyzed = new CompletableFuture<>();

        //If the keep alive is not set, we know we will be closing the connection
        if (!HttpUtil.isKeepAlive(requestContext.request())) {
            this.requestEntityAnalyzed.complete(ChannelFutureListener.CLOSE);
        }
        // Create response and handler for its completion
        BareResponseImpl bareResponse =
                new BareResponseImpl(ctx,
                                     request,
                                     requestContext,
                                     publisher::isCompleted,
                                     publisher::hasRequests,
                                     publisher::isCancelled,
                                     prevRequestFuture,
                                     requestEntityAnalyzed,
                                     requestId);
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

                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine(log("Response complete: %s", ctx, System.identityHashCode(msg)));
                    }
                });
        if (HttpUtil.is100ContinueExpected(request)) {
            send100Continue(ctx, request);
        }

        // If a problem during routing, return 400 response
        try {
            requestContext.runInScope(() -> routing.route(bareRequest, bareResponse));
        } catch (IllegalArgumentException e) {
            // this probably cannot happen
            send400BadRequest(ctx, request, e);
            return true;
        }

        // If WebSockets upgrade, re-arrange pipeline and drop HTTP decoder
        if (bareResponse.isWebSocketUpgrade()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(log("Replacing HttpRequestDecoder by WebSocketServerProtocolHandler", ctx));
            }
            ctx.pipeline().replace(httpRequestDecoder, "webSocketsHandler",
                                   new WebSocketServerProtocolHandler(bareRequest.uri().getPath(), null, true));
            removeHandshakeHandler(ctx);        // already done by Tyrus
            isWebSocketUpgrade = true;
            return true;
        }

        return false;
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

    private void send100Continue(ChannelHandlerContext ctx,
                                        HttpRequest request) {

        TransportResponse transportResponse = directHandlers.handler(DirectHandler.EventType.CONTINUE)
                .handle(new DirectHandlerRequest(request),
                        DirectHandler.EventType.CONTINUE,
                        Http.Status.CONTINUE_100,
                        "");

        FullHttpResponse response = toNettyResponse(transportResponse);
        ctx.write(response);
    }

    /**
     * Returns a 400 (Bad Request) response with a message as content. Message is encoded using
     * HTML entities to prevent potential XSS attacks even if content type is text/plain.
     *
     * @param ctx Channel context.
     * @param request Netty HTTP request
     * @param t associated throwable
     */
    private void send400BadRequest(ChannelHandlerContext ctx, HttpRequest request, Throwable t) {
        TransportResponse handlerResponse = directHandlers.handler(DirectHandler.EventType.BAD_REQUEST)
                .handle(new DirectHandlerRequest(request),
                        DirectHandler.EventType.BAD_REQUEST,
                        Http.Status.BAD_REQUEST_400,
                        t);

        FullHttpResponse response = toNettyResponse(handlerResponse);

        ctx.writeAndFlush(response)
                .addListener(future -> ctx.close());

        failPublisher(new Error("400: Bad request"));
    }

    /**
     * Returns a 413 (Payload Too Large) response.
     *
     * @param ctx Channel context.
     */
    private void send413PayloadTooLarge(ChannelHandlerContext ctx, HttpRequest request) {
        TransportResponse transportResponse = directHandlers.handler(DirectHandler.EventType.PAYLOAD_TOO_LARGE)
                .handle(new DirectHandlerRequest(request),
                        DirectHandler.EventType.PAYLOAD_TOO_LARGE,
                        Http.Status.REQUEST_ENTITY_TOO_LARGE_413,
                        "");

        FullHttpResponse response = toNettyResponse(transportResponse);

        ctx.writeAndFlush(response)
                .addListener(future -> ctx.close());

        failPublisher(new Error("413: Payload is too large"));
    }

    private FullHttpResponse toNettyResponse(TransportResponse handlerResponse) {
        Optional<byte[]> entity = handlerResponse.entity();
        Http.ResponseStatus status = handlerResponse.status();
        Map<String, List<String>> headers = handlerResponse.headers();

        HttpResponseStatus nettyStatus = HttpResponseStatus.valueOf(status.code(), status.reasonPhrase());

        FullHttpResponse response = entity.map(bytes -> new DefaultFullHttpResponse(HTTP_1_1,
                                                                                    nettyStatus,
                                                                                    Unpooled.wrappedBuffer(bytes)))
                .orElseGet(() -> new DefaultFullHttpResponse(HTTP_1_1, nettyStatus));

        HttpHeaders nettyHeaders = response.headers();
        headers.forEach(nettyHeaders::add);
        nettyHeaders.add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        return response;
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
        list.add(ctx != null ? ctx.channel().id() : "N/A");
        list.addAll(Arrays.asList(params));
        return String.format("[Handler: %s, Channel: 0x%s] " + template, list.toArray());
    }

    private static final class DirectHandlerRequest implements DirectHandler.TransportRequest {
        private final String protocolVersion;
        private final String uri;
        private final String method;
        private final Map<String, List<String>> headers;

        private DirectHandlerRequest(HttpRequest request) {
            protocolVersion = request.protocolVersion().text();
            uri = request.uri();
            method = request.method().name();
            Map<String, List<String>> result = new HashMap<>();
            for (String name : request.headers().names()) {
                result.put(name, request.headers().getAll(name));
            }
            headers = Map.copyOf(result);
        }

        @Override
        public String protocolVersion() {
            return protocolVersion;
        }

        @Override
        public String uri() {
            return uri;
        }

        @Override
        public String method() {
            return method;
        }

        @Override
        public Map<String, List<String>> headers() {
            return headers;
        }
    }
}
