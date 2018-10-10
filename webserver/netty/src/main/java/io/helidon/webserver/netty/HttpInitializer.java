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

import io.helidon.webserver.Routing;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapter;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AsciiString;

import static io.netty.handler.logging.LogLevel.INFO;

/**
 * The HttpInitializer.
 */
class HttpInitializer extends ChannelInitializer<SocketChannel> {

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

    // @Override
    public void initChannel_old(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();

        SSLEngine sslEngine = null;
        if (sslContext != null) {
            SslHandler sslHandler = sslContext.newHandler(ch.alloc());
            sslEngine = sslHandler.engine();
            p.addLast(sslHandler);
        }

        p.addLast(new HttpRequestDecoder());
        // Uncomment the following line if you don't want to handle HttpChunks.
        //        p.addLast(new HttpObjectAggregator(1048576));
        p.addLast(new HttpResponseEncoder());
        // Remove the following line if you don't want automatic content compression.
        //p.addLast(new HttpContentCompressor());
        p.addLast(new ForwardingHandler(routing, webServer, sslEngine, queues));

        ch.eventLoop().execute(this::clearQueues);
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

        final HttpServerCodec sourceCodec = new HttpServerCodec();
        final HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory);
        final HelidonConnectionHandler helidonHandler = new HelidonHttp2ConnectionHandlerBuilder().build();
        final CleartextHttp2ServerUpgradeHandler cleartextHttp2ServerUpgradeHandler =
                new CleartextHttp2ServerUpgradeHandler(sourceCodec, upgradeHandler, helidonHandler);

        p.addLast(new HelidonEventLogger());
        p.addLast(cleartextHttp2ServerUpgradeHandler);
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

    private static class HelidonEventLogger extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            System.out.println("Event Triggered: " + evt);
            ctx.fireUserEventTriggered(evt);
        }
    }

    private static class HelidonConnectionHandler extends Http2ConnectionHandler implements Http2FrameListener {

        private final InboundHttp2ToHttpAdapter inboundAdapter;

        HelidonConnectionHandler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                                 Http2Settings initialSettings) {
            super(decoder, encoder, initialSettings);
            inboundAdapter = new InboundHttp2ToHttpAdapterBuilder(decoder.connection())
                    .maxContentLength(64 * 1024)
                    .propagateSettings(true)
                    .validateHttpHeaders(true)
                    .build();
        }

        private static Http2Headers http1HeadersToHttp2Headers(FullHttpRequest request) {
            CharSequence host = request.headers().get(HttpHeaderNames.HOST);
            Http2Headers http2Headers = new DefaultHttp2Headers()
                    .method(HttpMethod.GET.asciiName())
                    .path(request.uri())
                    .scheme(HttpScheme.HTTP.name());
            if (host != null) {
                http2Headers.authority(host);
            }
            return http2Headers;
        }

        /**
         * Handles the cleartext HTTP upgrade event. If an upgrade occurred, sends a simple response via HTTP/2
         * on stream 1 (the stream specifically reserved for cleartext HTTP upgrade).
         */
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent) {
                HttpServerUpgradeHandler.UpgradeEvent upgradeEvent =
                        (HttpServerUpgradeHandler.UpgradeEvent) evt;
                onHeadersRead(ctx, 1, http1HeadersToHttp2Headers(upgradeEvent.upgradeRequest()), 0 , true);
            }
            super.userEventTriggered(ctx, evt);
        }

        public void onStreamRemoved(Http2Stream stream) {
            inboundAdapter.onStreamRemoved(stream);
        }

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                              boolean endOfStream) throws Http2Exception {
            return inboundAdapter.onDataRead(ctx, streamId, data, padding, endOfStream);
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                                  boolean endOfStream) throws Http2Exception {
            inboundAdapter.onHeadersRead(ctx, streamId, headers, padding, endOfStream);
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                                  short weight, boolean exclusive, int padding, boolean endOfStream)
                throws Http2Exception {
            inboundAdapter.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding,
                    endOfStream);
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
            inboundAdapter.onRstStreamRead(ctx, streamId, errorCode);
        }

        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                      Http2Headers headers, int padding) throws Http2Exception {
            inboundAdapter.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
        }

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
            inboundAdapter.onSettingsRead(ctx, settings);
        }

        @Override
        public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
                                   boolean exclusive) throws Http2Exception {
            inboundAdapter.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
        }

        @Override
        public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
            inboundAdapter.onSettingsAckRead(ctx);
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
            inboundAdapter.onPingRead(ctx, data);
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
            inboundAdapter.onPingAckRead(ctx, data);
        }

        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode,
                                 ByteBuf debugData) throws Http2Exception {
            inboundAdapter.onGoAwayRead(ctx, lastStreamId, errorCode, debugData);
        }

        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
                throws Http2Exception {
            inboundAdapter.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
        }

        @Override
        public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
                                   ByteBuf payload) throws Http2Exception {
            inboundAdapter.onUnknownFrame(ctx, frameType, streamId, flags, payload);
        }

        public void onStreamAdded(Http2Stream stream) {
            inboundAdapter.onStreamAdded(stream);
        }

        public void onStreamActive(Http2Stream stream) {
            inboundAdapter.onStreamActive(stream);
        }

        public void onStreamHalfClosed(Http2Stream stream) {
            inboundAdapter.onStreamHalfClosed(stream);
        }

        public void onStreamClosed(Http2Stream stream) {
            inboundAdapter.onStreamClosed(stream);
        }

        public void onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
            inboundAdapter.onGoAwaySent(lastStreamId, errorCode, debugData);
        }

        public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
            inboundAdapter.onGoAwayReceived(lastStreamId, errorCode, debugData);
        }
    }

    private static final class HelidonHttp2ConnectionHandlerBuilder
            extends AbstractHttp2ConnectionHandlerBuilder<HelidonConnectionHandler, HelidonHttp2ConnectionHandlerBuilder> {

        private static final Http2FrameLogger logger = new Http2FrameLogger(INFO, HelidonConnectionHandler.class);

        HelidonHttp2ConnectionHandlerBuilder() {
            frameLogger(logger);
        }

        @Override
        public HelidonConnectionHandler build() {
            return super.build();
        }

        @Override
        protected HelidonConnectionHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                                                 Http2Settings initialSettings) {
            HelidonConnectionHandler handler = new HelidonConnectionHandler(decoder, encoder, initialSettings);
            frameListener(handler);
            return handler;
        }
    }
}
