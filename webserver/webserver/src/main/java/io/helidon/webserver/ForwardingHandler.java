/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;

import static io.helidon.webserver.HttpInitializer.CLIENT_CERTIFICATE;
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
    private final long maxPayloadSize;
    private final Runnable clearQueues;
    private final SocketConfiguration soConfig;
    private final DirectHandlers directHandlers;

    // this field is always accessed by the very same thread; as such, it doesn't need to be
    // concurrency aware
    private RequestContext requestContext;

    private long actualPayloadSize;
    private boolean ignorePayload;

    private CompletableFuture<ChannelFutureListener> requestEntityAnalyzed;
    private CompletableFuture<?> prevRequestFuture;
    private boolean lastContent;

    ForwardingHandler(Routing routing,
                      NettyWebServer webServer,
                      SSLEngine sslEngine,
                      ReferenceQueue<Object> queues,
                      Runnable clearQueues,
                      SocketConfiguration soConfig,
                      DirectHandlers directHandlers) {
        this.routing = routing;
        this.webServer = webServer;
        this.sslEngine = sslEngine;
        this.queues = queues;
        this.maxPayloadSize = soConfig.maxPayloadSize();
        this.clearQueues = clearQueues;
        this.soConfig = soConfig;
        this.directHandlers = directHandlers;
    }

    private void reset() {
        lastContent = false;
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
                LOGGER.fine(() -> formatMsg("Read complete lastContent", ctx));
                ctx.channel().config().setAutoRead(true);
            } else {
                LOGGER.fine(() -> formatMsg("Read complete not lastContent", ctx));
            }
            return;
        }

        if (requestContext.hasRequests()) {
            LOGGER.fine(() -> formatMsg("Read complete has requests: %s", ctx, requestContext));
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
                LOGGER.fine(() -> formatMsg("Received HttpContent: %s", ctx, System.identityHashCode(msg)));
                HelidonMdc.remove(MDC_SCOPE_ID);
                throw new IllegalStateException("There is no request context associated with this http content. "
                                                + "This is never expected to happen!");
            }

            requestContext.runInScope(() -> channelReadHttpContent(ctx, msg));
        }

        if (msg instanceof ByteBuf) {
                HelidonMdc.remove(MDC_SCOPE_ID);
                throw new IllegalStateException("Received ByteBuf without upgrading to WebSockets");
        }
        HelidonMdc.remove(MDC_SCOPE_ID);
    }

    private void channelReadHttpContent(ChannelHandlerContext ctx, Object msg) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(formatMsg("Received HttpContent: %s", ctx, System.identityHashCode(msg)));
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
                    LOGGER.finer(formatMsg("Closing connection illegal payload; method: ", ctx, method));
                }
                throw new BadRequestException("It is illegal to send a payload with http method: " + method);
            }

            // compliance with RFC 7231
            if (requestContext.responseCompleted() && !(msg instanceof LastHttpContent)) {
                // payload is not consumed and the response is already sent; we must close the connection
                LOGGER.finer(() -> formatMsg("Closing connection unconsumed payload; method: ", ctx, method));
                ctx.close();
            } else if (!ignorePayload) {
                // Check payload size if a maximum has been set
                if (maxPayloadSize >= 0) {
                    actualPayloadSize += content.readableBytes();
                    if (actualPayloadSize > maxPayloadSize) {
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
                LOGGER.fine(formatMsg("Received LastHttpContent: %s", ctx, System.identityHashCode(msg)));
            }

            lastContent = true;
            requestContext.complete();
            requestContext = null; // just to be sure that current http req/res session doesn't interfere with other ones
            requestEntityAnalyzed.complete(ChannelFutureListener.CLOSE_ON_FAILURE);
        } else if (!content.isReadable()) {
            // this is here to handle the case when the content is not readable but we didn't
            // exceptionally complete the publisher and close the connection
            throw new IllegalStateException("It is not expected to not have readable content.");
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        // Watch for prematurely closed channel
        if (requestContext != null) {
            requestContext.fail(new IOException("Channel closed prematurely by other side!"));
        }
    }

    @SuppressWarnings("checkstyle:methodlength")
    private boolean channelReadHttpRequest(ChannelHandlerContext ctx, Context requestScope, Object msg) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(formatMsg("Received HttpRequest: %s. Remote address: %s. Scope id: %s",
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
            send400BadRequest(ctx, request, e, formatMsg("Invalid HTTP request. %s", ctx, e.getMessage()));
            return true;
        }

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest(formatMsg("Requested URI: %s %s", ctx, request.method(), request.uri()));
        }

        // Certificate management
        request.headers().remove(Http.Header.X_HELIDON_CN);
        String cn = ctx.channel().attr(CLIENT_CERTIFICATE_NAME).get();
        if (cn != null) {
            request.headers().set(Http.Header.X_HELIDON_CN, cn);
        }

        // If the client x509 certificate is present on the channel, add it to the context scope of the ongoing
        // request so that helidon handlers can inspect and react to this.
        X509Certificate cert = ctx.channel().attr(CLIENT_CERTIFICATE).get();
        if (cert != null) {
            requestScope.register(WebServerTls.CLIENT_X509_CERTIFICATE, cert);
        }

        // Context, publisher and DataChunk queue for this request/response
        DataChunkHoldingQueue queue = new DataChunkHoldingQueue();
        HttpRequestScopedPublisher publisher = new HttpRequestScopedPublisher(queue);
        requestContext = new RequestContext(publisher, request, requestScope, soConfig);

        // Closure local variables that cache mutable instance variables
        RequestContext requestContextRef = requestContext;

        // Creates an indirect reference between publisher and queue so that when
        // publisher is ready for collection, we have access to queue by calling its
        // acquire method. We shall also attempt to release queue on completion of
        // bareResponse below.
        IndirectReference<HttpRequestScopedPublisher, DataChunkHoldingQueue> publisherRef =
                new IndirectReference<>(publisher, queues, queue);

        CompletableFuture<Boolean> entityRequested = new CompletableFuture<>();

        // Set up read strategy for channel based on consumer demand
        publisher.onRequest((n, demand) -> {
            entityRequested.complete(true);
            if (publisher.isUnbounded()) {
                LOGGER.finest(() -> formatMsg("Netty autoread: true", ctx));
                ctx.channel().config().setAutoRead(true);
            } else {
                LOGGER.finest(() -> formatMsg("Netty autoread: false", ctx));
                ctx.channel().config().setAutoRead(false);
            }

            if (publisher.hasRequests()) {
                LOGGER.finest(() -> formatMsg("Requesting next (%d, %d) chunks from Netty", ctx, n, demand));
                ctx.channel().read();
            } else {
                LOGGER.finest(() -> formatMsg("No hook action required", ctx));
            }
        });

        // New request ID
        long requestId = REQUEST_ID_GENERATOR.incrementAndGet();


        requestEntityAnalyzed = new CompletableFuture<>();

        // If a problem with the request URI, return 400 response
        BareRequestImpl bareRequest;
        try {
            bareRequest = new BareRequestImpl(webServer,
                                              soConfig,
                                              sslEngine,
                                              ctx,
                                              request,
                                              requestContextRef.publisher(),
                                              requestId);
        } catch (IllegalArgumentException e) {
            send400BadRequest(ctx, request, e, "Malformed URI in request");
            return true;
        }

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest(formatMsg("Request id: %s", ctx, bareRequest.requestId()));
        }

        String contentLength = request.headers().get(HttpHeaderNames.CONTENT_LENGTH);

        // HTTP WebSocket client sends a content length of 0 together with Connection: Upgrade
        if ("0".equals(contentLength)
                               && !"upgrade".equalsIgnoreCase(request.headers().get(HttpHeaderNames.CONNECTION))
                || (contentLength == null
                             && !"upgrade".equalsIgnoreCase(request.headers().get(HttpHeaderNames.CONNECTION))
                             && !"chunked".equalsIgnoreCase(request.headers().get(HttpHeaderNames.TRANSFER_ENCODING))
                             && !"multipart/byteranges".equalsIgnoreCase(request.headers().get(HttpHeaderNames.CONTENT_TYPE)))) {
            // no entity
            requestContextRef.complete();
        }

        // If context length is greater than maximum allowed, return 413 response
        if (maxPayloadSize >= 0) {
            if (contentLength != null) {
                try {
                    long value = Long.parseLong(contentLength);
                    if (value > maxPayloadSize) {
                        ignorePayload = true;
                        send413PayloadTooLarge(ctx, request);
                        return true;
                    }
                } catch (NumberFormatException e) {
                    // this cannot happen, content length is validated in decoder
                    send400BadRequest(ctx, request, e, "Invalid Content-Length header value");
                    return true;
                }
            }
        }

        // If prev response is done, the next can start writing right away (HTTP pipelining)
        if (prevRequestFuture != null && prevRequestFuture.isDone()) {
            prevRequestFuture = null;
        }

        //If the keep alive is not set, we know we will be closing the connection
        if (!HttpUtil.isKeepAlive(requestContext.request())) {
            this.requestEntityAnalyzed.complete(ChannelFutureListener.CLOSE);
        }
        // Create response and handler for its completion
        BareResponseImpl bareResponse =
                new BareResponseImpl(ctx,
                                     entityRequested,
                                     request,
                                     requestContext,
                                     prevRequestFuture,
                                     requestEntityAnalyzed,
                                     soConfig,
                                     requestId);
        prevRequestFuture = new CompletableFuture<>();
        CompletableFuture<?> thisResp = prevRequestFuture;
        bareResponse.whenCompleted()
                .thenRun(() -> {
                    // Mark response completed in context
                    requestContextRef.responseCompleted(true);
                    entityRequested.complete(false);
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
                        LOGGER.fine(formatMsg("Response complete: %s", ctx, System.identityHashCode(msg)));
                    }
                });


        if (soConfig.continueImmediately()) {
            if (HttpUtil.is100ContinueExpected(request)) {
                send100Continue(ctx, request);
            }
        } else {
            // Send 100 continue only when entity is actually requested
            entityRequested.thenAccept(requestedByUser -> {
                if (requestedByUser && HttpUtil.is100ContinueExpected(request)) {
                    send100Continue(ctx, request);
                }
            });
        }

        // If a problem during routing, return 400 response
        try {
            requestContext.runInScope(() -> routing.route(bareRequest, bareResponse));
        } catch (IllegalArgumentException e) {
            // this probably cannot happen
            send400BadRequest(ctx, request, e, "Exception encountered while routing request");
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
        // Log just cause as string
        LOGGER.fine(() -> formatMsg("Exception caught: %s", ctx, cause.toString()));

        // Log full exception in FINEST
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "Exception stack trace: " + ctx, cause);
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
            LOGGER.info(() -> formatMsg("Request %s to %s rejected: %s", null,
                    request.method().asciiName(), request.uri(), decoderResult.cause().getMessage()));
            throw new BadRequestException(String.format("Request was rejected: %s", decoderResult.cause().getMessage()),
                    decoderResult.cause());
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
        // we should flush this immediately, as we need the client to send entity
        ctx.writeAndFlush(response);
    }

    /**
     * Returns a 400 (Bad Request) response with a message as content. Message is encoded using
     * HTML entities to prevent potential XSS attacks even if content type is text/plain.
     *
     * @param ctx Channel context.
     * @param request Netty HTTP request
     * @param t associated throwable
     */
    private void send400BadRequest(ChannelHandlerContext ctx, HttpRequest request, Throwable t, String message) {
        TransportResponse handlerResponse = directHandlers.handler(DirectHandler.EventType.BAD_REQUEST)
                .handle(new DirectHandlerRequest(request),
                        DirectHandler.EventType.BAD_REQUEST,
                        Http.Status.BAD_REQUEST_400,
                        "Bad request, see server log for more information\n");

        FullHttpResponse response = toNettyResponse(handlerResponse);
        // 400 -> close connection
        response.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

        ctx.writeAndFlush(response)
                .addListener(future -> ctx.close());

        // Log simple warning and more details if FINE level set
        Error error = new Error("400: Bad request");
        LOGGER.log(Level.WARNING, error::getMessage);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, message, error);
        }

        failPublisher(error);
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
                        "Payload too large, see server log for more information\n");

        FullHttpResponse response = toNettyResponse(transportResponse);
        // too big entity -> close connection
        response.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

        ctx.writeAndFlush(response)
                .addListener(future -> ctx.close());

        // Log simple warning and more details if FINE level set
        Error error = new Error("413: Payload is too large");
        LOGGER.log(Level.WARNING, error::getMessage);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, formatMsg("Chunked Payload over max %d > %d",
                    ctx, actualPayloadSize, maxPayloadSize), error);
        }

        LOGGER.log(Level.WARNING, error, error::getMessage);
        failPublisher(error);
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
        } else {
            LOGGER.finest(() -> "Error before request context established or after completed: " + cause);
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
    private String formatMsg(String template, ChannelHandlerContext ctx, Object... params) {
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
