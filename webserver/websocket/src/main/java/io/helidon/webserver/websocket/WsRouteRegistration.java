/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import io.helidon.service.registry.Service;
import io.helidon.webserver.WebServer;

/**
 * A contract for generated web socket registrations.
 * <p>
 * The implementing types are expected to be {@link io.helidon.service.registry.ServiceRegistry} services,
 * and will be loaded through a {@link io.helidon.webserver.spi.ServerFeature}.
 */
@Service.Contract
public interface WsRouteRegistration {
    /**
     * WebSocket route provided by this registration.
     *
     * @return the route
     */
    WsRoute route();

    /**
     * Named socket this registration should be added to.
     *
     * @return name of the socket
     */
    default String socket() {
        return WebServer.DEFAULT_SOCKET_NAME;
    }

    /**
     * Whether the socket defined in {@link #socket()} must be present for this registration, or it can be
     * exposed on default socket.
     *
     * @return {@code true} if this registration must be exposed on the named socket, {@code false} by default
     */
    default boolean socketRequired() {
        return false;
    }
}
