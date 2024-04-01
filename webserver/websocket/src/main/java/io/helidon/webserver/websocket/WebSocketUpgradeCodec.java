/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.webserver.ForwardingHandler;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.glassfish.tyrus.core.TyrusUpgradeResponse;

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
            upgradeResponseHeaders.remove(Http.Header.UPGRADE);
            upgradeResponseHeaders.remove(Http.Header.CONNECTION);
            wsHandler = new WebSocketHandler(ctx, path, upgradeRequest, upgradeResponseHeaders, webSocketRouting);

            // if not 101 code, create and write to channel a custom user response of
            // type text/plain using reason as payload and return false back to Netty
            TyrusUpgradeResponse upgradeResponse = wsHandler.upgradeResponse();
            if (upgradeResponse.getStatus() != Http.Status.SWITCHING_PROTOCOLS_101.code()) {
                // prepare headers for failed response
                upgradeResponse.getHeaders().remove(Http.Header.UPGRADE);
                upgradeResponse.getHeaders().remove(Http.Header.CONNECTION);
                upgradeResponse.getHeaders().remove("sec-websocket-accept");
                HttpHeaders headers = new DefaultHttpHeaders();
                upgradeResponse.getHeaders().forEach(headers::add);

                // set payload as text/plain with reason phrase
                headers.add(Http.Header.CONTENT_TYPE, "text/plain");
                String reasonPhrase = upgradeResponse.getReasonPhrase() == null ? ""
                        : upgradeResponse.getReasonPhrase();
                HttpResponse r = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                        HttpResponseStatus.valueOf(upgradeResponse.getStatus()),
                        Unpooled.wrappedBuffer(reasonPhrase.getBytes(Charset.defaultCharset())),
                        headers, EmptyHttpHeaders.INSTANCE);

                // write, flush and later close connection
                ChannelFuture writeComplete = ctx.writeAndFlush(r);
                writeComplete.addListener(ChannelFutureListener.CLOSE);
                return false;
            }
        } catch (Throwable cause) {
            LOGGER.log(Level.SEVERE, "Error during upgrade to WebSocket", cause);
            return false;
        }
        return true;
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
