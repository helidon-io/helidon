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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Flow;
import io.helidon.webserver.ConnectionClosedException;
import io.helidon.webserver.SocketClosedException;
import io.helidon.webserver.spi.BareResponse;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import static io.netty.handler.codec.http.HttpResponseStatus.valueOf;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * The BareResponseImpl.
 */
class BareResponseImpl implements BareResponse {

    private static final Logger LOGGER = Logger.getLogger(BareResponseImpl.class.getName());

    private final boolean keepAlive;
    private final ChannelHandlerContext ctx;
    private final AtomicBoolean statusHeadersSent = new AtomicBoolean(false);
    private final AtomicBoolean internallyClosed = new AtomicBoolean(false);
    private final CompletableFuture<BareResponse> responseFuture;
    private final CompletableFuture<BareResponse> headersFuture;
    private final BooleanSupplier requestContentConsumed;
    private final Thread thread;
    private final long requestId;

    private volatile Flow.Subscription subscription;

    /**
     * @param ctx                    the channel handler context
     * @param request                the request
     * @param requestContentConsumed whether the request content is consumed
     * @param thread                 the outbound event loop thread which will be used to write the response
     * @param requestId              the correlation ID that is added to the log statements
     */
    BareResponseImpl(ChannelHandlerContext ctx,
                     HttpRequest request,
                     BooleanSupplier requestContentConsumed,
                     Thread thread,
                     long requestId) {
        this.requestContentConsumed = requestContentConsumed;
        this.thread = thread;
        this.responseFuture = new CompletableFuture<>();
        this.headersFuture = new CompletableFuture<>();
        this.responseFuture
                .thenRun(() -> headersFuture.complete(this))
                .exceptionally(thr -> {
                    headersFuture.completeExceptionally(thr);
                    return null;
                });
        this.ctx = ctx;
        this.requestId = requestId;
        ctx.channel()
           .closeFuture()
           // to make this work, when programmatically closing the channel, the responseFuture must be closed beforehand!
           .addListener(channelFuture -> responseFuture
                   .completeExceptionally(new SocketClosedException("Response channel is closed!")));
        this.keepAlive = HttpUtil.isKeepAlive(request);
    }

    @Override
    public void writeStatusAndHeaders(Http.ResponseStatus status, Map<String, List<String>> headers) {
        Objects.requireNonNull(status, "Parameter 'statusCode' was null!");
        if (!statusHeadersSent.compareAndSet(false, true)) {
            throw new IllegalStateException("Status and headers were already sent");
        }

        DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, valueOf(status.code()));
        for (Map.Entry<String, List<String>> headerEntry : headers.entrySet()) {
            response.headers().add(headerEntry.getKey(), headerEntry.getValue());
        }

        if (keepAlive) {
            if (status.code() != Http.Status.NO_CONTENT_204.code()) {
                HttpUtil.setTransferEncodingChunked(response, true);
            }
            // Add keep alive header as per:
            // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }

        runOnOutboundEventLoopThread(() -> {
            ctx.writeAndFlush(response)
               .addListener(future -> {
                    if (future.isSuccess()) {
                        headersFuture.complete(this);
                    }
                })
               .addListener(completeOnFailureListener("An exception occurred when writing headers."))
               .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

            LOGGER.finest(() -> log("Writing headers: " + status));
        });
        headersFuture.complete(this);
    }

    /**
     * Completes {@code responseFuture} instance to signal that this response is done.
     * <b>Prefer to use {@link #completeInternal(Throwable)} to cover whole completion process.</b>
     *
     * @param throwable if {@code not-null} then this response is completed exceptionally.
     */
    private void completeResponseFuture(Throwable throwable) {
        if (throwable == null) {
            responseFuture.complete(this);
        } else {
            LOGGER.log(Level.FINER, throwable, () -> log("Response completion failed!"));
            responseFuture.completeExceptionally(throwable);
        }
    }

    /**
     * Completes this response. No other data are send to the client when response is completed. All caches are flushed.
     *
     * @param throwable if {@code not-null} then this response is completed exceptionally.
     */
    private void completeInternal(Throwable throwable) {
        if (!internallyClosed.compareAndSet(false, true)) {
            // if already closed, as the contract specifies, don't fail
            completeResponseFuture(throwable);
            return;
        }

        if (keepAlive) {
            runOnOutboundEventLoopThread(() -> {
                LOGGER.finest(() -> log("Writing an empty last http content; keep-alive: true"));

                if (!requestContentConsumed.getAsBoolean()) {
                    // the request content wasn't read, close the connection once the content is fully written.
                    LOGGER.finer(() -> log("Request content not fully read; trying to keep the connection; keep-alive: true"));

                    // if content is not consumed, we need to trigger next chunk read in order to not get stuck forever; the
                    // connection will be closed in the ForwardingHandler in case there is more than just small amount of data
                    ctx.channel().read();
                }

                ctx.writeAndFlush(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER))
                   .addListener(completeOnFailureListener("An exception occurred when writing last http content."))
                   .addListener(preventMaskingExceptionOnFailureListener(throwable))
                   .addListener(completeOnSuccessListener(throwable))
                   .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            });
        } else {
            // If keep-alive is off, close the connection once the content is fully written.
            runOnOutboundEventLoopThread(() -> {
                LOGGER.finest(() -> log("Closing with an empty buffer; keep-alive: " + keepAlive));

                ctx.writeAndFlush(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER))
                   .addListener(completeOnFailureListener("An exception occurred when writing last http content."))
                   .addListener(preventMaskingExceptionOnFailureListener(throwable))
                   .addListener(completeOnSuccessListener(throwable))
                   .addListener(ChannelFutureListener.CLOSE);
            });
        }
    }

    private GenericFutureListener<Future<? super Void>> completeOnFailureListener(String message) {
        return future -> {
            if (!future.isSuccess()) {
                completeResponseFuture(new IllegalStateException(message, future.cause()));
            }
        };
    }

    private GenericFutureListener<Future<? super Void>> preventMaskingExceptionOnFailureListener(Throwable throwable) {
        return future -> {
            if (!future.isSuccess() && throwable != null) {
                LOGGER.log(Level.FINE, throwable, () -> log("Response completion failed when handling an error."));
            }
        };
    }

    private GenericFutureListener<Future<? super Void>> completeOnSuccessListener(Throwable throwable) {
        return future -> {
            if (future.isSuccess()) {
                completeResponseFuture(throwable);
                LOGGER.finest(() -> log("Last http message flushed."));
            }
        };
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(DataChunk data) {
        if (internallyClosed.get()) {
            throw new IllegalStateException("Response is already closed!");
        }
        if (data != null) {

            LOGGER.finest(() -> log("Sending data chunk"));

            DefaultHttpContent httpContent = new DefaultHttpContent(Unpooled.wrappedBuffer(data.data()));

            runOnOutboundEventLoopThread(() -> {
                LOGGER.finest(() -> log("Sending data chunk on event loop thread."));

                ChannelFuture channelFuture;
                if (data.flush()) {
                    channelFuture = ctx.writeAndFlush(httpContent);
                } else {
                    channelFuture = ctx.write(httpContent);
                }

                channelFuture
                        .addListener(future -> {
                            data.release();
                            LOGGER.finest(() -> log("Data chunk sent with result: " + future.isSuccess()));
                        })
                        .addListener(completeOnFailureListener("Failure when sending a content!"))
                        .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            });

        }
    }

    private String log(String s) {
        return "(reqID: " + requestId + ") " + s;
    }

    /**
     * Runs the given runnable on an outbound event loop {@link #thread}.
     *
     * @param runnable the runnable to run
     */
    private void runOnOutboundEventLoopThread(Runnable runnable) {
        if (Thread.currentThread() != thread) {
            // not executing in the originating thread
            ChannelHandlerContext context = ctx.pipeline().context(ChannelOutboundHandler.class);
            if (context == null) {
                throw new ConnectionClosedException("The connection was closed.");
            }
            EventExecutor executor = context.executor();

            CountDownLatch latch = new CountDownLatch(1);
            executor.execute(() -> {
                if (Thread.currentThread() != thread) {
                    throw new IllegalStateException(String.format("Assertion error! Current thread '%s' != expected one '%s'",
                                                                  Thread.currentThread(),
                                                                  thread));
                }
                // it is safe to count down before the runnable itself as it is guarantied the
                // runnable will be executed before anything else
                latch.countDown();
                runnable.run();
            });

            try {
                if (!latch.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out while waiting for a message to be written on the event loop.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for the task to be executed on an event loop thread", e);
            }
        } else {
            runnable.run();
        }
    }

    @Override
    public void onError(Throwable thr) {
        completeInternal(thr);
        if (subscription != null) {
            subscription.cancel();
        }
    }

    @Override
    public void onComplete() {
        completeInternal(null);
        if (subscription != null) {
            subscription.cancel();
        }
    }

    @Override
    public CompletionStage<BareResponse> whenCompleted() {
        return responseFuture;
    }

    @Override
    public CompletionStage<BareResponse> whenHeadersCompleted() {
        return headersFuture;
    }

    @Override
    public long requestId() {
        return requestId;
    }
}
