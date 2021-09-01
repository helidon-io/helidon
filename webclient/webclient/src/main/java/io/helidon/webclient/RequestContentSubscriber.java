/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import static io.helidon.webclient.WebClientRequestBuilderImpl.RECEIVED;
import static io.helidon.webclient.WebClientRequestBuilderImpl.REQUEST;
import static io.helidon.webclient.WebClientRequestBuilderImpl.REQUEST_ID;

/**
 * Subscriber which handles entity sending.
 */
class RequestContentSubscriber implements Flow.Subscriber<DataChunk> {

    private static final Logger LOGGER = Logger.getLogger(RequestContentSubscriber.class.getName());
    private static final LastHttpContent LAST_HTTP_CONTENT = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER);
    private static final Set<HttpMethod> EMPTY_CONTENT_LENGTH = Set.of(HttpMethod.PUT, HttpMethod.POST);

    private final CompletableFuture<WebClientResponse> responseFuture;
    private final CompletableFuture<WebClientServiceRequest> sent;
    private final DefaultHttpRequest request;
    private final Channel channel;
    private final long requestId;
    private final boolean allowChunkedEncoding;

    private volatile Flow.Subscription subscription;
    private volatile DataChunk firstDataChunk;
    private volatile boolean lengthOptimization = true;

    RequestContentSubscriber(DefaultHttpRequest request,
                             Channel channel,
                             CompletableFuture<WebClientResponse> responseFuture,
                             CompletableFuture<WebClientServiceRequest> sent,
                             boolean allowChunkedEncoding) {
        this.request = request;
        this.channel = channel;
        this.responseFuture = responseFuture;
        this.sent = sent;
        this.requestId = channel.attr(REQUEST_ID).get();
        this.allowChunkedEncoding = allowChunkedEncoding;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
        LOGGER.finest(() -> "(client reqID: " + requestId + ") Writing sending request and its content to the server.");
    }

    @Override
    public void onNext(DataChunk data) {
        if (data.isFlushChunk()) {
            channel.flush();
            return;
        }

        // if first chunk, do not write yet, return
        if (lengthOptimization) {
            if (firstDataChunk == null) {
                firstDataChunk = data.isReadOnly() ? data : data.duplicate();      // cache first chunk
                subscription.request(1);
                return;
            }
        }

        if (null != firstDataChunk) {
            lengthOptimization = false;
            if (HttpUtil.isContentLengthSet(request)) {
                //User set Content-Length explicitly. It should be kept.
                if (allowChunkedEncoding) {
                    request.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                }
            } else {
                if (allowChunkedEncoding) {
                    HttpUtil.setTransferEncodingChunked(request, true);
                } else if (HttpUtil.isKeepAlive(request)) {
                    throw new WebClientException("Chunked " + Http.Header.TRANSFER_ENCODING + " is disabled. "
                                                         + Http.Header.CONTENT_LENGTH + " or "
                                                         + Http.Header.CONNECTION + ": close, has to be set.");
                }
            }
            channel.writeAndFlush(request);
            sendData(firstDataChunk);
            firstDataChunk = null;

        }
        sendData(data);
    }

    @Override
    public void onError(Throwable throwable) {
        responseFuture.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        if (lengthOptimization) {
            LOGGER.finest(() -> "(client reqID: " + requestId + ") "
                    + "Message body contains only one data chunk. Setting chunked encoding to false.");
            HttpUtil.setTransferEncodingChunked(request, false);
            if (!HttpUtil.isContentLengthSet(request)) {
                if (firstDataChunk != null) {
                    HttpUtil.setContentLength(request, firstDataChunk.remaining());
                } else if (EMPTY_CONTENT_LENGTH.contains(request.method())) {
                    HttpUtil.setContentLength(request, 0);
                }
            } else if (HttpUtil.getContentLength(request) == 0 && firstDataChunk != null) {
                HttpUtil.setContentLength(request, firstDataChunk.remaining());
            }
            channel.writeAndFlush(request);
            if (firstDataChunk != null) {
                sendData(firstDataChunk);
            }
        }
        WebClientRequestImpl clientRequest = channel.attr(REQUEST).get();
        WebClientServiceRequest serviceRequest = clientRequest.configuration().clientServiceRequest();
        LOGGER.finest(() -> "(client reqID: " + requestId + ") Sending last http content");
        channel.writeAndFlush(LAST_HTTP_CONTENT)
                .addListener(completeOnFailureListener("(client reqID: " + requestId + ") "
                                                               + "An exception occurred when writing last http content."))
                .addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
                .addListener(future -> {
                    if (future.isSuccess()) {
                        sent.complete(serviceRequest);
                        System.out.println("Client: Full entity sent, Thread: " + Thread.currentThread().getName());
                        LOGGER.finest(() -> "(client reqID: " + requestId + ") Request sent");
                    }
                });
    }

    private void sendData(DataChunk data) {
//        System.out.println("Sending data");
        LOGGER.finest(() -> "(client reqID: " + requestId + ") Sending data chunk");
        DefaultHttpContent httpContent = new DefaultHttpContent(Unpooled.wrappedBuffer(data.data()));
        channel.writeAndFlush(httpContent)
                .addListener(future -> {
                    data.release();
                    subscription.request(1);
                    LOGGER.finest(() -> "(client reqID: " + requestId + ") Data chunk sent with result: " + future.isSuccess());
                })
                .addListener(completeOnFailureListener("(client reqID: " + requestId + ") Failure when sending a content!"))
                .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    private GenericFutureListener<Future<? super Void>> completeOnFailureListener(String message) {
        return future -> {
            if (!future.isSuccess()) {
                Throwable cause = future.cause();
                if (channel.attr(RECEIVED).get().isDone() || !channel.isActive()) {
                    completeRequestFuture(new IllegalStateException("(client reqID: " + requestId + ") "
                                                                            + "Connection reset by the host", cause));
                } else {
                    completeRequestFuture(new IllegalStateException(message, cause));
                }
            }
        };
    }

    private void completeRequestFuture(Throwable throwable) {
        if (throwable != null) {
            responseFuture.completeExceptionally(throwable);
        }
    }

}
