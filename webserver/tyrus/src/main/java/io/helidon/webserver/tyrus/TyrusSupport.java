/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.tyrus;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import javax.websocket.DeploymentException;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import org.glassfish.tyrus.core.RequestContext;
import org.glassfish.tyrus.core.TyrusUpgradeResponse;
import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.server.TyrusServerContainer;
import org.glassfish.tyrus.spi.Connection;
import org.glassfish.tyrus.spi.WebSocketEngine;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Class TyrusSupport implemented as a Helidon service.
 */
public class TyrusSupport implements Service {
    private static final Logger LOGGER = Logger.getLogger(TyrusSupport.class.getName());

    /**
     * A zero-length buffer indicates a connection flush to Helidon.
     */
    private static final ByteBuffer FLUSH_BUFFER = ByteBuffer.allocateDirect(0);

    private final WebSocketEngine engine;
    private final TyrusHandler handler = new TyrusHandler();
    private Set<Class<?>> endpointClasses;
    private Set<ServerEndpointConfig> endpointConfigs;

    TyrusSupport(WebSocketEngine engine, Set<Class<?>> endpointClasses, Set<ServerEndpointConfig> endpointConfigs) {
        this.engine = engine;
        this.endpointClasses = endpointClasses;
        this.endpointConfigs = endpointConfigs;
    }

    /**
     * Register our WebSocket handler for all routes. Once a request is received,
     * it will be forwarded to the next handler if not a protocol upgrade request.
     *
     * @param routingRules Routing rules to update.
     */
    @Override
    public void update(Routing.Rules routingRules) {
        LOGGER.info("Updating TyrusSupport routing routes");
        routingRules.any(handler);
    }

    /**
     * Access to endpoint classes.
     *
     * @return Immutable set of end endpoint classes.
     */
    public Set<Class<?>> endpointClasses() {
        return Collections.unmodifiableSet(endpointClasses);
    }

    /**
     * Access to endpoint configs.
     *
     * @return Immutable set of end endpoint configs.
     */
    public Set<ServerEndpointConfig> endpointConfigs() {
        return Collections.unmodifiableSet(endpointConfigs);
    }

    /**
     * Creates a builder for this class.
     *
     * @return A builder for this class.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for convenient way to create {@link TyrusSupport}.
     */
    public static final class Builder implements io.helidon.common.Builder<TyrusSupport> {

        private Set<Class<?>> endpointClasses = new HashSet<>();
        private Set<ServerEndpointConfig> endpointConfigs = new HashSet<>();

        private Builder() {
        }

        /**
         * Register an endpoint class.
         *
         * @param endpointClass The class.
         * @return The builder.
         */
        public Builder register(Class<?> endpointClass) {
            endpointClasses.add(endpointClass);
            return this;
        }

        /**
         * Register an endpoint config.
         *
         * @param endpointConfig The endpoint config.
         * @return The builder.
         */
        public Builder register(ServerEndpointConfig endpointConfig) {
            endpointConfigs.add(endpointConfig);
            return this;
        }

        @Override
        public TyrusSupport build() {
            // Create container and WebSocket engine
            TyrusServerContainer serverContainer = new TyrusServerContainer(endpointClasses) {
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
                public WebSocketEngine getWebSocketEngine() {
                    return engine;
                }
            };

            // Register classes with context path "/"
            WebSocketEngine engine = serverContainer.getWebSocketEngine();
            endpointClasses.forEach(c -> {
                try {
                    // Context path handled by Helidon based on app's routes
                    engine.register(c, "/");
                } catch (DeploymentException e) {
                    throw new RuntimeException(e);
                }
            });
            endpointConfigs.forEach(c -> {
                try {
                    // Context path handled by Helidon based on app's routes
                    engine.register(c, "/");
                } catch (DeploymentException e) {
                    throw new RuntimeException(e);
                }
            });

            // Create TyrusSupport using WebSocket engine
            return new TyrusSupport(serverContainer.getWebSocketEngine(), endpointClasses, endpointConfigs);
        }
    }

    /**
     * A Helidon handler that integrates with Tyrus and can process WebSocket
     * upgrade requests.
     */
    private class TyrusHandler implements Handler {

        /**
         * Process a server request/response.
         *
         * @param req an HTTP server request.
         * @param res an HTTP server response.
         */
        @Override
        public void accept(ServerRequest req, ServerResponse res) {
            // Skip this handler if not an upgrade request
            Optional<String> secWebSocketKey = req.headers().value(HandshakeRequest.SEC_WEBSOCKET_KEY);
            if (!secWebSocketKey.isPresent()) {
                req.next();
                return;
            }

            LOGGER.fine("Initiating WebSocket handshake ...");

            // Create Tyrus request context and copy request headers
            RequestContext requestContext = RequestContext.Builder.create()
                    .requestURI(URI.create(req.path().toString()))      // excludes context path
                    .build();
            req.headers().toMap().entrySet().forEach(e ->
                    requestContext.getHeaders().put(e.getKey(), e.getValue()));

            // Use Tyrus to process a WebSocket upgrade request
            final TyrusUpgradeResponse upgradeResponse = new TyrusUpgradeResponse();
            final WebSocketEngine.UpgradeInfo upgradeInfo = engine.upgrade(requestContext, upgradeResponse);

            // Respond to upgrade request using response from Tyrus
            res.status(upgradeResponse.getStatus());
            upgradeResponse.getHeaders().entrySet().forEach(e ->
                    res.headers().add(e.getKey(), e.getValue()));
            TyrusWriterPublisher publisherWriter = new TyrusWriterPublisher();
            res.send(publisherWriter);

            // Write reason for failure if not successful
            if (upgradeInfo.getStatus() != WebSocketEngine.UpgradeStatus.SUCCESS) {
                publisherWriter.write(ByteBuffer.wrap(
                        upgradeResponse.getReasonPhrase().getBytes(UTF_8)), null);
            }

            // Flush upgrade response
            publisherWriter.write(FLUSH_BUFFER, null);

            // Setup the WebSocket connection and internally the ReaderHandler
            Connection connection = upgradeInfo.createConnection(publisherWriter,
                    closeReason -> LOGGER.fine(() -> "Connection closed: " + closeReason));

            // Set up reader to pass data back to Tyrus
            TyrusReaderSubscriber subscriber = new TyrusReaderSubscriber(connection);
            req.content().subscribe(subscriber);
        }
    }
}
