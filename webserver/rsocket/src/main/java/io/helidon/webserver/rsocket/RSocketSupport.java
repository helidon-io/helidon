/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.rsocket;

import io.helidon.webserver.tyrus.TyrusSupport;
import org.glassfish.tyrus.spi.WebSocketEngine;

import java.util.Set;
import javax.websocket.Extension;

import javax.websocket.server.ServerEndpointConfig;

public class RSocketSupport extends TyrusSupport {
    /**
     * Create from another instance.
     *
     * @param other The other instance.
     */
    protected RSocketSupport(TyrusSupport other) {
        super(other);
    }

    public RSocketSupport(
            WebSocketEngine engine,
            Set<Class<?>> endpointClasses,
            Set<ServerEndpointConfig> endpointConfigs,
            Set<Extension> extensions) {
        super(engine,endpointClasses,endpointConfigs,extensions);
    }
}
