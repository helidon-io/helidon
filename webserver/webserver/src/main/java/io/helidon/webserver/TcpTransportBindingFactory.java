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

import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.webserver.spi.TransportBinding;
import io.helidon.webserver.spi.TransportBindingFactory;

/**
 * TCP transport binding factory.
 */
@Api.Internal
public final class TcpTransportBindingFactory implements TransportBindingFactory {
    private final TcpTransportConfig config;

    private TcpTransportBindingFactory(TcpTransportConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Create a TCP transport binding factory.
     *
     * @param config TCP transport binding configuration
     * @return TCP transport binding factory
     */
    public static TcpTransportBindingFactory create(TcpTransportConfig config) {
        return new TcpTransportBindingFactory(config);
    }

    @Override
    public String type() {
        return TransportBindingTypes.TCP;
    }

    @Override
    public boolean enabled() {
        return config.enabled();
    }

    @Override
    public boolean required() {
        return config.required();
    }

    @Override
    public boolean canBind(BindingPlanContext context) {
        Objects.requireNonNull(context, "context");
        return config.enabled();
    }

    @Override
    public TransportBinding create(TransportBindingContext context) {
        Objects.requireNonNull(context, "context");
        if (!(context instanceof ServerListener listener)) {
            throw new IllegalArgumentException("Built-in TCP transport requires the WebServer listener runtime");
        }
        return new TcpTransportBinding(context, listener.idleConnectionTimer());
    }
}
