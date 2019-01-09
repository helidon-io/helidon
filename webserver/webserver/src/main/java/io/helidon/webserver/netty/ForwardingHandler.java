/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.netty;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import javax.net.ssl.SSLEngine;

import io.helidon.webserver.BadRequestException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.spi.BareRequest;
import io.helidon.webserver.spi.BareResponse;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;

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
    private final Queue<ReferenceHoldingQueue<ByteBufRequestChunk>> queues;

    // this field is always accessed by the very same thread; as such, it doesn't need to be
    // concurrency aware
    private RequestContext requestContext;

    ForwardingHandler(Routing routing,
                      NettyWebServer webServer,
                      SSLEngine sslEngine,
                      Queue<ReferenceHoldingQueue<ByteBufRequestChunk>> queues) {
        this.routing = routing;
        this.webServer = webServer;
        this.sslEngine = sslEngine;
        this.queues = queues;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();

        if (requestContext == null) {
            // there was no publisher associated with this connection
            // this happens in case there was no http request made on this connection
            return;
        }

        if (requestContext.publisher().tryAcquire() > 0) {
            ctx.channel().read();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        LOGGER.fine(() -> String.format("[Handler: %s] Received object: %s", System.identityHashCode(this), msg.getClass()));

        if (msg instanceof HttpRequest) {

            ctx.channel().config().setAutoRead(false);

            HttpRequest request = (HttpRequest) msg;
            ReferenceHoldingQueue<ByteBufRequestChunk> queue = new ReferenceHoldingQueue<>();
            queues.add(queue);
            requestContext = new RequestContext(new HttpRequestScopedPublisher(ctx, queue), request);
            // the only reason we have the 'ref' here is that the field might get assigned with null
            HttpRequestScopedPublisher publisherRef = requestContext.publisher();
            long requestId = REQUEST_ID_GENERATOR.incrementAndGet();

            BareRequestImpl bareRequest =
                    new BareRequestImpl((HttpRequest) msg, requestContext.publisher(), webServer, ctx, sslEngine, requestId);
            BareResponseImpl bareResponse =
                    new BareResponseImpl(ctx, request, publisherRef::isCompleted, Thread.currentThread(), requestId);

            bareResponse.whenCompleted()
                        .thenRun(() -> {
                            RequestContext requestContext = this.requestContext;
                            requestContext.responseCompleted(true);
                            /*
                             * Cleanup for these queues is done in HttpInitializer. However,
                             * it may take a while for the event loop on the channel to
                             * execute that code. If the handler has already consumed
                             * all the data when this code is executed, we can cleanup
                             * here and reduce the number of queues we track. This is
                             * especially useful when keep-alive is enabled.
                             */
                            if (queue.release()) {
                                queues.remove(queue);
                            }
                        });
            if (HttpUtil.is100ContinueExpected(request)) {
                send100Continue(ctx);
            }

            routing.route(bareRequest, bareResponse);
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
                    requestContext.publisher().submit(content);
                }
            }

            if (msg instanceof LastHttpContent) {
                requestContext.publisher().complete();
                requestContext = null; // just to be sure that current http req/res session doesn't interfere with other ones

                // with the last http request content, the tcp connection has to become 'autoReadable'
                // so that next http request can be obtained
                ctx.channel().config().setAutoRead(true);
            } else if (!content.isReadable()) {
                // this is here to handle the case when the content is not readable but we didn't
                // exceptionally complete the publisher and close the connection
                throw new IllegalStateException("It is not expected to not have readable content.");
            }
        }
    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE);
        ctx.write(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (requestContext != null) {
            requestContext.publisher().error(cause);
        }
        ctx.close();
    }
}
