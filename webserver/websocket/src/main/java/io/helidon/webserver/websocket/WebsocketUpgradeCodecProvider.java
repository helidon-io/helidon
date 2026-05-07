/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
package io.helidon.webserver.websocket;

import java.util.Optional;

import io.helidon.common.http.Http;
import io.helidon.webserver.Router;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.spi.UpgradeCodecProvider;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;

/**
 * Service providing WebSocket upgrade codec for Helidon webserver.
 */
public class WebsocketUpgradeCodecProvider implements UpgradeCodecProvider {

    /**
     * Creates a new {@link WebsocketUpgradeCodecProvider}.
     * @deprecated Only intended for service loader, do not instantiate
     */
    @Deprecated
    public WebsocketUpgradeCodecProvider() {
    }

    @Override
    public CharSequence clearTextProtocol() {
        return "websocket";
    }

    @Override
    public Optional<String> tlsProtocol() {
        return Optional.empty();
    }

    @Override
    public HttpServerUpgradeHandler.UpgradeCodec upgradeCodec(HttpServerCodec httpServerCodec,
                                                              Router router,
                                                              int maxContentLength) {
        WebSocketRouting routing = router.routing(WebSocketRouting.class, null);
        if (routing != null) {
            return new WebSocketUpgradeCodec(routing);
        }
        return null;
    }

    @Override
    public Optional<RoutedUpgrade> routedUpgrade(Router router, HttpRequest request) {
        WebSocketRouting routing = router.routing(WebSocketRouting.class, null);
        if (routing == null) {
            return Optional.empty();
        }
        FullHttpRequest copiedRequest = new DefaultFullHttpRequest(request.protocolVersion(),
                                                                   request.method(),
                                                                   request.uri(),
                                                                   Unpooled.EMPTY_BUFFER);
        copiedRequest.headers().set(request.headers());
        return Optional.of(new WebSocketRoutedUpgrade(routing, copiedRequest));
    }

    private static final class WebSocketRoutedUpgrade implements RoutedUpgrade {
        private final WebSocketRouting routing;
        private final FullHttpRequest request;
        private WebSocketUpgradeCodec upgradeCodec;

        private WebSocketRoutedUpgrade(WebSocketRouting routing, FullHttpRequest request) {
            this.routing = routing;
            this.request = request;
        }

        @Override
        public boolean prepareResponse(ChannelHandlerContext ctx, ServerResponse response) {
            upgradeCodec = new WebSocketUpgradeCodec(routing);
            if (!upgradeCodec.prepareRoutedUpgradeResponse(ctx, request, response)) {
                return false;
            }
            response.status(Http.Status.SWITCHING_PROTOCOLS_101);
            response.headers().put(HttpHeaderNames.CONNECTION.toString(), HttpHeaderValues.UPGRADE.toString());
            response.headers().put(HttpHeaderNames.UPGRADE.toString(), "websocket");
            response.headers().remove(HttpHeaderNames.CONTENT_LENGTH.toString());
            return true;
        }

        @Override
        public void upgrade(ChannelHandlerContext ctx) {
            HttpServerCodec sourceCodec = ctx.pipeline().get(HttpServerCodec.class);
            if (sourceCodec != null) {
                sourceCodec.upgradeFrom(ctx);
            }
            HttpServerUpgradeHandler upgradeHandler = ctx.pipeline().get(HttpServerUpgradeHandler.class);
            if (upgradeHandler != null) {
                ctx.pipeline().remove(upgradeHandler);
            }
            upgradeCodec.upgradeTo(ctx, request);
        }
    }
}
