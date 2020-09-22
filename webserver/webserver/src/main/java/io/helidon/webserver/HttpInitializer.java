/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;

import io.helidon.common.http.DataChunk;
import io.helidon.webserver.HelidonConnectionHandler.HelidonHttp2ConnectionHandlerBuilder;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AsciiString;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;

/**
 * The HttpInitializer.
 */
class HttpInitializer extends ChannelInitializer<SocketChannel> {
    private static final Logger LOGGER = Logger.getLogger(HttpInitializer.class.getName());
    static final AttributeKey<String> CERTIFICATE_NAME = AttributeKey.valueOf("certificate_name");

    private final SslContext sslContext;
    private final NettyWebServer webServer;
    private final SocketConfiguration soConfig;
    private final Routing routing;
    private final Queue<ReferenceHoldingQueue<DataChunk>> queues = new ConcurrentLinkedQueue<>();

    HttpInitializer(SocketConfiguration soConfig,
                    SslContext sslContext,
                    Routing routing,
                    NettyWebServer webServer) {
        this.soConfig = soConfig;
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
            sslHandler.handshakeFuture().addListener(future -> obtainClientCN(future, ch, sslHandler));
        }

        // Set up HTTP/2 pipeline if feature is enabled
        ServerConfiguration serverConfig = webServer.configuration();
        HttpRequestDecoder requestDecoder = new HttpRequestDecoder(soConfig.maxInitialLineLength(),
                                                                   soConfig.maxHeaderSize(),
                                                                   soConfig.maxChunkSize(),
                                                                   soConfig.validateHeaders(),
                                                                   soConfig.initialBufferSize());
        if (serverConfig.isHttp2Enabled()) {
            ExperimentalConfiguration experimental = serverConfig.experimental();
            Http2Configuration http2Config = experimental.http2();
            HttpServerCodec sourceCodec = new HttpServerCodec();
            HelidonConnectionHandler helidonHandler = new HelidonHttp2ConnectionHandlerBuilder()
                    .maxContentLength(http2Config.maxContentLength()).build();
            HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(sourceCodec,
                    protocol -> AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)
                            ? new Http2ServerUpgradeCodec(helidonHandler) : null,
                    http2Config.maxContentLength());

            CleartextHttp2ServerUpgradeHandler cleartextHttp2ServerUpgradeHandler =
                    new CleartextHttp2ServerUpgradeHandler(sourceCodec, upgradeHandler, helidonHandler);

            p.addLast(cleartextHttp2ServerUpgradeHandler);
            p.addLast(new HelidonEventLogger());
        } else {
            p.addLast(requestDecoder);
            // Uncomment the following line if you don't want to handle HttpChunks.
            //        p.addLast(new HttpObjectAggregator(1048576));
            p.addLast(new HttpResponseEncoder());

            // Enable compression via "Accept-Encoding" header if configured
            if (serverConfig.enableCompression()) {
                p.addLast(new HttpContentCompressor());
            }
        }

        // Helidon's forwarding handler
        p.addLast(new ForwardingHandler(routing, webServer, sslEngine, queues, requestDecoder));

        // Cleanup queues as part of event loop
        ch.eventLoop().execute(this::clearQueues);
    }

    private void obtainClientCN(Future<? super Channel> future, SocketChannel ch, SslHandler sslHandler) {
        if (future.cause() == null) {
            try {
                Certificate[] peerCertificates = sslHandler.engine().getSession().getPeerCertificates();
                if (peerCertificates.length >= 1) {
                    Certificate certificate = peerCertificates[0];
                    X509Certificate cert = (X509Certificate) certificate;
                    Principal principal = cert.getSubjectDN();

                    int start = principal.getName().indexOf("CN=");
                    String tmpName = "Unknown CN";
                    if (start >= 0) {
                        tmpName = principal.getName().substring(start + 3);
                        int end = tmpName.indexOf(",");
                        if (end > 0) {
                            tmpName = tmpName.substring(0, end);
                        }
                    }
                    ch.attr(CERTIFICATE_NAME).set(tmpName);
                }
            } catch (SSLPeerUnverifiedException ignored) {
                //User not authenticated. Client authentication probably set to OPTIONAL or NONE
            }

        }
    }

    private static final class HelidonEventLogger extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            LOGGER.finer(() -> "Event Triggered: " + evt);
            ctx.fireUserEventTriggered(evt);
        }
    }
}
