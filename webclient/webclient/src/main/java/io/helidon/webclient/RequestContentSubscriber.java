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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import static io.helidon.webclient.WebClientRequestBuilderImpl.REQUEST;
import static io.helidon.webclient.WebClientRequestBuilderImpl.REQUEST_ID;

/**
 * Subscriber which handles entity sending.
 */
class RequestContentSubscriber implements Flow.Subscriber<DataChunk> {

    private static final Logger LOGGER = Logger.getLogger(RequestContentSubscriber.class.getName());
    private static final LastHttpContent LAST_HTTP_CONTENT = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER);

    private final CompletableFuture<WebClientResponse> responseFuture;
    private final CompletableFuture<WebClientServiceRequest> sent;
    private final DefaultHttpRequest request;
    private final Channel channel;
    private final long requestId;

    private volatile Flow.Subscription subscription;
    private volatile DataChunk firstDataChunk;
    private volatile boolean lengthOptimization = true;

    RequestContentSubscriber(DefaultHttpRequest request,
                             Channel channel,
                             CompletableFuture<WebClientResponse> responseFuture,
                             CompletableFuture<WebClientServiceRequest> sent) {
        this.request = request;
        this.channel = channel;
        this.responseFuture = responseFuture;
        this.sent = sent;
        this.requestId = channel.attr(REQUEST_ID).get();
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
            HttpUtil.setTransferEncodingChunked(request, true);
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
            if (firstDataChunk != null) {
                HttpUtil.setContentLength(request, firstDataChunk.remaining());
            }
            channel.writeAndFlush(request);
            if (firstDataChunk != null) {
                sendData(firstDataChunk);
            }
        }
        LOGGER.finest(() -> "(client reqID: " + requestId + ") Sending last http content");
        channel.writeAndFlush(LAST_HTTP_CONTENT)
                .addListener(completeOnFailureListener("(client reqID: " + requestId + ") "
                                                               + "An exception occurred when writing last http content."))
                .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

        WebClientRequestImpl clientRequest = channel.attr(REQUEST).get();
        WebClientServiceRequest serviceRequest = clientRequest.configuration().clientServiceRequest();
        sent.complete(serviceRequest);
        LOGGER.finest(() -> "(client reqID: " + requestId + ") Request sent");
    }

    private void sendData(DataChunk data) {
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
                completeRequestFuture(new IllegalStateException(message, future.cause()));
            }
        };
    }

    private void completeRequestFuture(Throwable throwable) {
        if (throwable != null) {
            responseFuture.completeExceptionally(throwable);
        }
    }

}
