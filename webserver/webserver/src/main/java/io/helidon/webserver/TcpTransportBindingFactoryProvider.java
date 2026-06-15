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

import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.webserver.spi.TransportBindingFactoryProvider;

/**
 * Transport binding factory provider for the built-in TCP listener binding.
 */
public class TcpTransportBindingFactoryProvider implements TransportBindingFactoryProvider {
    /**
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public TcpTransportBindingFactoryProvider() {
    }

    @Override
    public String configKey() {
        return TcpTransportBinding.TYPE;
    }

    @Override
    public TcpTransportConfig create(Config config, String name) {
        TcpTransportConfig tcpConfig = TcpTransportConfig.builder()
                .config(config)
                .name(name)
                .build();
        if (!config.exists() && TcpTransportBinding.TYPE.equals(name)) {
            return new DiscoveredDefaultTcpTransportConfig(tcpConfig);
        }
        return tcpConfig;
    }

    static boolean isDiscoveredDefault(TcpTransportConfig config) {
        return config instanceof DiscoveredDefaultTcpTransportConfig;
    }

    private record DiscoveredDefaultTcpTransportConfig(TcpTransportConfig delegate) implements TcpTransportConfig {
        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public boolean enabled() {
            return delegate.enabled();
        }

        @Override
        public boolean required() {
            return delegate.required();
        }
    }
}
