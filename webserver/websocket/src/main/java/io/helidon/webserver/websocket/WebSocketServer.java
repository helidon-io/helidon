/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.service.registry.Service;

/**
 * Container class for declarative WebSocket server endpoint types.
 */
public final class WebSocketServer {
    private WebSocketServer() {
    }

    /**
     * Definition of a server endpoint.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Inherited
    @Service.Singleton
    public @interface Endpoint {
    }

    /**
     * Listener socket assigned to this endpoint.
     * This only makes sense for server side, as it is binding endpoint to a server socket.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Inherited
    public @interface Listener {
        /**
         * Name of a routing to bind this application/service to.
         *
         * @return name of a routing (or listener host/port) on WebServer
         */
        String value();
    }

}
