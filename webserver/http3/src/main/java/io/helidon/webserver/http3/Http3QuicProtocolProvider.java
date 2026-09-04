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

package io.helidon.webserver.http3;

import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.webserver.TransportBindingContext;
import io.helidon.webserver.quic.spi.QuicSubProtocolProvider;
import io.helidon.webserver.quic.spi.QuicSubProtocolRuntime;
import io.helidon.webserver.spi.ProtocolConfigProvider;

/**
 * Listener protocol configuration and QUIC runtime provider for HTTP/3.
 */
@Api.Internal
public class Http3QuicProtocolProvider
        implements ProtocolConfigProvider<Http3Config>, QuicSubProtocolProvider<Http3Config> {
    /**
     * Public constructor required by {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public Http3QuicProtocolProvider() {
    }

    @Override
    public String configKey() {
        return Http3BlueprintSupport.CONFIG_NAME;
    }

    @Override
    public Http3Config create(Config config, String name) {
        return Http3Config.builder()
                .config(config)
                .name(name)
                .enabled(config.exists() && config.get("enabled").asBoolean().orElse(true))
                .build();
    }

    @Override
    public Class<Http3Config> protocolConfigType() {
        return Http3Config.class;
    }

    @Override
    public QuicSubProtocolRuntime create(TransportBindingContext context, Http3Config config) {
        return new Http3QuicRuntime(context, config);
    }
}
