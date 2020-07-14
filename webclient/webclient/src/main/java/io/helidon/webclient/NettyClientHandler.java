/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.webclient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.BufferedEmittingPublisher;
import io.helidon.common.reactive.Single;
import io.helidon.webclient.spi.WebClientService;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AttributeKey;

import static io.helidon.webclient.WebClientRequestBuilderImpl.COMPLETED;
import static io.helidon.webclient.WebClientRequestBuilderImpl.IN_USE;
import static io.helidon.webclient.WebClientRequestBuilderImpl.RECEIVED;
import static io.helidon.webclient.WebClientRequestBuilderImpl.REQUEST;
import static io.helidon.webclient.WebClientRequestBuilderImpl.RESPONSE;
import static io.helidon.webclient.WebClientRequestBuilderImpl.RESULT;

/**
 * Created for each request/response interaction.
 */
class NettyClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final Logger LOGGER = Logger.getLogger(NettyClientHandler.class.getName());

    private static final AttributeKey<WebClientServiceResponse> SERVICE_RESPONSE = AttributeKey.valueOf("serviceResponse");

    private static final List<HttpInterceptor> HTTP_INTERCEPTORS = new ArrayList<>();

    static {
        HTTP_INTERCEPTORS.add(new RedirectInterceptor());
    }

    private HttpResponsePublisher publisher;
    private ResponseCloser responseCloser;

    /**
     * Creates new instance.
     */
    NettyClientHandler() {
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
        if (publisher != null && publisher.hasRequests()) {
            ctx.channel().read();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws IOException {
        if (msg instanceof HttpResponse) {
            ctx.channel().config().setAutoRead(false);
            HttpResponse response = (HttpResponse) msg;
            WebClientRequestImpl clientRequest = ctx.channel().attr(REQUEST).get();
            RequestConfiguration requestConfiguration = clientRequest.configuration();

            this.publisher = new HttpResponsePublisher(ctx);
            this.responseCloser = new ResponseCloser(ctx);
            WebClientResponseImpl.Builder responseBuilder = WebClientResponseImpl.builder();
            responseBuilder.contentPublisher(publisher)
                    .readerContext(requestConfiguration.readerContext())
                    .status(helidonStatus(response.status()))
                    .httpVersion(Http.Version.create(response.protocolVersion().toString()))
                    .responseCloser(responseCloser)
                    .lastEndpointURI(requestConfiguration.requestURI());

            HttpHeaders nettyHeaders = response.headers();
            for (String name : nettyHeaders.names()) {
                List<String> values = nettyHeaders.getAll(name);
                responseBuilder.addHeader(name, values);
            }

            // we got a response, we can safely complete the future
            // all errors are now fed only to the publisher
            WebClientResponse clientResponse = responseBuilder.build();
            ctx.channel().attr(RESPONSE).set(clientResponse);

            for (HttpInterceptor interceptor : HTTP_INTERCEPTORS) {
                if (interceptor.shouldIntercept(response.status(), requestConfiguration)) {
                    interceptor.handleInterception(response, clientRequest, ctx.channel().attr(RESULT).get());
                    if (!interceptor.continueAfterInterception()) {
                        responseCloser.close().thenAccept(future -> LOGGER.finest(() -> "Response closed due to redirection"));
                        return;
                    }
                }
            }

            requestConfiguration.cookieManager().put(requestConfiguration.requestURI(),
                                                     clientResponse.headers().toMap());

            WebClientServiceResponse clientServiceResponse =
                    new WebClientServiceResponseImpl(requestConfiguration.context().get(),
                                                     clientResponse.headers(),
                                                     clientResponse.status());

            ctx.channel().attr(SERVICE_RESPONSE).set(clientServiceResponse);

            List<WebClientService> services = requestConfiguration.services();
            CompletionStage<WebClientServiceResponse> csr = CompletableFuture.completedFuture(clientServiceResponse);

            for (WebClientService service : services) {
                csr = csr.thenCompose(clientSerResponse -> service.response(clientRequest, clientSerResponse));
            }

            CompletableFuture<WebClientServiceResponse> responseReceived = ctx.channel().attr(RECEIVED).get();
            CompletableFuture<WebClientResponse> responseFuture = ctx.channel().attr(RESULT).get();
            csr.whenComplete((clientSerResponse, throwable) -> {
                if (throwable != null) {
                    responseReceived.completeExceptionally(throwable);
                    responseFuture.completeExceptionally(throwable);
                    responseCloser.close();
                } else {
                    responseReceived.complete(clientServiceResponse);
                    responseReceived.thenRun(() -> {
                        if (shouldResponseAutomaticallyClose(clientResponse)) {
                            responseCloser.close()
                                    .thenAccept(aVoid -> {
                                        LOGGER.finest(() -> "Response automatically closed. No entity expected");
                                    });
                        }
                        responseFuture.complete(clientResponse);
                    });
                }
            });
        }

        if (responseCloser.isClosed()) {
            return;
        }

        // never "else-if" - msg may be an instance of more than one type, we must process all of them
        if (msg instanceof HttpContent) {
            HttpContent content = (HttpContent) msg;
            publisher.emit(content.content());
        }

        if (msg instanceof LastHttpContent) {
            responseCloser.close();
        }
    }

    private boolean shouldResponseAutomaticallyClose(WebClientResponse clientResponse) {
        WebClientResponseHeaders headers = clientResponse.headers();
        if (clientResponse.status() == Http.Status.NO_CONTENT_204) {
            return true;
        }
        return headers.contentType().isEmpty()
                && noContentLength(headers)
                && notChunked(headers);
    }

    private boolean noContentLength(WebClientResponseHeaders headers) {
        return headers.first(Http.Header.CONTENT_LENGTH).isEmpty()
                || headers.first(Http.Header.CONTENT_LENGTH).get().equals("0");
    }

    private boolean notChunked(WebClientResponseHeaders headers) {
        return headers.first(Http.Header.TRANSFER_ENCODING).isEmpty();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        CompletableFuture<WebClientResponse> responseFuture = ctx.channel().attr(RESULT).get();
        if (responseFuture.isDone()) {
            // we failed during entity processing
            publisher.fail(cause);
        } else {
            // we failed before getting response
            responseFuture.completeExceptionally(cause);
        }
        ctx.close();
    }

    private Http.ResponseStatus helidonStatus(HttpResponseStatus nettyStatus) {
        final int statusCode = nettyStatus.code();

        Optional<Http.Status> status = Http.Status.find(statusCode);
        if (status.isPresent()) {
            return status.get();
        }
        return new Http.ResponseStatus() {
            @Override
            public int code() {
                return statusCode;
            }

            @Override
            public Family family() {
                return Family.of(statusCode);
            }

            @Override
            public String reasonPhrase() {
                return nettyStatus.reasonPhrase();
            }
        };
    }

    private static final class HttpResponsePublisher extends BufferedEmittingPublisher<DataChunk> {

        private final ReentrantReadWriteLock.WriteLock lock = new ReentrantReadWriteLock().writeLock();

        HttpResponsePublisher(ChannelHandlerContext ctx) {
            super.onRequest((n, cnt) -> {
                ctx.channel().config().setAutoRead(super.isUnbounded());

                try {
                    lock.lock();
                    if (super.hasRequests()) {
                        ctx.channel().read();
                    }
                } finally {
                    lock.unlock();
                }
            });
        }

        public int emit(final ByteBuf buf) {
            buf.retain();
            return super.emit(DataChunk.create(false, true, buf::release,
                                               buf.nioBuffer().asReadOnlyBuffer()));
        }
    }

    final class ResponseCloser {

        private final AtomicBoolean closed;
        private final ChannelHandlerContext ctx;
        private final CompletableFuture<Void> cf;

        ResponseCloser(ChannelHandlerContext ctx) {
            this.ctx = ctx;
            this.closed = new AtomicBoolean();
            this.cf = new CompletableFuture<>();
        }

        boolean isClosed() {
            return closed.get();
        }

        /**
         * Asynchronous close method.
         *
         * @return single of the closing process
         */
        Single<Void> close() {
            if (closed.compareAndSet(false, true)) {
                Channel channel = ctx.channel();
                WebClientServiceResponse clientServiceResponse = channel.attr(SERVICE_RESPONSE).get();
                CompletableFuture<WebClientServiceResponse> requestComplete = channel.attr(COMPLETED).get();
                requestComplete.complete(clientServiceResponse);
                channel.config().setAutoRead(true);
                WebClientResponse response = channel.attr(RESPONSE).get();
                String connection = response.headers().first(Http.Header.CONNECTION)
                        .orElseGet(HttpHeaderValues.CLOSE::toString);
                if (connection.equals(HttpHeaderValues.CLOSE.toString())) {
                    ctx.close()
                            .addListener(future -> {
                                if (future.isSuccess()) {
                                    LOGGER.finest(() -> "Response from has been closed.");
                                    cf.complete(null);
                                } else {
                                    LOGGER.log(Level.SEVERE,
                                               future.cause(),
                                               () -> "An exception occurred while closing the response");
                                    cf.completeExceptionally(future.cause());
                                }
                            });
                } else {
                    channel.attr(IN_USE).get().set(false);
                    cf.complete(null);
                }
                publisher.complete();
            }
            return Single.create(cf, true);
        }

    }

}
