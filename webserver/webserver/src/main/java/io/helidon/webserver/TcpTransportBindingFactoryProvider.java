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
import io.helidon.webserver.spi.TransportBindingFactory;
import io.helidon.webserver.spi.TransportBindingFactoryProvider;

/**
 * Transport binding factory provider for the built-in TCP listener binding.
 */
@Api.Internal
public class TcpTransportBindingFactoryProvider implements TransportBindingFactoryProvider {
    /**
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public TcpTransportBindingFactoryProvider() {
    }

    @Override
    public String configKey() {
        return TransportBindingTypes.TCP;
    }

    @Override
    public TransportBindingFactory create(Config config) {
        TcpTransportConfig tcpConfig = TcpTransportConfig.builder()
                .config(config)
                .build();
        return TcpTransportBindingFactory.create(tcpConfig);
    }
}
