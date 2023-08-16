/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.http2;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.webserver.ProtocolConfigs;
import io.helidon.webserver.http1.spi.Http1UpgradeProvider;
import io.helidon.webserver.http1.spi.Http1Upgrader;
import io.helidon.webserver.http2.spi.Http2SubProtocolProvider;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.spi.ProtocolConfig;

/**
 * {@link java.util.ServiceLoader} upgrade protocol provider to upgrade from HTTP/1.1 to HTTP/2.
 */
public class Http2UpgradeProvider implements Http1UpgradeProvider<Http2Config> {
    private final Http2Config http2Config;
    private final List<Http2SubProtocolProvider> subProtocolProviders;

    private Http2UpgradeProvider(Builder builder) {
        this.http2Config = builder.http2Config;
        this.subProtocolProviders = builder.subProtocolProviders.build().asList();
    }

    /**
     * Create a new instance with default configuration.
     *
     * @deprecated to be used solely by {@link java.util.ServiceLoader}
     */
    public Http2UpgradeProvider() {
        this(builder());
    }

    /**
     * Builder to set up this provider.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String protocolType() {
        return Http2ConnectionProvider.CONFIG_NAME;
    }

    @Override
    public Class<Http2Config> protocolConfigType() {
        return Http2Config.class;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public Http1Upgrader create(Http2Config config, ProtocolConfigs configs) {
        Http2Config usedConfig;
        if (http2Config == null) {
            usedConfig = config;
        } else {
            usedConfig = http2Config;
        }

        var subProtocolSelectors = new ArrayList<Http2SubProtocolSelector>();
        for (Http2SubProtocolProvider subProtocolProvider : subProtocolProviders) {
            List<ProtocolConfig> providerConfigs = configs.config(subProtocolProvider.protocolType(),
                                                                  subProtocolProvider.protocolConfigType());
            for (ProtocolConfig providerConfig : providerConfigs) {
                subProtocolSelectors.add(subProtocolProvider.create(providerConfig, configs));
            }
        }

        return new Http2Upgrader(usedConfig, subProtocolSelectors);
    }

    /**
     * Fluent API builder for {@link Http2UpgradeProvider}.
     */
    public static class Builder implements io.helidon.common.Builder<Http2UpgradeProvider.Builder, Http2UpgradeProvider> {
        private final HelidonServiceLoader.Builder<Http2SubProtocolProvider> subProtocolProviders = HelidonServiceLoader.builder(
                ServiceLoader.load(Http2SubProtocolProvider.class));

        private Http2Config http2Config;

        private Builder() {
        }

        @Override
        public Http2UpgradeProvider build() {
            return new Http2UpgradeProvider(this);
        }

        /**
         * Custom configuration of HTTP/2 connections.
         * If not defined, it will be configured from config, or defaults would be used.
         *
         * @param http2Config HTTP/2 configuration
         * @return updated builder
         */
        public Builder http2Config(Http2Config http2Config) {
            this.http2Config = http2Config;
            return this;
        }

        /**
         * Add a configured sub-protocol provider. This will replace the instance discovered through service loader (if one
         * exists).
         *
         * @param provider provider to add
         * @return updated builer
         */
        public Builder addSubProtocolProvider(Http2SubProtocolProvider provider) {
            subProtocolProviders.addService(provider);
            return this;
        }
    }
}
