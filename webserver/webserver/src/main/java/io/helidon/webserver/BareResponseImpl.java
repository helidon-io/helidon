/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import static io.netty.handler.codec.http.HttpResponseStatus.valueOf;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * The BareResponseImpl.
 */
class BareResponseImpl implements BareResponse {
    private static final Logger LOGGER = Logger.getLogger(BareResponseImpl.class.getName());

    // See HttpConversionUtil.ExtensionHeaderNames
    private static final String HTTP_2_HEADER_PREFIX = "x-http2";
    private static final String HTTP_2_STREAM_ID = "x-http2-stream-id";
    private static final SocketClosedException CLOSED = new SocketClosedException("Response channel is closed!");

    private final boolean keepAlive;
    private final NettyChannel channel;
    private final AtomicBoolean statusHeadersSent = new AtomicBoolean(false);
    private final AtomicBoolean internallyClosed = new AtomicBoolean(false);
    private final CompletableFuture<BareResponse> responseFuture;
    private final CompletableFuture<BareResponse> headersFuture;
    private final RequestContext requestContext;
    private final long requestId;
    private final String http2StreamId;
    private final HttpHeaders requestHeaders;
    private final ChannelFuture channelClosedFuture;
    private final GenericFutureListener<? extends Future<? super Void>> channelClosedListener;
    private final CompletableFuture<ChannelFutureListener> originalEntityAnalyzed;

    // Accessed by Subscriber method threads
    private Flow.Subscription subscription;
    private volatile DataChunk firstChunk;
    private CompletableFuture<?> prevRequestChunk;
    private CompletableFuture<ChannelFutureListener> requestEntityAnalyzed;

    // Accessed by writeStatusHeaders(status, headers) method
    private volatile boolean lengthOptimization;
    private volatile boolean isWebSocketUpgrade = false;
    private volatile DefaultHttpResponse response;

    /**
     * @param ctx the channel handler context
     * @param request the request
     * @param requestContext request context
     * @param prevRequestChunk Future that represents previous request completion for HTTP pipelining
     * @param requestEntityAnalyzed connection closing listener after entity analysis
     * @param requestId the correlation ID that is added to the log statements
     */
    BareResponseImpl(ChannelHandlerContext ctx,
                     HttpRequest request,
                     RequestContext requestContext,
                     CompletableFuture<?> prevRequestChunk,
                     CompletableFuture<ChannelFutureListener> requestEntityAnalyzed,
                     long requestId) {
        this.requestContext = requestContext;
        this.originalEntityAnalyzed = requestEntityAnalyzed;
        this.requestEntityAnalyzed = requestEntityAnalyzed;
        this.responseFuture = new CompletableFuture<>();
        this.headersFuture = new CompletableFuture<>();
        this.channel = new NettyChannel(ctx.channel());
        this.requestId = requestId;
        this.keepAlive = HttpUtil.isKeepAlive(request);
        this.requestHeaders = request.headers();
        this.prevRequestChunk = prevRequestChunk;
        this.http2StreamId = requestHeaders.get(HTTP_2_STREAM_ID);

        // We need to keep this listener so we can remove it when this response completes. If we don't, we leak
        // while the channel remains open since each response adds a new listener that references 'this'.
        // Use fields to avoid capturing lambdas.

        this.channelClosedListener = this::channelClosed;
        this.channelClosedFuture = ctx.channel().closeFuture();

        // to make this work, when programmatically closing the channel the responseFuture must be closed first!
        channelClosedFuture.addListener(channelClosedListener);

        responseFuture.whenComplete(this::responseComplete);
    }

    /**
     * Steps required for the completion of this response.
     *
     * @param self this instance
     * @param throwable a throwable indicating unsuccessful completion
     */
    private void responseComplete(BareResponse self, Throwable throwable) {
        if (throwable == null) {
            headersFuture.complete(this);
        } else {
            headersFuture.completeExceptionally(throwable);
        }
        channelClosedFuture.removeListener(channelClosedListener);
    }

    /**
     * Called when a channel is closed programmatically.
     *
     * @param future a future
     */
    private void channelClosed(Future<? super Void> future) {
        responseFuture.completeExceptionally(CLOSED);
    }

    @Override
    public void writeStatusAndHeaders(Http.ResponseStatus status, Map<String, List<String>> headers) {
        Objects.requireNonNull(status, "Parameter 'statusCode' was null!");
        if (!statusHeadersSent.compareAndSet(false, true)) {
            throw new IllegalStateException("Status and headers were already sent");
        }

        HttpResponseStatus nettyStatus;
        if (status instanceof Http.Status || status.reasonPhrase() == null) {
            // default reason phrase
            nettyStatus = valueOf(status.code());
        } else {
            // custom reason phrase
            nettyStatus = valueOf(status.code(), status.reasonPhrase());
        }
        response = new DefaultHttpResponse(HTTP_1_1, nettyStatus);
        for (Map.Entry<String, List<String>> headerEntry : headers.entrySet()) {
            response.headers().add(headerEntry.getKey(), headerEntry.getValue());
        }

        // Copy HTTP/2 headers to response for correlation (streamId)
        requestHeaders.names().stream()
                .filter(header -> header.startsWith(HTTP_2_HEADER_PREFIX))
                .forEach(header -> response.headers().add(header, requestHeaders.get(header)));

        // Check if WebSocket upgrade
        boolean isUpgrade = isWebSocketUpgrade(status, headers);
        if (isUpgrade) {
            isWebSocketUpgrade = true;
        } else {
            // Set chunked if length not set, may switch to length later
            boolean lengthSet = HttpUtil.isContentLengthSet(response);
            if (!lengthSet) {
                lengthOptimization = status.code() == Http.Status.OK_200.code()
                        && !HttpUtil.isTransferEncodingChunked(response) && !isSseEventStream(headers);
                HttpUtil.setTransferEncodingChunked(response, true);
            }
        }

        // Add keep alive header as per:
        // http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
        // If already set (e.g. WebSocket upgrade), do not override
        // if response Connection header is set explicitly to close, we can ignore the following
        if (!keepAlive || HttpHeaderValues.CLOSE.contentEqualsIgnoreCase(response.headers().get(HttpHeaderNames.CONNECTION))) {
            response.headers().remove(HttpHeaderNames.CONNECTION);
            originalEntityAnalyzed.complete(ChannelFutureListener.CLOSE);
        } else {
            if (!requestContext.requestCompleted()) {
                LOGGER.finer(() -> log("Request content not fully read with keep-alive: true", channel));

                if (!isWebSocketUpgrade) {
                    if (requestContext.isDataRequested()) {
                        // there are pending requests, we have emitted some data and request was not explicitly canceled
                        // this is a bug in code, where entity is requested and not fully processed
                        // throwing an exception here is a breaking change (also this may be an intermittent problem
                        // as it may depend on thread race)
                        HttpRequest request = requestContext.request();
                        LOGGER.warning("Entity was requested and not fully consumed before a response is sent. "
                                               + "This is not supported. Connection will be closed. Please fix your route for "
                                               + request.method() + " " + request.uri());

                        // let's close this connection, as it is in an unexpected state
                        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                        originalEntityAnalyzed.complete(ChannelFutureListener.CLOSE);
                    } else {
                        // we want to consume the entity and keep alive
                        // entity must be consumed here, so we do not close connection in forwarding handler
                        // because of unconsumed payload (the following code will only succeed if there is no subscriber)
                        requestContext.publisher()
                                .forEach(DataChunk::release)
                                .onComplete(() -> {
                                    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                                    originalEntityAnalyzed.complete(ChannelFutureListener.CLOSE_ON_FAILURE);
                                })
                                .onError(t -> {
                                    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                                    originalEntityAnalyzed.complete(ChannelFutureListener.CLOSE);
                                })
                                .ignoreElement();
                    }
                }
            } else if (!headers.containsKey(HttpHeaderNames.CONNECTION.toString())) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
        }

        // Content length optimization attempt
        if (!lengthOptimization) {
            requestEntityAnalyzed = requestEntityAnalyzed.thenApply(listener -> {
                LOGGER.fine(() -> log("Writing headers %s", status));
                requestContext.runInScope(() -> orderedWrite(this::initWriteResponse));
                return listener;
            });
        }
    }

    private boolean isWebSocketUpgrade(Http.ResponseStatus status, Map<String, List<String>> headers) {
        return status.code() == 101 && headers.containsKey("Upgrade")
                && headers.get("Upgrade").contains("websocket");
    }

    private boolean isSseEventStream(Map<String, List<String>> headers) {
        return headers.containsKey("Content-Type") && headers.get("Content-Type").contains("text/event-stream");
    }

    /**
     * Determines if response is a WebSockets upgrade.
     *
     * @return Outcome of test.
     * @throws IllegalStateException If headers not written yet.
     */
    boolean isWebSocketUpgrade() {
        return isWebSocketUpgrade;
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
            LOGGER.finer(() -> log("Response completion failed %s", throwable));
            if (subscription != null) {
                subscription.cancel();
            }
            internallyClosed.set(true);
            responseFuture.completeExceptionally(throwable);
        }
    }

    /**
     * Completes this response. No other data are send to the client when response is completed.
     * All caches are flushed.
     *
     * @param throwable if {@code not-null} then this response is completed exceptionally.
     */
    private void completeInternal(Throwable throwable) {
        boolean wasClosed = !internallyClosed.compareAndSet(false, true);
        if (wasClosed && subscription != null) {
            subscription.cancel();
        }
        orderedWrite(() -> completeInternalPipe(wasClosed, throwable));
    }

    /**
     * Utility method to complete internal pipe. This method must be called inside an
     * {@link #orderedWrite(Runnable)} runnable.
     *
     * @param wasClosed response closed boolean.
     * @param throwable a throwable.
     */
    private void completeInternalPipe(boolean wasClosed, Throwable throwable) {
        if (wasClosed) {
            // if already closed, as the contract specifies, don't fail
            completeResponseFuture(throwable);
            return;
        }
        requestEntityAnalyzed = requestEntityAnalyzed.thenApply(listener -> {
            requestContext.runInScope(() -> {
                if (ChannelFutureListener.CLOSE.equals(listener)) {
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest(log("Closing with an empty buffer; keep-alive: false", channel));
                    }
                } else {
                    LOGGER.finest(() -> log("Writing an empty last http content; keep-alive: true"));
                    channel.read();
                }
                writeLastContent(throwable, listener);
            });
            return listener;
        });
    }

    /**
     * Write last HTTP content. If length optimization is active and a first chunk is cached,
     * switch content encoding and write response. This method must be called inside an
     * {@link #orderedWrite(Runnable)} runnable.
     *
     * @param throwable   A throwable.
     * @param closeAction Close action listener.
     */
    private void writeLastContent(final Throwable throwable, final ChannelFutureListener closeAction) {
        boolean chunked = true;
        if (lengthOptimization) {
            if (throwable == null) {
                int length = (firstChunk == null ? 0 : firstChunk.remaining());
                HttpUtil.setTransferEncodingChunked(response, false);
                HttpUtil.setContentLength(response, length);
                chunked = false;
            } else {
                //headers not sent yet
                response.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
                //We are not using CombinedHttpHeaders
                response.headers()
                        .set(HttpHeaderNames.TRAILER, Response.STREAM_STATUS + "," + Response.STREAM_RESULT);
            }
        }
        if (response != null) {
            initWriteResponse();
        }

        LastHttpContent lastHttpContent = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER);
        if (chunked) {
            if (throwable != null) {
                lastHttpContent.trailingHeaders()
                        .set(Response.STREAM_STATUS, 500)
                        .set(Response.STREAM_RESULT, throwable);
                LOGGER.severe(() -> log("Upstream error while sending response: %s", throwable));
            }
        }
        channel.write(true, lastHttpContent, f -> f
                .addListener(completeOnFailureListener("An exception occurred when writing last http content."))
                .addListener(completeOnSuccessListener(throwable))
                .addListener(closeAction));
    }

    private GenericFutureListener<Future<? super Void>> completeOnFailureListener(String message) {
        return future -> {
            if (!future.isSuccess()) {
                completeResponseFuture(new IllegalStateException(message, future.cause()));
                LOGGER.finest(() -> log("Failure listener: " + future.cause()));
            }
        };
    }

    private GenericFutureListener<Future<? super Void>> completeOnSuccessListener(Throwable throwable) {
        return future -> {
            if (future.isSuccess()) {
                completeResponseFuture(throwable);
                LOGGER.finest(() -> log("Last http message flushed", channel));
            }
        };
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        if (this.subscription != null) {
            subscription.cancel();
            return;
        }
        this.subscription = Objects.requireNonNull(subscription, "subscription is null");
        subscription.request(1);
    }

    @Override
    public void onNext(DataChunk data) {
        Objects.requireNonNull(data, "DataChunk is null");
        requestEntityAnalyzed = requestEntityAnalyzed.thenApply(listener -> {
            requestContext.runInScope(() -> {
                if (data.isFlushChunk()) {
                    if (prevRequestChunk == null) {
                        channel.flush();
                    } else {
                        prevRequestChunk = prevRequestChunk.thenRun(channel::flush);
                    }
                    subscription.request(1);
                    return;
                }

                if (lengthOptimization && firstChunk == null) {
                    firstChunk = data.isReadOnly() ? data : data.duplicate();      // cache first chunk
                    subscription.request(1);
                    return;
                }

                orderedWrite(() -> onNextPipe(data));
            });
            return listener;
        });
    }

    /**
     * Utility method to write next data chunk. This method must be called inside an
     * {@link #orderedWrite(Runnable)} runnable.
     *
     * @param data the data chunk.
     */
    private void onNextPipe(DataChunk data) {
        if (lengthOptimization) {
            initWriteResponse();
        }
        sendData(data, true);
    }

    /**
     * Initiates write of response and sends first chunk if available. This method must be called
     * inside an {@link #orderedWrite(Runnable)} runnable.
     *
     * @return Future of response or first chunk.
     */
    private void initWriteResponse() {
        channel.write(false, response, f -> f
                .addListener(future -> NettyChannel.completeFuture(future, headersFuture, this))
                .addListener(completeOnFailureListener("An exception occurred when writing headers."))
                .addListener(ChannelFutureListener.CLOSE_ON_FAILURE));
        response = null;
        if (firstChunk != null) {
            sendData(firstChunk, false);
            firstChunk = null;
        }
        lengthOptimization = false;
    }

    /**
     * Submits a data chunk for writing. This method must be called inside an
     * {@link #orderedWrite(Runnable)} runnable.
     *
     * @param data the chunk.
     */
    private void sendData(DataChunk data, boolean requestOneMore) {
        LOGGER.finest(() -> log("Sending data chunk"));

        DefaultHttpContent httpContent;
        if (data.isBackedBy(ByteBuf.class)) {
            // DefaultHttpContent will call release, we retain to also call ours
            ByteBuf[] byteBufs = data.data(ByteBuf.class);
            if (byteBufs.length == 1) {
                httpContent = new DefaultHttpContent(byteBufs[0].retain());
            } else {
                for (ByteBuf byteBuf : byteBufs) {
                    byteBuf.retain();
                }
                httpContent = new DefaultHttpContent(Unpooled.wrappedBuffer(byteBufs));
            }
        } else {
            httpContent = new DefaultHttpContent(Unpooled.wrappedBuffer(data.data()));
        }

        LOGGER.finest(() -> log("Sending data chunk on event loop thread", channel));

        channel.write(data.flush(), httpContent, f -> {
            // After request for write is made on event loop thread
            if (!data.flush() && requestOneMore) {
                // No flush, request another chunk immediately, this one needs to wait in the cache
                subscription.request(1);
            }
            // Add listeners to execute when actual write is done
            return f.addListener(future -> {
                        // Complete write future based con channel future
                        data.writeFuture()
                                .ifPresent(writeFuture -> NettyChannel.completeFuture(future, writeFuture, data));
                        boolean flush = data.flush();
                        data.release();
                        if (flush && requestOneMore) {
                            // Previous chunk sent, request another one
                            subscription.request(1);
                        }
                        LOGGER.finest(() -> log("Data chunk sent with result: %s", future.isSuccess()));
                    })
                    .addListener(completeOnFailureListener("Failure when sending a content!"))
                    .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        });
    }


    @Override
    public void onError(Throwable thr) {
        Objects.requireNonNull(thr, "throwable is null");
        completeInternal(thr);
    }

    @Override
    public void onComplete() {
        completeInternal(null);
    }

    @Override
    public Single<BareResponse> whenCompleted() {
        // need to return a new single each time
        return Single.create(responseFuture);
    }

    @Override
    public Single<BareResponse> whenHeadersCompleted() {
        // need to return a new single each time
        return Single.create(headersFuture);
    }

    @Override
    public long requestId() {
        return requestId;
    }

    /**
     * Ensures a write for a response is only submitted when all writes from the previous
     * response in an HTTP connection have been submitted. This is required to properly
     * support HTTP pipelining.
     *
     * @param runnable a runnable that writes.
     * @return new future or {@code null}.
     */
    private CompletableFuture<?> orderedWrite(Runnable runnable) {
        if (prevRequestChunk == null) {
            runnable.run();
        } else {
            prevRequestChunk = prevRequestChunk.thenRun(runnable);
        }
        return prevRequestChunk;
    }

    /**
     * Log message formatter for this class.
     *
     * @param template template suffix.
     * @param params template suffix params.
     * @return string to log.
     */
    private String log(String template, Object... params) {
        List<Object> list = new ArrayList<>(params.length + 3);
        list.add(System.identityHashCode(this));
        list.add(channel.id());
        list.add(http2StreamId != null ? http2StreamId : "N/A");
        list.addAll(Arrays.asList(params));
        return String.format("[Response: %s, Channel: 0x%s, StreamID: %s] " + template, list.toArray());
    }
}
