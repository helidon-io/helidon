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

package io.helidon.microprofile.tyrus;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.DirectHandler;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RequestException;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.http1.spi.Http1RoutedUpgrade;
import io.helidon.webserver.http1.spi.Http1RoutedUpgrader;
import io.helidon.webserver.http1.spi.Http1UpgradeResponse;
import io.helidon.webserver.http1.spi.Http1UpgradeResult;
import io.helidon.webserver.spi.ServerConnection;
import io.helidon.webserver.websocket.WsConfig;
import io.helidon.webserver.websocket.WsUpgrader;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Extension;
import jakarta.websocket.server.ServerEndpointConfig;
import org.glassfish.tyrus.core.RequestContext;
import org.glassfish.tyrus.core.TyrusUpgradeResponse;
import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.server.TyrusServerContainer;
import org.glassfish.tyrus.spi.WebSocketEngine;

/**
 * Tyrus connection upgrade provider.
 */
public class TyrusUpgrader extends WsUpgrader implements Http1RoutedUpgrader {
    private static final System.Logger LOGGER = System.getLogger(TyrusUpgrader.class.getName());
    private static final HeaderName WS_ACCEPT = HeaderNames.create("Sec-WebSocket-Accept");

    private final EngineHolder engine = new EngineHolder();

    private TyrusUpgrader(WsConfig origins) {
        super(origins);
    }

    /**
     * Create a new configured instance of Tyrus upgrader.
     *
     * @param config configuration of WebSocket
     * @return a new HTTP/1 upgrader
     */
    public static TyrusUpgrader create(WsConfig config) {
        return new TyrusUpgrader(config);
    }

    @Override
    public ServerConnection upgrade(ConnectionContext ctx, HttpPrologue prologue, WritableHeaders<?> headers) {
        Optional<PreparedUpgrade> prepared = prepareUpgrade(ctx, prologue, headers);
        if (prepared.isEmpty()) {
            return null;
        }
        return prepared.get().upgrade();
    }

    @Override
    public Optional<Http1RoutedUpgrade> routedUpgrade(ConnectionContext ctx,
                                                     HttpPrologue prologue,
                                                     WritableHeaders<?> headers) {
        return prepareUpgrade(ctx, prologue, headers)
                .map(prepared -> (response, requestHeaders) -> prepared.upgrade(response, requestHeaders));
    }

    private Optional<PreparedUpgrade> prepareUpgrade(ConnectionContext ctx,
                                                     HttpPrologue prologue,
                                                     Headers headers) {
        // Initialize path and queryString
        String path = prologue.uriPath().path();
        UriQuery query = prologue.query();

        // Check if this a Tyrus route exists
        TyrusRouting routing = ctx.router()
                .routing(TyrusRouting.class, null);
        if (routing == null) {
            return Optional.empty();
        }
        TyrusRoute route = routing.findRoute(prologue);
        if (route == null) {
            return Optional.empty();
        }

        if (prologue.method() != Method.GET) {
            throw RequestException.builder()
                    .type(DirectHandler.EventType.BAD_REQUEST)
                    .message("WebSocket upgrade must use GET")
                    .build();
        }

        // Check required header
        if (!headers.contains(WS_KEY)) {
            throw RequestException.builder()
                    .type(DirectHandler.EventType.BAD_REQUEST)
                    .message("Missing Sec-WebSocket-Key header")
                    .build();
        }
        String wsKey = headers.get(WS_KEY).get();

        // Verify protocol version
        String version;
        if (headers.contains(WS_VERSION)) {
            version = headers.get(WS_VERSION).get();
        } else {
            version = SUPPORTED_VERSION;
        }
        if (!SUPPORTED_VERSION.equals(version)) {
            throw RequestException.builder()
                    .type(DirectHandler.EventType.BAD_REQUEST)
                    .message("Unsupported WebSocket Version")
                    .header(SUPPORTED_VERSION_HEADER)
                    .build();
        }

        // Validate origin
        if (headers.contains(HeaderNames.ORIGIN)) {
            validateOrigin(headers, headers.get(HeaderNames.ORIGIN).get());
        }

        return Optional.of(new PreparedUpgrade(ctx, prologue, headers, routing, query, path, wsKey, version));
    }

    private ServerResponseHeaders handshakeHeaders(ConnectionContext ctx, String wsKey) {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        responseHeaders.set(HeaderValues.create(HeaderNames.CONNECTION, "Upgrade"));
        responseHeaders.set(HeaderValues.create(HeaderNames.UPGRADE, "websocket"));
        responseHeaders.set(HeaderValues.create(WS_ACCEPT, hash(ctx, wsKey)));
        return responseHeaders;
    }

    private final class PreparedUpgrade {
        private final ConnectionContext ctx;
        private final HttpPrologue prologue;
        private final Headers headers;
        private final TyrusRouting routing;
        private final UriQuery query;
        private final String path;
        private final String wsKey;
        private final String version;

        private PreparedUpgrade(ConnectionContext ctx,
                                HttpPrologue prologue,
                                Headers headers,
                                TyrusRouting routing,
                                UriQuery query,
                                String path,
                                String wsKey,
                                String version) {
            this.ctx = ctx;
            this.prologue = prologue;
            this.headers = headers;
            this.routing = routing;
            this.query = query;
            this.path = path;
            this.wsKey = wsKey;
            this.version = version;
        }

        ServerConnection upgrade() {
            TyrusHandshake handshake = handshake();
            WebSocketEngine.UpgradeInfo upgradeInfo = handshake.upgradeInfo();
            if (upgradeInfo.getStatus() == WebSocketEngine.UpgradeStatus.NOT_APPLICABLE) {
                return null;
            }
            if (upgradeInfo.getStatus() != WebSocketEngine.UpgradeStatus.SUCCESS) {
                throw rejectedHandshakeException(handshake.upgradeResponse(), handshake.responseHeaders());
            }

            // todo support subprotocols (must be provided by route)
            // Sec-WebSocket-Protocol: sub-protocol (list provided in PROTOCOL header, separated by comma space
            DataWriter dataWriter = ctx.dataWriter();
            BufferData responseData = BufferData.growing(128);
            responseData.write("HTTP/1.1 101 Switching Protocols\r\n".getBytes(StandardCharsets.US_ASCII));
            handshakeHeaders(ctx, wsKey).forEach(header -> header.writeHttp1Header(responseData));
            responseData.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            dataWriter.write(responseData);

            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Upgraded to websocket version " + version);
            }
            return new TyrusConnection(ctx, prologue, upgradeInfo);
        }

        Http1UpgradeResult upgrade(Http1UpgradeResponse response, Headers requestHeaders) {
            if (!isWebSocketUpgrade(requestHeaders)) {
                return Http1UpgradeResult.notApplicable();
            }

            Optional<PreparedUpgrade> finalPrepared = prepareUpgrade(ctx, prologue, requestHeaders);
            if (finalPrepared.isEmpty()) {
                return Http1UpgradeResult.notApplicable();
            }

            PreparedUpgrade prepared = finalPrepared.get();
            TyrusHandshake handshake = prepared.handshake();
            WebSocketEngine.UpgradeInfo upgradeInfo = handshake.upgradeInfo();
            if (upgradeInfo.getStatus() == WebSocketEngine.UpgradeStatus.NOT_APPLICABLE) {
                return Http1UpgradeResult.notApplicable();
            }
            if (upgradeInfo.getStatus() != WebSocketEngine.UpgradeStatus.SUCCESS) {
                throw rejectedHandshakeException(handshake.upgradeResponse(), handshake.responseHeaders());
            }

            TyrusUpgradeResponse upgradeResponse = handshake.upgradeResponse();
            if (upgradeResponse.getStatus() != Status.SWITCHING_PROTOCOLS_101.code()) {
                handshake.responseHeaders().forEach(response.headers()::set);
                response.send(Status.create(upgradeResponse.getStatus(), upgradeResponse.getReasonPhrase()));
                return Http1UpgradeResult.responded();
            }

            // todo support subprotocols (must be provided by route)
            // Sec-WebSocket-Protocol: sub-protocol (list provided in PROTOCOL header, separated by comma space
            response.sendSwitchingProtocols(handshakeHeaders(ctx, prepared.wsKey));

            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Upgraded to websocket version " + prepared.version);
            }
            return Http1UpgradeResult.upgraded(new TyrusConnection(ctx, prologue, upgradeInfo));
        }

        private TyrusHandshake handshake() {
            return protocolHandshake(routing, headers, query, path);
        }
    }

    @Override
    protected Set<String> origins() {
        return super.origins();
    }

    TyrusHandshake protocolHandshake(TyrusRouting routing,
                                     Headers headers,
                                     UriQuery uriQuery,
                                     String path) {
        LOGGER.log(Level.DEBUG, "Initiating WebSocket handshake with Tyrus...");

        // Create Tyrus request context, copy request headers and query params
        Map<String, String[]> paramsMap = new HashMap<>();

        for (String name : uriQuery.names()) {
            paramsMap.put(name, uriQuery.all(name).toArray(new String[0]));
        }
        RequestContext requestContext = RequestContext.Builder.create()
                .requestURI(URI.create(path))      // excludes context path
                .queryString(uriQuery.rawValue())
                .parameterMap(paramsMap)
                .build();
        headers.forEach(e -> requestContext.getHeaders().put(e.name(), List.of(e.values())));

        // Protocol handshake with Tyrus
        final TyrusUpgradeResponse upgradeResponse = new TyrusUpgradeResponse();
        final WebSocketEngine.UpgradeInfo upgradeInfo = engine.get(routing).upgrade(requestContext, upgradeResponse);

        Set<Header> responseHeaders = new LinkedHashSet<>();
        upgradeResponse.getHeaders()
                .forEach((key, value) -> responseHeaders.add(
                        HeaderValues.create(
                                HeaderNames.create(key, key.toLowerCase(Locale.ROOT)),
                                value)));
        return new TyrusHandshake(upgradeInfo, upgradeResponse, Set.copyOf(responseHeaders));
    }

    private record TyrusHandshake(WebSocketEngine.UpgradeInfo upgradeInfo,
                                  TyrusUpgradeResponse upgradeResponse,
                                  Set<Header> responseHeaders) {
    }

    static RequestException rejectedHandshakeException(TyrusUpgradeResponse upgradeResponse,
                                                       Set<Header> responseHeaders) {
        RequestException.Builder builder = RequestException.builder()
                .type(DirectHandler.EventType.OTHER)
                .status(Status.create(upgradeResponse.getStatus(), upgradeResponse.getReasonPhrase()))
                .message("WebSocket handshake rejected");
        responseHeaders.forEach(builder::header);
        return builder.build();
    }

    // to initialize Tyrus only once
    private static final class EngineHolder {
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

        private volatile WebSocketEngine engine;

        WebSocketEngine get(TyrusRouting routing) {

            try {
                rwLock.readLock().lock();
                if (engine != null) {
                    return engine;
                }
            } finally {
                rwLock.readLock().unlock();
            }

            // was not there
            try {
                rwLock.writeLock().lock();
                if (engine != null) {
                    // competing thread managed to obtain the write lock before us and initailized it
                    return engine;
                }
                engine = createTyrusEngine(routing);
                return engine;
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        private WebSocketEngine createTyrusEngine(TyrusRouting routing) {
            TyrusCdiExtension extension = CDI.current().select(TyrusCdiExtension.class).get();
            Objects.requireNonNull(extension);
            TyrusServerContainer tyrusServerContainer = initializeTyrus(routing);
            return tyrusServerContainer.getWebSocketEngine();
        }

        private TyrusServerContainer initializeTyrus(TyrusRouting tyrusRouting) {
            Set<Class<?>> allEndpointClasses = tyrusRouting.routes()
                    .stream()
                    .map(TyrusRoute::endpointClass)
                    .collect(Collectors.toSet());

            TyrusServerContainer tyrusServerContainer = new TyrusServerContainer(allEndpointClasses) {
                private final WebSocketEngine engine =
                        TyrusWebSocketEngine.builder(this).build();

                @Override
                public void register(Class<?> endpointClass) {
                    throw new UnsupportedOperationException("Cannot register endpoint class");
                }

                @Override
                public void register(ServerEndpointConfig serverEndpointConfig) {
                    throw new UnsupportedOperationException("Cannot register ServerEndpointConfig");
                }

                @Override
                public Set<Extension> getInstalledExtensions() {
                    return tyrusRouting.extensions();
                }

                @Override
                public WebSocketEngine getWebSocketEngine() {
                    return engine;
                }
            };

            // Register classes with context path "/"
            WebSocketEngine engine = tyrusServerContainer.getWebSocketEngine();
            tyrusRouting.routes().forEach(route -> {
                try {
                    if (route.serverEndpointConfig() != null) {
                        LOGGER.log(Level.DEBUG, () -> "Registering ws endpoint "
                                + route.path()
                                + route.serverEndpointConfig().getPath());
                        engine.register(route.serverEndpointConfig(), route.path());
                    } else {
                        LOGGER.log(Level.DEBUG, () -> "Registering annotated ws endpoint " + route.path());
                        engine.register(route.endpointClass(), route.path());
                    }
                } catch (DeploymentException e) {
                    throw new RuntimeException(e);
                }
            });

            return tyrusServerContainer;
        }
    }
}
