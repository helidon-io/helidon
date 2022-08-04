/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import static io.helidon.webclient.WebClientRequestBuilderImpl.RECEIVED;
import static io.helidon.webclient.WebClientRequestBuilderImpl.REQUEST;

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

    private final Channel channel;
    private CompletionStage<ChannelFuture> writeFuture = CompletableFuture.completedFuture(null);

    NettyChannel(Channel channel) {
        this.channel = channel;
    }

    /**
     * Request to flush all pending messages via this ChannelOutboundInvoker from Netty's event loop thread.
     */
    void flush() {
        writeFuture = writeFuture.thenApply(f -> {
            if (channel.eventLoop().inEventLoop()) {
                channel.flush();
            } else {
                channel.eventLoop().execute(channel::flush);
            }
            return f;
        });
    }

    /**
     * Request to write a message via this ChannelHandlerContext through the ChannelPipeline from Netty's event loop thread.
     *
     * @param flush flush immediately
     * @param msg   message to write
     * @param listeners function for accessing channel future of the netty write
     * @return CompletionStage completed when request is made from event loop thread, containing future of actual write
     */
    void write(boolean flush, Object msg, Function<ChannelFuture, ChannelFuture> listeners) {
        // Ordered writes
        writeFuture = writeFuture.thenCompose(f -> writeInt(flush, msg, listeners));
    }

    /**
     * Request to write a message via this ChannelHandlerContext through the ChannelPipeline from Netty's event loop thread.
     *
     * @param flush flush immediately
     * @param msg   message to write
     * @return CompletionStage completed when request is made from event loop thread, containing future of actual write
     */
    void write(boolean flush, Object msg) {
        // Ordered writes
        writeFuture = writeFuture.thenCompose(f -> writeInt(flush, msg, Function.identity()));
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

    WebClientServiceRequest serviceRequest() {
        return channel.attr(REQUEST).get().configuration().clientServiceRequest();
    }

    boolean isConnectionReset() {
        return channel.attr(RECEIVED).get().isDone() || !channel.isActive();
    }

    @Override
    public String toString() {
        return "NettyChannel{"
                + "ctx=" + channel.toString()
                + '}';
    }
}
