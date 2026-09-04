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

package io.helidon.webserver.quic;

import java.util.List;
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.webserver.BindingPlanContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.TransportBindingContext;
import io.helidon.webserver.quic.spi.QuicSubProtocolConfig;
import io.helidon.webserver.spi.TransportBinding;
import io.helidon.webserver.spi.TransportBindingFactory;

/**
 * QUIC transport binding factory.
 */
@Api.Internal
public final class QuicTransportBindingFactory implements TransportBindingFactory {
    private final QuicTransportConfig config;

    private QuicTransportBindingFactory(QuicTransportConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Create a QUIC transport binding factory.
     *
     * @param config QUIC transport binding configuration
     * @return QUIC transport binding factory
     */
    public static QuicTransportBindingFactory create(QuicTransportConfig config) {
        return new QuicTransportBindingFactory(config);
    }

    @Override
    public String type() {
        return QuicTransportBindingTypes.QUIC;
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
        return config.enabled() && !quicProtocols(context.listenerConfig()).isEmpty();
    }

    @Override
    public TransportBinding create(TransportBindingContext context) {
        Objects.requireNonNull(context, "context");
        List<QuicSubProtocolConfig> quicProtocols = quicProtocols(context.listenerContext().config());

        if (quicProtocols.isEmpty()) {
            throw new UnsupportedOperationException("Listener " + context.listenerContext().config().name()
                                                            + " requires QUIC binding type, but no QUIC protocol "
                                                            + "configuration is available");
        }
        return new QuicTransportBinding(context, config, quicProtocols, config.subProtocolProviders());
    }

    private static List<QuicSubProtocolConfig> quicProtocols(ListenerConfig listenerConfig) {
        return listenerConfig.protocols().stream()
                .filter(QuicSubProtocolConfig.class::isInstance)
                .map(QuicSubProtocolConfig.class::cast)
                .filter(QuicSubProtocolConfig::enabled)
                .toList();
    }
}
