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

import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.common.Weighted;
import io.helidon.config.Config;
import io.helidon.webserver.spi.TransportBindingFactory;
import io.helidon.webserver.spi.TransportBindingFactoryProvider;
import io.helidon.webserver.spi.TransportConfig;

/**
 * Provider of QUIC transport binding factories.
 */
@Api.Internal
public class QuicTransportBindingProvider implements TransportBindingFactoryProvider, Weighted {
    /**
     * Public constructor required by {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public QuicTransportBindingProvider() {
    }

    @Override
    public String configKey() {
        return QuicTransportBindingTypes.QUIC;
    }

    @Override
    public TransportBindingFactory create(Config config) {
        QuicTransportConfig quicConfig = QuicTransportConfig.builder()
                .config(config)
                .build();
        return QuicTransportBindingFactory.create(quicConfig);
    }

    @Override
    public TransportBindingFactory create(TransportConfig config) {
        Objects.requireNonNull(config, "config");
        if (config instanceof QuicTransportConfig quicConfig) {
            return QuicTransportBindingFactory.create(quicConfig);
        }
        throw new IllegalArgumentException("QUIC transport requires QuicTransportConfig, got " + config.getClass().getName());
    }

    @Override
    public double weight() {
        return Weighted.DEFAULT_WEIGHT - 10;
    }
}
