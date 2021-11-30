/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.webserver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.util.concurrent.Future;

/**
 * Wrapper for {@link io.netty.channel.ChannelHandlerContext} guarding all writes to be made exclusively from event loop thread.
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
    private final ChannelHandlerContext ctx;

    NettyChannel(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Returns the globally unique identifier of the underlying Channel.
     *
     * @return globally unique identifier
     */
    ChannelId id() {
        return ctx.channel().id();
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
        ctx.read();
    }

    /**
     * Request to flush all pending messages via this ChannelOutboundInvoker from Netty's event loop thread.
     */
    void flush() {
        if (ctx.executor().inEventLoop()) {
            ctx.flush();
        } else {
            ctx.executor().execute(ctx::flush);
        }
    }

    /**
     * Request to write a message via this ChannelHandlerContext through the ChannelPipeline from Netty's event loop thread.
     *
     * @param flush flush immediately
     * @param msg   message to write
     * @return CompletionStage completed when request is made from event loop thread, containing future of actual write
     */
    CompletionStage<ChannelFuture> write(boolean flush, Object msg) {
        CompletableFuture<ChannelFuture> channelFuture = new CompletableFuture<>();

        if (ctx.executor().inEventLoop()) {
            // Fast path for items emitted by event loop thread
            if (flush) {
                channelFuture.complete(ctx.writeAndFlush(msg));
            } else {
                channelFuture.complete(ctx.write(msg));
            }
        } else {
            if (flush) {
                ctx.executor().execute(() -> channelFuture.complete(ctx.writeAndFlush(msg)));
            } else {
                ctx.executor().execute(() -> channelFuture.complete(ctx.write(msg)));
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

    @Override
    public String toString() {
        return "NettyChannel{"
                + "ctx=" + ctx.toString()
                + '}';
    }
}
