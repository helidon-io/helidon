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

package io.helidon.microprofile.tyrus;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.DirectHandler;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.http.RequestException;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.uri.UriQuery;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.spi.ServerConnection;
import io.helidon.nima.websocket.webserver.WsUpgradeProvider;

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
@Weight(Weighted.DEFAULT_WEIGHT + 100)      // higher than base class
public class TyrusUpgradeProvider extends WsUpgradeProvider {
    private static final Logger LOGGER = Logger.getLogger(TyrusUpgradeProvider.class.getName());

    private String path;
    private String queryString;
    private final TyrusRouting tyrusRouting;
    private final WebSocketEngine engine;

    /**
     * @deprecated This constructor is only to be used by {@link java.util.ServiceLoader}.
     */
    @Deprecated()
    public TyrusUpgradeProvider() {
        WebSocketCdiExtension extension = CDI.current().select(WebSocketCdiExtension.class).get();
        Objects.requireNonNull(extension);
        this.tyrusRouting = extension.tyrusRouting();
        TyrusServerContainer tyrusServerContainer = initializeTyrus();
        this.engine = tyrusServerContainer.getWebSocketEngine();
    }

    @Override
    public ServerConnection upgrade(ConnectionContext ctx, HttpPrologue prologue, WritableHeaders<?> headers) {
        // Check required header
        String wsKey;
        if (headers.contains(WS_KEY)) {
            wsKey = headers.get(WS_KEY).value();
        } else {
            // this header is required
            return null;
        }

        // Verify protocol version
        String version;
        if (headers.contains(WS_VERSION)) {
            version = headers.get(WS_VERSION).value();
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

        // Initialize path and queryString
        path = prologue.uriPath().path();
        int k = path.indexOf('?');
        if (k > 0) {
            this.path = path.substring(0, k);
            this.queryString = path.substring(k + 1);
        } else {
            this.queryString = "";
        }

        // Check if this a Tyrus route exists
        TyrusRoute route = ctx.router()
                .routing(TyrusRouting.class, tyrusRouting)
                .findRoute(prologue);
        if (route == null) {
            return null;
        }

        // Validate origin
        if (!anyOrigin()) {
            if (headers.contains(Http.Header.ORIGIN)) {
                String origin = headers.get(Http.Header.ORIGIN).value();
                if (!origins().contains(origin)) {
                    throw RequestException.builder()
                            .message("Invalid Origin")
                            .type(DirectHandler.EventType.FORBIDDEN)
                            .build();
                }
            }
        }

        // Protocol handshake with Tyrus
        WebSocketEngine.UpgradeInfo upgradeInfo = protocolHandshake(headers);

        // todo support subprotocols (must be provided by route)
        // Sec-WebSocket-Protocol: sub-protocol (list provided in PROTOCOL header, separated by comma space
        DataWriter dataWriter = ctx.dataWriter();
        String switchingProtocols = SWITCHING_PROTOCOL_PREFIX + hash(ctx, wsKey) + SWITCHING_PROTOCOLS_SUFFIX;
        dataWriter.write(BufferData.create(switchingProtocols.getBytes(StandardCharsets.US_ASCII)));

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Upgraded to websocket version " + version);
        }
        return new TyrusConnection(ctx, upgradeInfo);
    }

    TyrusServerContainer initializeTyrus() {
        // Collect endpoint classes -- TODO others
        Set<Class<?>> allEndpointClasses = tyrusRouting.routes()
                .stream()
                .map(TyrusRoute::endpointClass)
                .collect(Collectors.toSet());

        TyrusServerContainer tyrusServerContainer = new TyrusServerContainer(allEndpointClasses) {
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
                return tyrusRouting.extensions();
            }

            @Override
            public WebSocketEngine getWebSocketEngine() {
                return engine;
            }
        };

        // Register classes with context path "/"
        WebSocketEngine engine = tyrusServerContainer.getWebSocketEngine();
        tyrusRouting.routes().forEach(wsRoute -> {
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

        return tyrusServerContainer;
    }

    WebSocketEngine.UpgradeInfo protocolHandshake(WritableHeaders<?> headers) {
        LOGGER.log(Level.FINE, "Initiating WebSocket handshake with Tyrus...");

        // Create Tyrus request context, copy request headers and query params
        Map<String, String[]> paramsMap = new HashMap<>();
        UriQuery uriQuery = UriQuery.create(queryString);
        for (String name : uriQuery.names()) {
            paramsMap.put(name, uriQuery.all(name).toArray(new String[0]));
        }
        RequestContext requestContext = RequestContext.Builder.create()
                .requestURI(URI.create(path))      // excludes context path
                .queryString(queryString)
                .parameterMap(paramsMap)
                .build();
        headers.forEach(e -> requestContext.getHeaders().put(e.name(), List.of(e.values())));

        // Use Tyrus to process a WebSocket upgrade request
        final TyrusUpgradeResponse upgradeResponse = new TyrusUpgradeResponse();
        final WebSocketEngine.UpgradeInfo upgradeInfo = engine.upgrade(requestContext, upgradeResponse);

        // Map Tyrus response headers back to Nima
        upgradeResponse.getHeaders()
                .forEach((key, value) -> headers.add(
                        Http.Header.create(
                                Http.Header.createName(key, key.toLowerCase(Locale.ROOT)),
                                value)));
        return upgradeInfo;
    }
}
