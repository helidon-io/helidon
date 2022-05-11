/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.webserver.ForwardingHandler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

class WebSocketUpgradeCodec implements HttpServerUpgradeHandler.UpgradeCodec {

    private static final Logger LOGGER = Logger.getLogger(WebSocketUpgradeCodec.class.getName());

    private final WebSocketRouting webSocketRouting;
    private String path;
    private WebSocketHandler wsHandler;

    WebSocketUpgradeCodec(WebSocketRouting webSocketRouting) {
        this.webSocketRouting = webSocketRouting;
    }

    @Override
    public Collection<CharSequence> requiredUpgradeHeaders() {
        // Only Connection header value!
        return List.of("Upgrade");
    }

    @Override
    public boolean prepareUpgradeResponse(ChannelHandlerContext ctx,
                                          FullHttpRequest upgradeRequest,
                                          HttpHeaders upgradeResponseHeaders) {
        try {
            path = upgradeRequest.uri();
            upgradeResponseHeaders.remove("upgrade");
            upgradeResponseHeaders.remove("connection");
            this.wsHandler = new WebSocketHandler(ctx, path, upgradeRequest, upgradeResponseHeaders, webSocketRouting);
            return true;
        } catch (Throwable cause) {
            LOGGER.log(Level.SEVERE, "Error during upgrade to WebSocket", cause);
            return false;
        }
    }

    @Override
    public void upgradeTo(ChannelHandlerContext ctx, FullHttpRequest upgradeRequest) {
        if (ctx.pipeline().get(ForwardingHandler.class) != null) {
            ctx.pipeline().remove(ForwardingHandler.class);
        }
        ctx.pipeline().addLast(new WebSocketServerProtocolHandler(path, null, true));
        ctx.pipeline().addLast(this.wsHandler);
        // Handshake done by tyrus
        ctx.pipeline().remove("io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandshakeHandler");
        this.wsHandler.open(ctx);
    }
}
