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

import javax.net.ssl.SSLEngine;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import io.helidon.webserver.Routing;
import io.helidon.webserver.netty.HelidonConnectionHandler.HelidonHttp2ConnectionHandlerBuilder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AsciiString;

/**
 * The HttpInitializer.
 */
class HttpInitializer extends ChannelInitializer<SocketChannel> {
    private static final Logger LOGGER = Logger.getLogger(HttpInitializer.class.getName());

    private final SslContext sslContext;
    private final NettyWebServer webServer;
    private final Routing routing;
    private final Queue<ReferenceHoldingQueue<ByteBufRequestChunk>> queues = new ConcurrentLinkedQueue<>();

    HttpInitializer(SslContext sslContext, Routing routing, NettyWebServer webServer) {
        this.routing = routing;
        this.sslContext = sslContext;
        this.webServer = webServer;
    }

    private void clearQueues() {
        queues.removeIf(ReferenceHoldingQueue::release);
    }

    void queuesShutdown() {
        queues.removeIf(queue -> {
            queue.shutdown();
            return true;
        });
    }

    @Override
    public void initChannel(SocketChannel ch) {
        final ChannelPipeline p = ch.pipeline();

        SSLEngine sslEngine = null;
        if (sslContext != null) {
            SslHandler sslHandler = sslContext.newHandler(ch.alloc());
            sslEngine = sslHandler.engine();
            p.addLast(sslHandler);
        }

        HttpServerCodec sourceCodec = new HttpServerCodec();
        HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory);
        HelidonConnectionHandler helidonHandler = new HelidonHttp2ConnectionHandlerBuilder().build();
        CleartextHttp2ServerUpgradeHandler cleartextHttp2ServerUpgradeHandler =
                new CleartextHttp2ServerUpgradeHandler(sourceCodec, upgradeHandler, helidonHandler);

        p.addLast(cleartextHttp2ServerUpgradeHandler);
        p.addLast(new HelidonEventLogger());
        p.addLast(new ForwardingHandler(routing, webServer, sslEngine, queues));
    }

    private static final HttpServerUpgradeHandler.UpgradeCodecFactory upgradeCodecFactory =
            protocol -> {
                if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
                    return new Http2ServerUpgradeCodec(new HelidonHttp2ConnectionHandlerBuilder().build());
                } else {
                    return null;
                }
            };

    private static final class HelidonEventLogger extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            LOGGER.info("Event Triggered: " + evt);
            ctx.fireUserEventTriggered(evt);
        }
    }
}
