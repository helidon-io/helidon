/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.http1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.nima.webserver.ProtocolConfigs;
import io.helidon.nima.webserver.http1.spi.Http1UpgradeProvider;
import io.helidon.nima.webserver.http1.spi.Http1Upgrader;
import io.helidon.nima.webserver.spi.ProtocolConfig;
import io.helidon.nima.webserver.spi.ServerConnectionSelector;
import io.helidon.nima.webserver.spi.ServerConnectionSelectorProvider;

/**
 * {@link io.helidon.nima.webserver.spi.ServerConnectionSelectorProvider} implementation for HTTP/1.1 server connection provider.
 */
public class Http1ConnectionProvider implements ServerConnectionSelectorProvider<Http1Config> {

    /**
     * HTTP/1.1 server connection provider configuration node name.
     */
    static final String CONFIG_NAME = "http_1_1";

    // all upgrade providers supported by HTTP/1.1
    private final List<Http1UpgradeProvider> upgradeProviders;
    private final Http1Config http1Config;

    private Http1ConnectionProvider(Builder builder) {
        this.upgradeProviders = builder.upgradeProviders();
        this.http1Config = builder.http1Config();
    }

    /**
     * Create a new instance with default configuration.
     * Please use {@link #builder()} to customize this provider.
     *
     * @deprecated to be used solely by {@link java.util.ServiceLoader}
     */
    @Deprecated
    public Http1ConnectionProvider() {
        this.upgradeProviders = builder().upgradeProviders();
        this.http1Config = null;
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
    public Class<Http1Config> protocolConfigType() {
        return Http1Config.class;
    }

    @Override
    public String protocolType() {
        return CONFIG_NAME;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public ServerConnectionSelector create(String socketName, Http1Config config, ProtocolConfigs configs) {
        Http1Config usedConfig = this.http1Config;
        if (usedConfig == null) {
            usedConfig = config;
        }

        // now create an upgrader for each upgrade provider
        var upgraderList = new ArrayList<Http1Upgrader>();
        for (Http1UpgradeProvider upgradeProvider : upgradeProviders) {
            List<ProtocolConfig> upgradeConfigs = configs.config(upgradeProvider.protocolType(),
                                                                 upgradeProvider.protocolConfigType());
            for (ProtocolConfig upgradeConfig : upgradeConfigs) {
                upgraderList.add(upgradeProvider.create(upgradeConfig, configs));
            }
        }

        var upgraders = new HashMap<String, Http1Upgrader>();
        for (Http1Upgrader http1Upgrader : upgraderList) {
            // use put if absent, so when we have more than one upgraded for the same protocol, we use the one with higher weight
            upgraders.putIfAbsent(http1Upgrader.supportedProtocol(), http1Upgrader);
        }

        return new Http1ConnectionSelector(usedConfig, upgraders);
    }

    /**
     * Fluent API builder for {@link Http1ConnectionProvider}.
     */
    public static class Builder implements io.helidon.common.Builder<Http1ConnectionProvider.Builder, Http1ConnectionProvider> {
        private final HelidonServiceLoader.Builder<Http1UpgradeProvider> upgradeProviderServices = HelidonServiceLoader.builder(
                ServiceLoader.load(Http1UpgradeProvider.class));
        private Http1Config http1Config;

        private Builder() {
        }

        @Override
        public Http1ConnectionProvider build() {
            return new Http1ConnectionProvider(this);
        }

        /**
         * Custom configuration of HTTP/1 connection provider.
         * If not defined, it will be configured from config, or defaults would be used.
         *
         * @param http1Config HTTP/1 configuration
         * @return updated builder
         */
        public Builder http1Config(Http1Config http1Config) {
            this.http1Config = http1Config;
            return this;
        }

        /**
         * Add a configured upgrade provider. This will replace the instance discovered through service loader (if one exists).
         *
         * @param provider provider to add
         * @return updated builder
         */
        public Builder addUpgradeProvider(Http1UpgradeProvider<?> provider) {
            upgradeProviderServices.addService(provider);
            return this;
        }

        private List<Http1UpgradeProvider> upgradeProviders() {
            return upgradeProviderServices.build().asList();
        }

        // may be null
        private Http1Config http1Config() {
            return http1Config;
        }
    }
}
