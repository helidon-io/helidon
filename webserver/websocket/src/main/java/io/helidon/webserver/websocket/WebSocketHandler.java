/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.common.http.Parameters;
import io.helidon.common.http.UriComponent;
import io.helidon.common.reactive.BufferedEmittingPublisher;
import io.helidon.common.reactive.Multi;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import jakarta.websocket.CloseReason;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Extension;
import jakarta.websocket.server.ServerEndpointConfig;
import org.glassfish.tyrus.core.RequestContext;
import org.glassfish.tyrus.core.TyrusUpgradeResponse;
import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.server.TyrusServerContainer;
import org.glassfish.tyrus.spi.CompletionHandler;
import org.glassfish.tyrus.spi.Connection;
import org.glassfish.tyrus.spi.WebSocketEngine;
import org.glassfish.tyrus.spi.Writer;

import static jakarta.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION;

class WebSocketHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger LOGGER = Logger.getLogger(WebSocketHandler.class.getName());

    private static final int MAX_RETRIES = 5;

    private final WebSocketEngine engine;
    private final String path;
    private final String queryString;
    private final FullHttpRequest upgradeRequest;
    private final HttpHeaders upgradeResponseHeaders;
    private final WebSocketRouting webSocketRouting;
    private final TyrusServerContainer tyrusServerContainer;
    private volatile Connection connection;
    private final WebSocketEngine.UpgradeInfo upgradeInfo;
    private final BufferedEmittingPublisher<ByteBuf> emitter;

    WebSocketHandler(ChannelHandlerContext ctx, String path,
                            FullHttpRequest upgradeRequest,
                            HttpHeaders upgradeResponseHeaders,
                            WebSocketRouting webSocketRouting) {
        int k = path.indexOf('?');
        if (k > 0) {
            this.path = path.substring(0, k);
            this.queryString = path.substring(k + 1);
        } else {
            this.path = path;
            this.queryString = "";
        }
        this.upgradeRequest = upgradeRequest;
        this.upgradeResponseHeaders = upgradeResponseHeaders;
        this.webSocketRouting = webSocketRouting;
        this.emitter = BufferedEmittingPublisher.create();

        // Create container and WebSocket engine
        Set<Class<?>> allEndpointClasses = webSocketRouting.getRoutes().stream()
                .map(WebSocketRoute::endpointClass)
                .collect(Collectors.toSet());
        tyrusServerContainer = new TyrusServerContainer(allEndpointClasses) {
            private final WebSocketEngine engine =
                    TyrusWebSocketEngine.builder(this).build();

            @Override
            public void register(Class<?> endpointClass) {
                throw new UnsupportedOperationException("Use TyrusWebSocketEngine for registration");
            }

            @Override
            public void register(ServerEndpointConfig serverEndpointConfig) {
                throw new UnsupportedOperationException("Use TyrusWebSocketEngine for registration");
            }

            @Override
            public Set<Extension> getInstalledExtensions() {
                return webSocketRouting.getExtensions();
            }

            @Override
            public WebSocketEngine getWebSocketEngine() {
                return engine;
            }
        };

        // Register classes with context path "/"
        WebSocketEngine engine = tyrusServerContainer.getWebSocketEngine();

        webSocketRouting.getRoutes().forEach(wsRoute -> {
            try {
                if (wsRoute.serverEndpointConfig() != null) {
                    LOGGER.log(Level.FINE, () -> "Registering ws endpoint "
                            + wsRoute.path()
                            + wsRoute.serverEndpointConfig().getPath());
                    engine.register(wsRoute.serverEndpointConfig(), wsRoute.path());
                } else {
                    LOGGER.log(Level.FINE, () -> "Registering annotated ws endpoint " + wsRoute.path());
                    engine.register(wsRoute.endpointClass(), wsRoute.path());
                }
            } catch (DeploymentException e) {
                throw new RuntimeException(e);
            }
        });

        this.engine = tyrusServerContainer.getWebSocketEngine();
        this.upgradeInfo = upgrade(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.log(Level.SEVERE, "WS handler ERROR ", cause);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        if (connection != null) {
            connection.close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "Client connection closed"));
        }

        tyrusServerContainer.shutdown();
        super.channelUnregistered(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf byteBuf) {
            emitter.emit(byteBuf.copy());
        }
    }

    private void sendBytesToTyrus(ChannelHandlerContext ctx, ByteBuf byteBuf) {
        // Pass all data to Tyrus spi
        ByteBuffer nioBuffer = byteBuf.nioBuffer();
        int retries = MAX_RETRIES;
        while (nioBuffer.remaining() > 0 && retries-- > 0) {
            connection.getReadHandler().handle(nioBuffer);
        }
        byteBuf.release();

        // If we can't push all data to Tyrus, cancel and report problem
        if (retries == 0) {
            ctx.close();
            connection.close(
                    new CloseReason(UNEXPECTED_CONDITION, "Tyrus did not consume all data after " + MAX_RETRIES + " retries")
            );
        }
    }

    WebSocketEngine.UpgradeInfo upgrade(ChannelHandlerContext ctx) {
        LOGGER.fine("Initiating WebSocket handshake ...");

        // Create Tyrus request context, copy request headers and query params
        Map<String, String[]> paramsMap = new HashMap<>();
        Parameters params = UriComponent.decodeQuery(queryString, true);
        params.toMap().forEach((key, value) -> paramsMap.put(key, value.toArray(new String[0])));
        RequestContext requestContext = RequestContext.Builder.create()
                .requestURI(URI.create(path))      // excludes context path
                .queryString(queryString)
                .parameterMap(paramsMap)
                .build();
        upgradeRequest.headers().forEach(e -> requestContext.getHeaders().put(e.getKey(), List.of(e.getValue())));

        // Use Tyrus to process a WebSocket upgrade request
        final TyrusUpgradeResponse upgradeResponse = new TyrusUpgradeResponse();
        final WebSocketEngine.UpgradeInfo upgradeInfo = engine.upgrade(requestContext, upgradeResponse);

        upgradeResponse.getHeaders().forEach(this.upgradeResponseHeaders::add);
        return upgradeInfo;
    }

    void open(ChannelHandlerContext ctx) {
        Writer writer = new Writer() {

            @Override
            public void close() throws IOException {
                ctx.write(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }

            @Override
            public void write(ByteBuffer byteBuffer, CompletionHandler<ByteBuffer> completionHandler) {
                ctx.writeAndFlush(Unpooled.wrappedBuffer(byteBuffer))
                        .addListener(f -> {
                            if (f.isSuccess()) {
                                completionHandler.completed(byteBuffer);
                            } else {
                                completionHandler.failed(f.cause());
                            }
                        });
            }
        };

        if (webSocketRouting.getExecutorService() != null) {
            CompletableFuture.supplyAsync(() -> {
                this.connection = upgradeInfo.createConnection(writer, WebSocketHandler::close);
                return ctx;
            }, webSocketRouting.getExecutorService()).thenAccept(c -> Multi.create(emitter)
                    .observeOn(webSocketRouting.getExecutorService())
                    .forEach(byteBuf -> sendBytesToTyrus(c, byteBuf))
                    .onError(this::logError)
            );
        } else {
            this.connection = upgradeInfo.createConnection(writer, WebSocketHandler::close);
            Multi.create(emitter)
                    .forEach(byteBuf -> sendBytesToTyrus(ctx, byteBuf))
                    .onError(this::logError);
        }

        ctx.channel().config().setAutoRead(true);
    }

    private void logError(Throwable throwable){
        LOGGER.log(Level.SEVERE, "WS handler ERROR ", throwable);
    }

    private static void close(CloseReason closeReason) {
        LOGGER.fine(() -> "Connection closed: " + closeReason);
    }
}
