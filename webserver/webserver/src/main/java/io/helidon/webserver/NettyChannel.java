/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.handler.codec.http.HttpExpectationFailedEvent;
import io.netty.util.concurrent.Future;

/**
 * Wrapper for {@link io.netty.channel.Channel} guarding all writes to be made exclusively from event loop thread.
 *
 * <ul>
 * <b>Netty can provide "ordering" in the one of the following situations exclusively:</b>
 *  <ul>
 *      <li>You are doing all writes from the EventLoop thread.</li>
 *      <li>You are doing no writes from the EventLoop thread (i.e. all writes are being done in other thread(s)).</li>
 *  </ul>
 * </ul>
 *
 * @see <a href="https://github.com/netty/netty/issues/3887#issuecomment-112540327"
 * >https://github.com/netty/netty/issues/3887#issuecomment-112540327</a>
 */
class NettyChannel {
    private final Channel channel;
    private final ChannelHandlerContext ctx;
    private CompletionStage<ChannelFuture> writeFuture = CompletableFuture.completedFuture(null);

    NettyChannel(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        this.channel = ctx.channel();
    }

    /**
     * Returns the globally unique identifier of the underlying Channel.
     *
     * @return globally unique identifier
     */
    ChannelId id() {
        return channel.id();
    }

    /**
     * Request to Read data from the Channel into the first inbound buffer,
     * triggers an ChannelInboundHandler.channelRead(ChannelHandlerContext, Object) event if data was read, and triggers
     * a channelReadComplete event so the handler can decide to continue reading.
     * If there's a pending read operation already, this method does nothing.
     * This will result in having the ChannelOutboundHandler.read(ChannelHandlerContext)
     * method called of the next ChannelOutboundHandler contained in the ChannelPipeline of the Channel.
     */
    void read() {
        channel.read();
    }

    /**
     * Request to flush all pending messages via this ChannelOutboundInvoker from Netty's event loop thread.
     */
    void flush() {
        if (channel.eventLoop().inEventLoop()) {
            channel.flush();
        } else {
            channel.eventLoop().execute(channel::flush);
        }
    }

    /**
     * Request to write a message via this ChannelHandlerContext through the ChannelPipeline from Netty's event loop thread.
     *
     * @param flush flush immediately
     * @param msg   message to write
     * @param listeners function for accessing channel future of the netty write
     */
    void write(boolean flush, Object msg, Function<ChannelFuture, ChannelFuture> listeners) {
        // Ordered writes
        writeFuture = writeFuture.thenCompose(f -> writeInt(flush, msg, listeners));
    }

    private CompletionStage<ChannelFuture> writeInt(boolean flush,
                                                    Object msg,
                                                    Function<ChannelFuture, ChannelFuture> listeners) {
        CompletableFuture<ChannelFuture> channelFuture = new CompletableFuture<>();

        if (channel.eventLoop().inEventLoop()) {
            // Fast path for items emitted by event loop thread
            if (flush) {
                channelFuture.complete(listeners.apply(channel.writeAndFlush(msg)));
            } else {
                channelFuture.complete(listeners.apply(channel.write(msg)));
            }
        } else {
            if (flush) {
                channel.eventLoop().execute(() -> channelFuture.complete(listeners.apply(channel.writeAndFlush(msg))));
            } else {
                channel.eventLoop().execute(() -> channelFuture.complete(listeners.apply(channel.write(msg))));
            }
        }

        return channelFuture;
    }


    /**
     * Map Netty's future completing with void to CompletableFuture completing with supplied item.
     *
     * @param nettyFuture Netty's future completing with void
     * @param completable CompletableFuture completing with supplied item
     * @param item        to complete supplied CompletableFuture with
     * @param <T>         type of the CompletableFuture's item
     */
    static <T> void completeFuture(Future<? super Void> nettyFuture, CompletableFuture<T> completable, T item) {
        if (nettyFuture.isSuccess()) {
            completable.complete(item);
        } else {
            completable.completeExceptionally(nettyFuture.cause());
        }
    }


    /**
     * Reset HttpObjectDecoder to not expect data.
     */
    void expectationFailed(){
        ctx.pipeline().fireUserEventTriggered(HttpExpectationFailedEvent.INSTANCE);
    }

    @Override
    public String toString() {
        return "NettyChannel{"
                + "channel=" + channel.toString()
                + '}';
    }
}
