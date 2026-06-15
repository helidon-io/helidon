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

package io.helidon.webserver;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;

import io.helidon.webserver.spi.PortTransportBinding;

final class TcpTransportBinding extends SocketTransportBinding implements PortTransportBinding {
    static final String TYPE = "tcp";

    TcpTransportBinding(TransportBindingContext transportContext, TcpTransportConfig config) {
        super(transportContext, TYPE, configName(config), configuredAddress(transportContext.listenerContext().config()));
    }

    private static String configName(TcpTransportConfig config) {
        Objects.requireNonNull(config, "config");
        return config.name();
    }

    private static SocketAddress configuredAddress(ListenerConfig listenerConfig) {
        return listenerConfig.bindAddress()
                .orElseGet(() -> {
                    int port = listenerConfig.port();
                    if (port < 1) {
                        port = 0;
                    }
                    return new InetSocketAddress(listenerConfig.address(), port);
                });
    }

    @Override
    public int port() {
        return connectedPort();
    }
}
