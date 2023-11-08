/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.tests.http2;

import java.net.InetSocketAddress;

import io.helidon.common.Builder;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
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
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import static java.lang.System.Logger.Level.INFO;

class MockHttp2Server {
    static System.Logger LOGGER = System.getLogger(MockHttp2Server.class.getName());
    private final EventLoopGroup group;
    private final InetSocketAddress socketAddress;

    public MockHttp2Server(EventLoopGroup group, InetSocketAddress socketAddress) {
        this.group = group;
        this.socketAddress = socketAddress;
    }

    static MockHttp2ServerBuilder builder() {
        return new MockHttp2ServerBuilder();
    }

    int port() {
        return socketAddress.getPort();
    }

    void shutdown() {
        group.shutdownGracefully();
    }

    @FunctionalInterface
    interface Handler {
        void handle(ChannelHandlerContext ctx,
                    int streamId,
                    Http2Headers headers,
                    ByteBuf payload,
                    Http2ConnectionEncoder encoder) throws Http2Exception;
    }

    static class MockHttp2ServerBuilder implements Builder<MockHttp2ServerBuilder, MockHttp2Server> {

        private int port = 0;

        private Handler onHeadersHandler = (ctx, streamId, headers, unused, encoder) -> {
            Http2Headers h = new DefaultHttp2Headers().status(HttpResponseStatus.OK.codeAsText());
            encoder.writeHeaders(ctx, streamId, h, 0, false, ctx.newPromise());
            encoder.writeData(ctx,
                              streamId,
                              Unpooled.wrappedBuffer("Hello World!".getBytes()),
                              0,
                              true,
                              ctx.newPromise());
        };
        private Handler onGoAwayHandler = (ctx, streamId, headers, data,encoder) -> {

        };

        @Override
        public MockHttp2Server build() {
            EventLoopGroup group = new NioEventLoopGroup();
            var initializer =
                    new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel channel) throws Exception {
                            HttpServerCodec codec = new HttpServerCodec(
                                    4096,
                                    16384,
                                    8192,
                                    true,
                                    128);

                            var mockHandler = new MockHttp2HandlerBuilder(MockHttp2ServerBuilder.this).build();

                            var upgradeHandler =
                                    new HttpServerUpgradeHandler(codec,
                                                                 protocol -> new Http2ServerUpgradeCodec(mockHandler),
                                                                 64 * 1024);
                            var cleartextHttp2ServerUpgradeHandler =
                                    new CleartextHttp2ServerUpgradeHandler(codec, upgradeHandler, mockHandler);
                            channel.pipeline()
                                    .addLast(cleartextHttp2ServerUpgradeHandler);
                        }
                    };
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.group(group)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(initializer);

            Channel ch;
            try {
                ch = b.bind(port).sync().channel();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            InetSocketAddress socketAddress = (InetSocketAddress) ch.localAddress();

            LOGGER.log(INFO, "HTTP/2 Server is listening on http://127.0.0.1:" + socketAddress.getPort() + '/');

            return new MockHttp2Server(group, socketAddress);
        }

        MockHttp2ServerBuilder port(int port) {
            this.port = port;
            return this;
        }

        MockHttp2ServerBuilder onHeaders(Handler onHeadersHandler) {
            this.onHeadersHandler = onHeadersHandler;
            return this;
        }

        MockHttp2ServerBuilder onGoAway(Handler handlerHandler) {
            this.onGoAwayHandler = handlerHandler;
            return this;
        }
    }

    static abstract class Http2Handler extends Http2ConnectionHandler implements Http2FrameListener {
        protected Http2Handler(Http2ConnectionDecoder decoder,
                               Http2ConnectionEncoder encoder,
                               Http2Settings initialSettings) {
            super(decoder, encoder, initialSettings);
        }

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream)
                throws Http2Exception {

            return 0;
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endOfStream)
                throws Http2Exception {
        }

        @Override
        public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive)
                throws Http2Exception {

        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {

        }

        @Override
        public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {

        }

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {

        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception {

        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, long data) throws Http2Exception {

        }

        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx,
                                      int streamId,
                                      int promisedStreamId,
                                      Http2Headers headers,
                                      int padding) throws Http2Exception {

        }

        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) throws Http2Exception {
        }

        @Override
        public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf payload)
                throws Http2Exception {

        }
    }

    static final class MockHttp2HandlerBuilder
            extends AbstractHttp2ConnectionHandlerBuilder<Http2Handler, MockHttp2HandlerBuilder> {

        private static final Http2FrameLogger LOGGER = new Http2FrameLogger(LogLevel.DEBUG, MockHttp2Server.class);
        private final MockHttp2ServerBuilder serverBuilder;

        MockHttp2HandlerBuilder(MockHttp2ServerBuilder serverBuilder) {
            this.serverBuilder = serverBuilder;
            frameLogger(LOGGER);
        }

        @Override
        public Http2Handler build() {
            return super.build();
        }

        @Override
        protected Http2Handler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                                     Http2Settings initialSettings) {
            Http2Handler handler = new Http2Handler(decoder, encoder, initialSettings) {

                @Override
                public void onHeadersRead(ChannelHandlerContext ctx,
                                          int streamId,
                                          Http2Headers headers,
                                          int streamDependency,
                                          short weight,
                                          boolean exclusive,
                                          int padding,
                                          boolean endOfStream) throws Http2Exception {
                    serverBuilder.onHeadersHandler.handle(ctx, streamId, headers, null, encoder());
                }

                @Override
                public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
                        throws Http2Exception {
                    serverBuilder.onGoAwayHandler.handle(ctx, lastStreamId, null, debugData, encoder());
                }
            };

            frameListener(handler);
            return handler;
        }

    }
}
