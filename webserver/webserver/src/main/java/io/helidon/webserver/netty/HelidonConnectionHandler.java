/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapter;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;

import static io.netty.handler.logging.LogLevel.INFO;

/**
 * Class HelidonConnectionHandler.
 */
class HelidonConnectionHandler extends HttpToHttp2ConnectionHandler implements Http2FrameListener {

    private final InboundHttp2ToHttpAdapter inboundAdapter;

    HelidonConnectionHandler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                             Http2Settings initialSettings, int maxContentLength) {
        super(decoder, encoder, initialSettings, true);
        inboundAdapter = new InboundHttp2ToHttpAdapterBuilder(decoder.connection())
                .maxContentLength(maxContentLength)
                .propagateSettings(true)
                .validateHttpHeaders(true)
                .build();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent) {
            HttpServerUpgradeHandler.UpgradeEvent upgradeEvent = (HttpServerUpgradeHandler.UpgradeEvent) evt;

            // Map initial request headers to HTTP2
            FullHttpRequest request = upgradeEvent.upgradeRequest();
            Http2Headers headers = new DefaultHttp2Headers()
                    .method(HttpMethod.GET.asciiName())
                    .path(request.uri())
                    .scheme(HttpScheme.HTTP.name());
            CharSequence host = request.headers().get(HttpHeaderNames.HOST);
            if (host != null) {
                headers.authority(host);
            }

            // Process mapped headers
            onHeadersRead(ctx, 1, headers, 0, true);
        }
        super.userEventTriggered(ctx, evt);
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

    static final class HelidonHttp2ConnectionHandlerBuilder extends
            AbstractHttp2ConnectionHandlerBuilder<HelidonConnectionHandler, HelidonHttp2ConnectionHandlerBuilder> {

        private static final Http2FrameLogger LOGGER = new Http2FrameLogger(INFO, HelidonConnectionHandler.class);

        private int maxContentLength;

        HelidonHttp2ConnectionHandlerBuilder() {
            frameLogger(LOGGER);
        }

        public HelidonHttp2ConnectionHandlerBuilder maxContentLength(int maxContentLength) {
            this.maxContentLength = maxContentLength;
            return this;
        }

        @Override
        public HelidonConnectionHandler build() {
            return super.build();
        }

        @Override
        protected HelidonConnectionHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                                                 Http2Settings initialSettings) {
            HelidonConnectionHandler handler = new HelidonConnectionHandler(decoder, encoder, initialSettings,
                    maxContentLength);
            frameListener(handler);
            return handler;
        }
    }
}

